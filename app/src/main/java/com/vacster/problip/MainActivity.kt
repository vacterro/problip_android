package com.vacster.problip

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.ui.ProblipRoot
import com.vacster.problip.ui.ProblipViewModel
import com.vacster.problip.ui.theme.ProblipTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The session runs regardless; without the grant Android 13+ only
            // hides the notification (app appears in the task manager instead).
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val billing = ProblipApp.billing(this)
        setContent {
            val viewModel: ProblipViewModel = viewModel {
                ProblipViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                )
            }
            val settings by viewModel.settings.collectAsState()
            val owned by viewModel.owned.collectAsState()
            val access by viewModel.access.collectAsState()
            val ownsThemePack = ProductCatalog.THEME_PACK in owned
            // A theme trial expiring — or Developer Access ending — flips the
            // granted set, which recomposes straight into the Classic palette; no
            // Activity restart involved.
            ProblipTheme(
                themeId = settings.themeId,
                ownsThemePack = ownsThemePack,
                trials = access.grantedIds,
            ) {
                ProblipRoot(
                    viewModel = viewModel,
                    onStartRequested = {
                        maybeRequestNotificationPermission()
                        viewModel.start()
                    },
                    onPurchaseRequested = { productId -> billing.purchase(this, productId) },
                    // Task behaviour stays at the Activity boundary: Compose never
                    // casts LocalContext to an Activity. The service is untouched,
                    // so an active session keeps blipping in the background.
                    onMinimize = { moveTaskToBack(true) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reconnects after a drop and re-queries purchases (restore) even when
        // the connection is still up.
        ProblipApp.billing(this).refresh()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
