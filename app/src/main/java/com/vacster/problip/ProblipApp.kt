package com.vacster.problip

import android.app.Application
import android.content.Context
import com.vacster.problip.billing.BillingRepository
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.trial.TemporaryAccess
import com.vacster.problip.trial.TrialCoordinator
import com.vacster.problip.widget.ProblipWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-level singletons: one BillingClient and one trial coordinator per process. */
class ProblipApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var billing: BillingRepository
        private set

    lateinit var trials: TrialCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsRepository.fromContext(this)
        billing = BillingRepository(
            context = this,
            scope = appScope,
            cacheOwned = { owned -> settings.setOwnedProducts(owned) },
        )
        trials = TrialCoordinator(
            persisted = settings.settings.map {
                TemporaryAccess(it.trialExpiries, it.developerAccessExpiryMillis)
            },
            update = settings::updateTrialExpiries,
            setDeveloperExpiry = settings::setDeveloperAccessExpiry,
            clearTemporary = settings::clearTemporaryAccess,
            scope = appScope,
        )
        trials.start()
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
    }
}
