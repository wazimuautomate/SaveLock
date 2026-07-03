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
    // Full-lockdown allow-list: emergency/system infra + SIM Toolkit + Messages + Dialer. Nothing else.
    private var lockdownAllowed: Set<String> = emptySet()
    // The Settings app(s) — allowed only briefly while the user turns WiFi/data on from the lock screen.
    private var settingsPackages: Set<String> = setOf("com.android.settings")
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
        refreshAllowLists()

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
        // Skipped while a payment is running so the re-add doesn't slam our window on top of the M-Pesa
        // PIN dialog (see LockInteraction.paymentInProgress).
        if (reassertTicker == null) {
            reassertTicker = ServiceLocator.appScope.launch {
                while (true) {
                    delay(1200)
                    if (!LockInteraction.paymentInProgress && shouldShowNow()) {
                        LockScreenController.show(this@AppBlockerAccessibilityService)
                    }
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
        if (!lock.isLockActiveNow()) return false
        return when (lock.lockMode()) {
            // Full lockdown: allow ONLY SIM Toolkit, Messages, Dialer (+ emergency/system infra, and
            // the Settings app briefly while enabling WiFi/data). EVERY other app — and the launcher /
            // recents / an empty foreground — is covered. This is what makes Messages un-abusable: the
            // instant any non-allowed app comes forward, this returns true and the overlay slams back.
            LockMode.FULL_LOCKDOWN -> currentForeground !in effectiveLockdownAllowed()
            // Chosen apps: cover only while one of the user's picked apps is in front.
            LockMode.CHOSEN_APPS -> currentForeground.isNotEmpty() &&
                currentForeground !in emergencyAllowed &&
                lock.shouldBlockDistractionPackage(currentForeground)
        }
    }

    /** The lockdown allow-list right now (adds the Settings app only during a WiFi/data grant window). */
    private fun effectiveLockdownAllowed(): Set<String> =
        if (LockInteraction.settingsAllowedNow()) lockdownAllowed + settingsPackages else lockdownAllowed

    /** Add or remove the overlay to match [shouldShowNow]. */
    private fun updateOverlay() {
        if (shouldShowNow()) LockScreenController.show(this) else LockScreenController.hide(this)
    }

    /** Like [updateOverlay] but forces a fresh window on top (used after screen-on / unlock). */
    private fun reassert() {
        // Never re-add during payment — a fresh window would cover the M-Pesa PIN dialog.
        if (LockInteraction.paymentInProgress) return
        refreshAllowLists() // pick up any change to the default SMS/Dialer app since connect
        if (shouldShowNow()) LockScreenController.forceReshow(this) else LockScreenController.hide(this)
    }

    /** (Re)compute the emergency + full-lockdown allow-lists. Cheap; called on connect and each tick. */
    private fun refreshAllowLists() {
        emergencyAllowed = buildEmergencyAllowList()
        // Full lockdown additionally permits ONLY: SIM Toolkit (offline USSD paying), the Messages app
        // (to read the M-Pesa code), and the Dialer (to dial *334# / make calls).
        lockdownAllowed = emergencyAllowed + buildAllowedAppPackages()
    }

    /**
     * Telephony/emergency infrastructure + system UI + current keyboard + SaveLock itself. In
     * CHOSEN_APPS these never trigger the lock.
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

    /** The three apps a locked user may open: SIM Toolkit, the default Messages app, the Dialer. */
    private fun buildAllowedAppPackages(): Set<String> = AllowedApps.packages(this)
}
