package com.vacster.problip

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.service.ExitPolicy
import com.vacster.problip.service.ProblipService
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.theme.ThemeCatalog
import com.vacster.problip.ui.ProblipRoot
import com.vacster.problip.ui.ProblipViewModel
import com.vacster.problip.ui.theme.ProblipTheme
import com.vacster.problip.ui.theme.paletteFor

// AppCompatActivity (not ComponentActivity) is what makes the AppCompat per-app
// locale mechanism work on API 26-32; Compose setContent is unaffected.
class MainActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The session runs regardless; without the grant Android 13+ only
            // hides the notification (app appears in the task manager instead).
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Root Compose insets own the safe area on every Android version. This
        // avoids double padding on older releases and covered controls on 35+.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
            val lightBars = paletteFor(
                ThemeCatalog.effective(settings.themeId, ownsThemePack, access.grantedIds).id,
            ).Bg.luminance() > 0.5f
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
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
                    // casts LocalContext to an Activity.
                    onMinimize = { moveTaskToBack(true) },
                    // EXIT is state-aware: a live session (STARTING/RUNNING) is
                    // stopped through the normal service path first, while
                    // STOPPED/ERROR must not spawn a service just to stop it.
                    // No System.exit, no killProcess, no force-stop hacks.
                    onExit = {
                        if (ExitPolicy.shouldStopServiceBeforeExit(ProblipSession.state.value)) {
                            ProblipService.stop(this)
                        }
                        finishAndRemoveTask()
                    },
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
