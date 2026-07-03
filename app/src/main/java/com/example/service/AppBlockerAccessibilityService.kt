package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import com.example.di.ServiceLocator
import com.example.overlay.LockOverlayActivity

/**
 * Detects when a foreground app changes and, while the lock is active, redirects blocked apps to the
 * SaveLock lock screen.
 *
 * HARD SAFETY RULE (never remove): the dialer, the default messaging app, the system UI, the current
 * keyboard, and SaveLock itself are ALWAYS allowed and can never be blocked — in either lock mode.
 * Emergency access (calling for help) must never be blockable. The user can also always escape via
 * Safe Mode, which disables this service entirely.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    // Cached allow-list of packages that must never be blocked. Rebuilt on connect.
    private var emergencyAllowed: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        emergencyAllowed = buildEmergencyAllowList()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Never touch our own screens (would loop) or emergency/system packages.
        if (pkg == packageName) return
        if (pkg in emergencyAllowed) return

        val lock = ServiceLocator.lockStateManager
        if (!lock.isLockActiveNow()) return

        // At this point the lock is active and the package is neither ours nor an emergency app.
        // Chosen-apps mode: block only ticked apps. Full-lockdown: block everything else (incl. Settings).
        if (lock.shouldBlockDistractionPackage(pkg)) {
            bringLockScreenToFront()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    private fun bringLockScreenToFront() {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        // Requires the "Display over other apps" permission to launch from the background reliably.
        runCatching { startActivity(intent) }
    }

    /**
     * Builds the ALWAYS-allowed set: dialer + default SMS app + system UI + current keyboard.
     * These can never be blocked so the user can always call/text for help and use the phone shell.
     */
    private fun buildEmergencyAllowList(): Set<String> {
        val allowed = mutableSetOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            packageName
        )
        // Default dialer
        runCatching {
            (getSystemService(TelecomManager::class.java))?.defaultDialerPackage?.let { allowed.add(it) }
        }
        // Default messaging app
        runCatching {
            Telephony.Sms.getDefaultSmsPackage(this)?.let { allowed.add(it) }
        }
        // Current keyboard (so typing a recovery code / message always works)
        runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { allowed.add(it) }
        }
        return allowed
    }
}
