package com.example.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.di.ServiceLocator
import com.example.ui.screens.LockScreenContent
import com.example.ui.theme.SaveLockTheme
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.LockOverlayViewModel
import com.example.viewmodel.RecoveryViewModel

/**
 * Owns the actual lock UI as a full-screen **system overlay window** (`TYPE_APPLICATION_OVERLAY`,
 * i.e. the "Display over other apps" permission). Unlike an Activity, this window stays ON TOP of the
 * launcher and other apps, so pressing Home or opening Recents does not reveal what is behind it.
 * Because the window is focusable it also swallows the Back key. The keyboard still works over it
 * (needed for the recovery code / phone number), which is why we use this type rather than an
 * accessibility overlay (which can hide the IME). [ShadeGuard] blocks the notification shade on top.
 *
 * This is the strongest lock a non-device-owner (sideloaded) app can do. It is still a SOFT lock:
 * Safe Mode disables the accessibility service, which stops this overlay from being (re)shown — the
 * deliberate, documented escape hatch that keeps SaveLock uninstallable.
 */
object LockScreenController {

    private val main = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lifecycleHost: OverlayLifecycleHost? = null

    // Long-lived ViewModels for the lock UI (created once, reused for the life of the process).
    private val dashboardVm by lazy {
        DashboardViewModel(ServiceLocator.repository, ServiceLocator.paymentRepository)
    }
    private val lockVm by lazy { LockOverlayViewModel(ServiceLocator.repository) }
    private val recoveryVm by lazy { RecoveryViewModel(ServiceLocator.repository) }

    fun isShowing(): Boolean = overlay != null

    /** Add the lock overlay (idempotent). Requires the "Display over other apps" permission. */
    fun show(context: Context) {
        main.post { if (overlay == null) addOverlay(context) }
    }

    /**
     * Remove and re-add the overlay so it is guaranteed to be on top and focused again. Called after
     * the screen turns on / the phone is unlocked, which is when some OEMs let the overlay slip behind.
     */
    fun forceReshow(context: Context) {
        main.post { removeOverlay(context); addOverlay(context) }
    }

    /** Remove the lock overlay (idempotent). */
    fun hide(context: Context) {
        main.post { removeOverlay(context) }
    }

    private fun addOverlay(context: Context) {
        run {
            if (overlay != null) return
            if (!Settings.canDrawOverlays(context)) return
            val wm = context.getSystemService(WindowManager::class.java) ?: return

            val host = OverlayLifecycleHost().apply { create() }
            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(host)
                setViewTreeViewModelStoreOwner(host)
                setViewTreeSavedStateRegistryOwner(host)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    SaveLockTheme {
                        LockScreenContent(dashboardVm, lockVm, recoveryVm)
                    }
                }
                // Focusable so we receive and swallow the Back key.
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                // Focusable (no FLAG_NOT_FOCUSABLE) so Back is captured and the keyboard works. Extend
                // into the system-bar areas so as much of them as possible is covered.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }

            runCatching {
                wm.addView(composeView, params)
                composeView.requestFocus()
                overlay = composeView
                lifecycleHost = host
            }.onFailure { host.destroy() }
        }
    }

    private fun removeOverlay(context: Context) {
        val v = overlay ?: return
        val wm = context.getSystemService(WindowManager::class.java)
        runCatching { wm?.removeView(v) }
        lifecycleHost?.destroy()
        lifecycleHost = null
        overlay = null
    }

    /**
     * Minimal owner so a [ComposeView] can run outside an Activity (in a WindowManager overlay).
     * Provides the Lifecycle, ViewModelStore and SavedStateRegistry that Compose requires.
     */
    private class OverlayLifecycleHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun create() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }
}
