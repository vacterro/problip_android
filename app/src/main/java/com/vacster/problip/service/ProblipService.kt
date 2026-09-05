package com.vacster.problip.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vacster.problip.ProblipApp
import com.vacster.problip.audio.SoundPoolAudioPlayer
import com.vacster.problip.audio.Volume
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.core.BlipPlayer
import com.vacster.problip.core.BlipScheduler
import com.vacster.problip.core.CoroutineDelayBoundary
import com.vacster.problip.core.DelayBoundary
import com.vacster.problip.core.PremiumInterval
import com.vacster.problip.core.ProblipState
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.trial.PremiumAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * User-started foreground service owning the active session: one scheduler,
 * one audio engine, one persistent notification with a STOP action.
 *
 * Deliberately NOT done (behavior contract):
 * - START_NOT_STICKY: reboot/force-stop never resurrect a session.
 * - no exact AlarmManager alarms for 4–30 s scheduling.
 *
 * A PARTIAL_WAKE_LOCK is held for the duration of a RUNNING session: device
 * testing proved the CPU suspends between blips with the screen off, stretching
 * a 5 s interval to roughly 20 s.
 */
class ProblipService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val lifecycle = ProblipSession.lifecycle
    private val wakeLock by lazy { SessionWakeLock(this) }

    /**
     * Null until DataStore has answered. The synthetic default Settings() used to
     * be the initial value, so a session could prepare its audio pool, volume and
     * interval from defaults the user never chose and then jump to the real values
     * a moment later; nothing downstream may read settings before they are real.
     */
    private var settingsState: StateFlow<SettingsRepository.Settings?>? = null

    private var audio: SoundPoolAudioPlayer? = null
    private var scheduler: BlipScheduler? = null
    private val sessionJobs = mutableListOf<Job>()

    /** Pool the audio engine was last prepared with; switches only on change. */
    @Volatile
    private var preparedPool: Set<String> = emptySet()

    /**
     * Newest startId Android delivered. stopSelf() without an id kills the
     * service even when a newer START is already queued, which silently killed
     * the fresh session; stopSelf(id) is ignored once a newer start arrived.
     */
    @Volatile
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        val repo = SettingsRepository.fromContext(this)
        settingsState = repo.settings.stateIn(scope, SharingStarted.Eagerly, null)
        ProblipNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> stopSession()
            else -> startSession()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession() {
        // The notification has to be up within seconds of startForegroundService,
        // which can be before DataStore answers: its first text may show defaults
        // and is corrected by the collector below. Audio never uses them.
        goForeground(settingsState?.value ?: SettingsRepository.Settings())
        if (scheduler != null) return // exactly one session; repeated START is a no-op

        val settings = settingsState ?: return
        val audio = SoundPoolAudioPlayer(this)
        val scheduler = BlipScheduler(
            scope,
            BlipPlayer { audio.play().also(ProblipSession::reportPlayback) },
            delayBoundary = timingBoundary(),
        )
        this.audio = audio
        this.scheduler = scheduler
        // After the no-op check: a duplicate START must not orphan the token of
        // the session that is already running.
        val token = lifecycle.begin()
        applyWakeLock()

        sessionJobs += scope.launch {
            val owned = ProblipApp.billing(this@ProblipService).owned
            val access = ProblipApp.trials(this@ProblipService).access
            combine(settings.filterNotNull(), owned, access) { s, o, a -> Triple(s, o, a) }
                .collect { (s, o, a) ->
                    audio.setVolume(Volume.percentToGain(s.volumePercent))
                    // The SAME resolution the cold-start barrier uses, so a running
                    // session and a first session can never disagree about what a
                    // snapshot means.
                    val plan = ColdStart.plan(s, o, a)
                    // Trial start/expiry, Developer Access, a reset or a purchase all
                    // land here as a new interval on the SAME scheduler.
                    scheduler.interval = plan.interval
                    notifyUpdated(s, o, a)
                    // Pool changes (settings, new purchases, a trial starting or
                    // expiring) load in place; the scheduler loop is untouched.
                    if (scheduler.state.value != ProblipState.STOPPED && plan.pool != preparedPool) {
                        if (audio.prepare(plan.pool)) preparedPool = plan.pool
                        else failSession(token, SOUND_LOAD_FAILED)
                    }
                }
        }
        sessionJobs += scope.launch {
            // Cold-start barrier: real persisted settings AND initialized access
            // snapshots (ownership cache or Play answer, plus restored trials and
            // Developer Access) before the FIRST start(), so a premium sound or a
            // persisted MANUAL/PULSE pick is honoured by the very first blip.
            val plan = ColdStart.awaitFirstPlan(
                settings = settings,
                ownershipReady = ProblipApp.billing(this@ProblipService).ownershipReady,
                ownedNow = { ProblipApp.billing(this@ProblipService).owned.value },
                accessReady = ProblipApp.trials(this@ProblipService).ready,
                accessNow = { ProblipApp.trials(this@ProblipService).access.value },
            )
            if (!audio.prepare(plan.pool)) {
                failSession(token, SOUND_LOAD_FAILED)
                return@launch
            }
            preparedPool = plan.pool
            scheduler.interval = plan.interval
            scheduler.start()
        }
        sessionJobs += scope.launch {
            scheduler.state.collect { st ->
                if (st == ProblipState.ERROR) {
                    failSession(token, scheduler.error.value ?: PLAYBACK_FAILED)
                } else {
                    onMain { if (lifecycle.report(token, st)) applyWakeLock() }
                }
            }
        }
    }

    /** User STOP, from the app button or the notification action. */
    private fun stopSession() = onMain {
        lifecycle.stop()
        applyWakeLock()
        releaseSession()
    }

    /**
     * Terminal audio failure. A stale token means the failure belongs to a session
     * that is already gone, so tearing down now would kill the one the user just
     * started; the accepted case keeps ERROR visible past teardown.
     */
    private fun failSession(token: Int, message: String) = onMain {
        if (lifecycle.fail(token, message)) {
            applyWakeLock()
            releaseSession()
        }
    }

    /**
     * The wake lock follows published session state, so it can only ever be
     * released by the transition that actually reached the state machine: a stale
     * generation is rejected by [SessionLifecycle] before it gets here and can
     * therefore never release the lock of the session the user just started.
     */
    private fun applyWakeLock() {
        wakeLock.apply(WakeLockPolicy.requiredFor(ProblipSession.state.value))
    }

    /**
     * Debug builds log scheduled-versus-actual delay so screen-off timing can be
     * measured on a physical device. Release builds get the plain boundary.
     */
    private fun timingBoundary(): DelayBoundary {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return CoroutineDelayBoundary
        return DelayBoundary { ms ->
            // elapsedRealtime, not wall clock: it keeps counting through suspend
            // and is not moved by clock changes.
            val started = SystemClock.elapsedRealtime()
            CoroutineDelayBoundary.delay(ms)
            val actual = SystemClock.elapsedRealtime() - started
            Log.d(TIMING_TAG, "scheduled=$ms actual=$actual drift=${actual - ms}")
        }
    }

    /**
     * Releases everything the session owns and publishes no state: the caller has
     * already published the terminal one. Leaving the scheduler and the
     * notification alive after a failure made START a no-op, so a dead session
     * could never be restarted.
     */
    private fun releaseSession() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        scheduler?.stop()
        scheduler = null
        audio?.release()
        audio = null
        preparedPool = emptySet()
        // Drop the notification here, not at destroy time: the process can
        // outlive stopSelf() (queued start, system delay) and a STOP button
        // without a session behind it is an orphan.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    /**
     * Session bookkeeping is main-confined: START/STOP arrive on the main thread
     * while failures surface on Dispatchers.Default, and two threads tearing down
     * the same session cannot be made safe by field ordering alone.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == main.looper) block() else main.post(block)
    }

    private fun goForeground(settings: SettingsRepository.Settings) {
        ServiceCompat.startForeground(
            this,
            ProblipNotification.NOTIFICATION_ID,
            ProblipNotification.build(this, settings),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun notifyUpdated(
        settings: SettingsRepository.Settings,
        owned: Set<String>,
        access: PremiumAccess,
    ) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // The notification shows what is actually running, so an expired premium
        // interval reads as the free preset instead of lying about MANUAL or PULSE.
        val ownsPack = ProductCatalog.THEME_PACK in owned
        val shown = settings.copy(
            intervalMode = PremiumInterval.effectiveMode(
                mode = settings.intervalMode,
                manualAccessible = access.grantsManualInterval(ownsPack),
                pulseAccessible = access.grantsPulseInterval(ownsPack),
            ),
        )
        nm.notify(
            ProblipNotification.NOTIFICATION_ID,
            ProblipNotification.build(this, shown, owned, access.grantedIds),
        )
    }

    override fun onDestroy() {
        // System-initiated destroy: release everything so no orphan can survive,
        // but never overwrite a failure the user has not seen yet.
        scheduler?.stop()
        scheduler = null
        audio?.release()
        audio = null
        scope.cancel()
        lifecycle.destroy()
        applyWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.vacster.problip.action.START"
        const val ACTION_STOP = "com.vacster.problip.action.STOP"

        private const val SOUND_LOAD_FAILED = "Sound could not be loaded"
        private const val PLAYBACK_FAILED = "Playback failed"
        private const val TIMING_TAG = "ProblipTiming"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProblipService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ProblipService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
