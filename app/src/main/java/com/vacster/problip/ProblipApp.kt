package com.vacster.problip

import android.app.Application
import android.content.Context
import com.vacster.problip.billing.BillingRepository
import com.vacster.problip.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-level singletons: one BillingClient for the whole process. */
class ProblipApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var billing: BillingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsRepository.fromContext(this)
        billing = BillingRepository(
            context = this,
            scope = appScope,
            cacheOwned = { owned -> settings.setOwnedProducts(owned) },
        )
        appScope.launch {
            // Seed the offline cache, then let Play answer with the truth.
            billing.seedCached(settings.settings.first().ownedProducts)
            billing.connect()
        }
    }

    companion object {
        fun billing(context: Context): BillingRepository =
            (context.applicationContext as ProblipApp).billing
    }
}
