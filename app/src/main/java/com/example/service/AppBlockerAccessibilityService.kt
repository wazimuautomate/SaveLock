package com.example.service

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.data.local.entity.LockMode
import com.example.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The enforcement brain. It watches which app is in the foreground and, while the lock is active,
 * puts SaveLock's full-screen accessibility overlay on top (via [LockScreenController]).
 *
 * WHY THIS IS STRONG: the overlay is a `TYPE_ACCESSIBILITY_OVERLAY` window that floats above the
 * launcher, recents, the status/nav bars and system dialogs, and it is focusable so it swallows the
 * Back key. In FULL_LOCKDOWN it stays up the entire time the lock is active, so Home / Recents / Back
 * cannot reveal anything. In CHOSEN_APPS it appears only over the apps the user picked.
 *
 * SAFETY: this is still a soft lock — Safe Mode disables the accessibility service (removing the
 * overlay) and factory reset always wins, so SaveLock is never truly unremovable.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private var emergencyAllowed: Set<String> = emptySet()
    private var currentForeground: String = ""
    private var lockCollector: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        emergencyAllowed = buildEmergencyAllowList()
        // React the instant the lock turns on/off (payment, recovery code, new period, disable).
        if (lockCollector == null) {
            lockCollector = ServiceLocator.appScope.launch {
                ServiceLocator.lockStateManager.lockActive.collect { updateOverlay() }
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        lockCollector?.cancel(); lockCollector = null
        LockScreenController.hide(this)
        return super.onUnbind(intent)
    }

    /** Decide whether the lock overlay should be up right now, and add/remove it accordingly. */
    private fun updateOverlay() {
        val lock = ServiceLocator.lockStateManager
        val shouldShow = lock.isLockActiveNow() && when (lock.lockMode()) {
            // Full lockdown: cover the phone the entire time the lock is active.
            LockMode.FULL_LOCKDOWN -> true
            // Chosen apps: cover only while one of the user's picked apps is in front.
            LockMode.CHOSEN_APPS -> currentForeground.isNotEmpty() &&
                currentForeground !in emergencyAllowed &&
                lock.shouldBlockDistractionPackage(currentForeground)
        }
        if (shouldShow) LockScreenController.show(this) else LockScreenController.hide(this)
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
