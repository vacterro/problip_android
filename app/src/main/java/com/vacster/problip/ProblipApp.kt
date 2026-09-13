package com.vacster.problip

import android.app.Application
import android.content.Context
import com.vacster.problip.billing.BillingRepository
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.settings.SettingsWriteResult
import com.vacster.problip.stats.BlipStatsRepository
import com.vacster.problip.trial.PersistenceOutcome
import com.vacster.problip.trial.TemporaryAccess
import com.vacster.problip.trial.TrialCoordinator
import com.vacster.problip.widget.ProblipWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-level singletons: one BillingClient, one trial coordinator and one stats repository per process. */
class ProblipApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var billing: BillingRepository
        private set

    lateinit var trials: TrialCoordinator
        private set

    lateinit var stats: BlipStatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsRepository.fromContext(this)
        billing = BillingRepository(
            context = this,
            scope = appScope,
            // W2-004 Part E: the ownership cache is OFFLINE CONVENIENCE ONLY.
            // Play stays the authority; the typed write result becomes the
            // best-effort boolean (true = committed, false = expected local
            // I/O failure), which can never interrupt acknowledgement or the
            // Play-success bookkeeping (see BillingRepository).
            cacheOwned = { owned ->
                settings.setOwnedProducts(owned) == SettingsWriteResult.Success
            },
        )
        stats = BlipStatsRepository.fromContext(this, appScope)
        trials = TrialCoordinator(
            persisted = settings.settings.map {
                TemporaryAccess(it.trialExpiries, it.developerAccessExpiryMillis)
            },
            // W2-004: the coordinator's neutral PersistenceOutcome is mapped from
            // the repository's typed write result at the wiring edge, keeping the
            // trial package independent of the settings package.
            update = { transform ->
                when (settings.updateTrialExpiries(transform)) {
                    SettingsWriteResult.Success -> PersistenceOutcome.PERSISTED
                    SettingsWriteResult.IoFailure -> PersistenceOutcome.IO_FAILED
                }
            },
            setDeveloperExpiry = { expiry ->
                when (settings.setDeveloperAccessExpiry(expiry)) {
                    SettingsWriteResult.Success -> PersistenceOutcome.PERSISTED
                    SettingsWriteResult.IoFailure -> PersistenceOutcome.IO_FAILED
                }
            },
            clearTemporary = {
                when (settings.clearTemporaryAccess()) {
                    SettingsWriteResult.Success -> PersistenceOutcome.PERSISTED
                    SettingsWriteResult.IoFailure -> PersistenceOutcome.IO_FAILED
                }
            },
            scope = appScope,
            earnedPremium = stats.earnedPremium,
            earnedReady = stats.ready,
        )
        trials.start()
        appScope.launch { stats.start() }
        appScope.launch {
            // Seed the offline cache, then let Play answer with the truth.
            billing.seedCached(settings.settings.first().ownedProducts)
            billing.connect()
        }
        appScope.launch {
            // One place turns real session states into widget refreshes: app START,
            // service RUNNING, notification STOP, playback ERROR and teardown all
            // pass through here, so no call site has to remember the widget.
            ProblipSession.state.collect { ProblipWidgetUpdater.updateAll(this@ProblipApp) }
        }
    }

    companion object {
        fun billing(context: Context): BillingRepository =
            (context.applicationContext as ProblipApp).billing

        fun trials(context: Context): TrialCoordinator =
            (context.applicationContext as ProblipApp).trials

        fun stats(context: Context): BlipStatsRepository =
            (context.applicationContext as ProblipApp).stats
    }
}
