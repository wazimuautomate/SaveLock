package com.example.overlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.ServiceLocator
import com.example.ui.screens.OverlayScreen
import com.example.ui.screens.RecoveryCodeEntryScreen
import com.example.ui.theme.SaveLockTheme
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.LockOverlayViewModel
import com.example.viewmodel.RecoveryViewModel
import com.example.viewmodel.SaveLockViewModels

/**
 * The SaveLock lock screen, brought to the front by [com.example.service.AppBlockerAccessibilityService]
 * when a blocked app is opened while the lock is active. Offers: pay, enter recovery code, and (in
 * full lockdown) direct Call / Messages access.
 *
 * INTENTIONAL SAFETY BOUNDARY: this is NOT a kiosk / lock-task screen. The Home button always works
 * (in full lockdown it just re-presents this screen). There is no anti-uninstall here — the user can
 * always leave via Home, and can always escape the whole system via Safe Mode. This is deliberate.
 */
class LockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show even over the keyguard, wake + keep the screen on so the lock is actually seen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive full-screen: hide the status bar and the nav bar (home/recents/back buttons),
        // and keep them hidden if the user swipes them in.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Swallow the Back button — it must never dismiss the lock to reveal a blocked app.
        onBackPressedDispatcher.addCallback(this) { /* intentionally do nothing */ }

        setContent {
            SaveLockTheme {
                // When the day gets resolved (paid or recovery code), the lock clears — close the screen.
                val lockActive by ServiceLocator.lockStateManager.lockActive.collectAsState()
                LaunchedEffect(lockActive) { if (!lockActive) finish() }

                val lockVm: LockOverlayViewModel = viewModel(factory = SaveLockViewModels.Factory)
                val dashVm: DashboardViewModel = viewModel(factory = SaveLockViewModels.Factory)
                val recoveryVm: RecoveryViewModel = viewModel(factory = SaveLockViewModels.Factory)

                var showRecovery by remember { mutableStateOf(false) }

                if (showRecovery) {
                    RecoveryCodeEntryScreen(
                        viewModel = recoveryVm,
                        onNavigateBack = { showRecovery = false },
                        onResetSuccess = { finish() }
                    )
                } else {
                    OverlayScreen(
                        viewModel = lockVm,
                        dashboardViewModel = dashVm,
                        onNavigateToRecoveryEntry = { showRecovery = true },
                        onOpenDialer = { openDialer() },
                        onOpenMessages = { openMessages() }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openDialer() {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openMessages() {
        // Prefer the default SMS app; fall back to the messaging category launcher.
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_MESSAGING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(smsIntent) }
            .onFailure { runCatching { startActivity(fallback) } }
    }
}
