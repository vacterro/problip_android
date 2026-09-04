package com.vacster.problip.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vacster.problip.ProblipApp
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.audio.SoundPoolAudioPlayer
import com.vacster.problip.audio.Volume
import com.vacster.problip.core.BlipScheduler
import com.vacster.problip.core.ProblipState
import com.vacster.problip.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * User-started foreground service owning the active session: one scheduler,
 * one audio engine, one persistent notification with a STOP action.
 *
 * Deliberately NOT done (behavior contract):
 * - START_NOT_STICKY: reboot/force-stop never resurrect a session.
 * - no exact AlarmManager alarms for 4–30 s scheduling.
 * - no partial WakeLock until device testing proves CPU suspend breaks timing.
 */
class ProblipService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val lifecycle = ProblipSession.lifecycle
    private var settingsState: StateFlow<SettingsRepository.Settings>? = null

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
        settingsState = repo.settings.stateIn(scope, SharingStarted.Eagerly, SettingsRepository.Settings())
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
        val settings = settingsState?.value ?: SettingsRepository.Settings()
        goForeground(settings)
        if (scheduler != null) return // exactly one session; repeated START is a no-op

        val audio = SoundPoolAudioPlayer(this)
        val scheduler = BlipScheduler(scope, audio)
        this.audio = audio
        this.scheduler = scheduler
        // After the no-op check: a duplicate START must not orphan the token of
        // the session that is already running.
        val token = lifecycle.begin()

        sessionJobs += scope.launch {
            val owned = ProblipApp.billing(this@ProblipService).owned
            combine(settingsState ?: return@launch, owned) { s, o -> s to o }
                .collect { (s, o) ->
                    audio.setVolume(Volume.percentToGain(s.volumePercent))
                    scheduler.interval = s.intervalMode.toConfig()
                    notifyUpdated(s, o)
                    // Pool changes (settings or new purchases) load in place;
                    // the scheduler loop is untouched.
                    val pool = SoundCatalog.playableSelection(s.selectedSounds, o)
                    if (scheduler.state.value != ProblipState.STOPPED && pool != preparedPool) {
                        if (audio.prepare(pool)) preparedPool = pool
                        else failSession(token, SOUND_LOAD_FAILED)
                    }
                }
        }
        sessionJobs += scope.launch {
            val owned = ProblipApp.billing(this@ProblipService).owned.value
            val pool = SoundCatalog.playableSelection(
                settingsState?.value?.selectedSounds ?: emptySet(),
                owned,
            )
            if (!audio.prepare(pool)) {
                failSession(token, SOUND_LOAD_FAILED)
                return@launch
            }
            preparedPool = pool
            scheduler.interval = settingsState?.value?.intervalMode?.toConfig()
                ?: SettingsRepository.Settings().intervalMode.toConfig()
            scheduler.start()
        }
        sessionJobs += scope.launch {
            scheduler.state.collect { st ->
                if (st == ProblipState.ERROR) {
                    failSession(token, scheduler.error.value ?: PLAYBACK_FAILED)
                } else {
                    onMain { lifecycle.report(token, st) }
                }
            }
        }
    }

    /** User STOP, from the app button or the notification action. */
    private fun stopSession() = onMain {
        lifecycle.stop()
        releaseSession()
    }

    /**
     * Terminal audio failure. A stale token means the failure belongs to a session
     * that is already gone, so tearing down now would kill the one the user just
     * started; the accepted case keeps ERROR visible past teardown.
     */
    private fun failSession(token: Int, message: String) = onMain {
        if (lifecycle.fail(token, message)) releaseSession()
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

    private fun notifyUpdated(settings: SettingsRepository.Settings, owned: Set<String>) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ProblipNotification.NOTIFICATION_ID, ProblipNotification.build(this, settings, owned))
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
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.vacster.problip.action.START"
        const val ACTION_STOP = "com.vacster.problip.action.STOP"

        private const val SOUND_LOAD_FAILED = "Sound could not be loaded"
        private const val PLAYBACK_FAILED = "Playback failed"

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
