package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.data.local.entity.LockMode
import com.example.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The enforcement brain. It watches which app is in the foreground and, while the lock is active,
 * keeps SaveLock's full-screen overlay on top (via [LockScreenController]).
 *
 * WHY THIS IS STRONG:
 *  - the overlay is a focusable `TYPE_APPLICATION_OVERLAY` that stays above the launcher/recents and
 *    swallows Back, so Home / Recents / Back can't reveal what's behind it;
 *  - it re-asserts on EVERY foreground change, on screen-on / unlock (the power-button timing gap),
 *    and on a short safety ticker — so even if something briefly slips through, it re-locks instantly.
 *
 * SAFETY: still a soft lock — Safe Mode disables this service (removing the overlay) and factory reset
 * always wins, so SaveLock is never truly unremovable.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private var emergencyAllowed: Set<String> = emptySet()
    private var currentForeground: String = ""
    private var lockCollector: Job? = null
    private var reassertTicker: Job? = null

    // Screen on / unlock is exactly when some OEMs let an overlay slip behind — re-assert then.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> reassert()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        emergencyAllowed = buildEmergencyAllowList()

        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        }

        if (lockCollector == null) {
            lockCollector = ServiceLocator.appScope.launch {
                ServiceLocator.lockStateManager.lockActive.collect { updateOverlay() }
            }
        }
        // Safety net: if the overlay ever gets removed while it should be up (OEM kill, race), re-add it.
        if (reassertTicker == null) {
            reassertTicker = ServiceLocator.appScope.launch {
                while (true) {
                    delay(1500)
                    if (shouldShowNow()) LockScreenController.show(this@AppBlockerAccessibilityService)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore our own overlay/app so its appearance doesn't count as "the user left the blocked app".
        if (pkg == packageName) return
        currentForeground = pkg
        updateOverlay()
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        lockCollector?.cancel(); lockCollector = null
        reassertTicker?.cancel(); reassertTicker = null
        runCatching { unregisterReceiver(screenReceiver) }
        LockScreenController.hide(this)
        return super.onUnbind(intent)
    }

    /** Should the lock overlay be up right now? */
    private fun shouldShowNow(): Boolean {
        val lock = ServiceLocator.lockStateManager
        return lock.isLockActiveNow() && when (lock.lockMode()) {
            // Full lockdown: cover the phone the entire time the lock is active.
            LockMode.FULL_LOCKDOWN -> true
            // Chosen apps: cover only while one of the user's picked apps is in front.
            LockMode.CHOSEN_APPS -> currentForeground.isNotEmpty() &&
                currentForeground !in emergencyAllowed &&
                lock.shouldBlockDistractionPackage(currentForeground)
        }
    }

    /** Add or remove the overlay to match [shouldShowNow]. */
    private fun updateOverlay() {
        if (shouldShowNow()) LockScreenController.show(this) else LockScreenController.hide(this)
    }

    /** Like [updateOverlay] but forces a fresh window on top (used after screen-on / unlock). */
    private fun reassert() {
        if (shouldShowNow()) LockScreenController.forceReshow(this) else LockScreenController.hide(this)
    }

    /**
     * Telephony/emergency infrastructure + system UI + current keyboard + SaveLock itself. In
     * CHOSEN_APPS these never trigger the lock. (FULL_LOCKDOWN covers everything regardless.)
     */
    private fun buildEmergencyAllowList(): Set<String> {
        val allowed = mutableSetOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            packageName
        )
        runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { allowed.add(it) }
        }
        return allowed
    }
}
