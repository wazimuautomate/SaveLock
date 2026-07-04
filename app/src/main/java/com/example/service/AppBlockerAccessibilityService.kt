package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
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

    private var chosenModeNeverBlocked: Set<String> = emptySet()
    // Full-lockdown allow-list: SaveLock + keyboard + SIM Toolkit + Messages + Dialer. Nothing else.
    private var lockdownAllowed: Set<String> = emptySet()
    private var keyboardPackages: Set<String> = emptySet()
    // The Settings app(s) — allowed only briefly while the user turns WiFi/data on from the lock screen.
    private var settingsPackages: Set<String> = setOf("com.android.settings")
    private var currentForeground: String = ""
    private var lockCollector: Job? = null
    private var reassertTicker: Job? = null

    // Screen on / unlock is exactly when some OEMs let an overlay slip behind — re-assert then.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> reassert(forceLock = false)
                Intent.ACTION_USER_PRESENT -> reassert(forceLock = true)
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
                    refreshAllowLists()
                    if (!LockInteraction.paymentInProgress && shouldShowNow()) {
                        LockScreenController.show(this@AppBlockerAccessibilityService)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType !in WATCHED_EVENT_TYPES) return
        val pkg = event.packageName?.toString().orEmpty()
        // Ignore our own overlay/app so its appearance doesn't count as "the user left the blocked app".
        if (pkg == packageName) return

        // IME packages are not foreground apps. Let them type over SaveLock or over the M-Pesa PIN
        // surface without changing the app we are judging underneath.
        if (pkg in keyboardPackages) return

        // Samsung often emits SystemUI while launching an allowed shortcut from our overlay. Ignore
        // that short transition only when SaveLock itself granted an exact allowed-app launch.
        if (pkg == SYSTEM_UI && LockInteraction.allowedLaunchActiveNow()) return

        if (pkg.isNotBlank()) currentForeground = pkg
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
            LockMode.FULL_LOCKDOWN ->
                currentForeground !in effectiveLockdownAllowed() &&
                    !LockInteraction.packageLaunchAllowedNow(currentForeground)
            // Chosen apps: cover only while one of the user's picked apps is in front.
            LockMode.CHOSEN_APPS -> currentForeground.isNotEmpty() &&
                currentForeground !in chosenModeNeverBlocked &&
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
    private fun reassert(forceLock: Boolean) {
        // Never re-add during payment — a fresh window would cover the M-Pesa PIN dialog.
        if (LockInteraction.paymentInProgress) return
        refreshAllowLists() // pick up any change to the default SMS/Dialer app since connect
        if (forceLock && ServiceLocator.lockStateManager.isLockActiveNow()) {
            // Critical Samsung A05 path: user opens Settings from the phone lock screen, enters the
            // pattern, and Android lands straight in Settings. After USER_PRESENT, force SaveLock on
            // top before trusting any stale allowed/keyboard foreground package.
            currentForeground = ""
            LockInteraction.clearAllowedLaunch()
            LockScreenController.forceReshow(this)
            return
        }
        if (shouldShowNow()) LockScreenController.forceReshow(this) else LockScreenController.hide(this)
    }

    /** (Re)compute the emergency + full-lockdown allow-lists. Cheap; called on connect and each tick. */
    private fun refreshAllowLists() {
        keyboardPackages = buildKeyboardPackages()
        chosenModeNeverBlocked = buildChosenModeNeverBlocked()
        // Full lockdown permits ONLY SaveLock infrastructure, the keyboard, SIM Toolkit (offline USSD
        // paying), the Messages app (to read the M-Pesa code), and the Dialer (to dial *334# / calls).
        // Do not include com.android.systemui here: on Samsung A05 the notification shade reports as
        // SystemUI, and treating it as allowed lets the overlay disappear when the shade is pulled.
        lockdownAllowed = buildFullLockdownAllowed() + keyboardPackages + buildAllowedAppPackages()
    }

    /**
     * Telephony/emergency infrastructure + system UI + current keyboard + SaveLock itself. In chosen
     * apps mode these never trigger the lock.
     */
    private fun buildChosenModeNeverBlocked(): Set<String> =
        buildFullLockdownAllowed() + "com.android.systemui"

    /** SaveLock + telephony infrastructure + current keyboard are needed while full lockdown is up. */
    private fun buildFullLockdownAllowed(): Set<String> {
        val allowed = mutableSetOf(
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

    /** Current + common keyboard packages. They must never be treated as blocked foreground apps. */
    private fun buildKeyboardPackages(): Set<String> {
        val allowed = mutableSetOf(
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.swiftkey.swiftkeyconfigurator",
            "com.touchtype.swiftkey",
        )
        runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { allowed.add(it) }
        }
        runCatching {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.mapNotNull { it.packageName.takeIf { pkg -> pkg.isNotBlank() } }
                ?.let { allowed.addAll(it) }
        }
        return allowed
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"

        val WATCHED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        )
    }
}
