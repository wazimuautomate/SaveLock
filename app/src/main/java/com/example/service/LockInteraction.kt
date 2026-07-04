package com.example.service

/**
 * Tiny shared state that lets the lock UI tell the enforcement service to *briefly* step aside for a
 * sanctioned system surface — WITHOUT lifting the lock or opening a general escape hatch:
 *
 *  - [paymentInProgress]: an STK payment is running, so the M-Pesa PIN dialog must be usable. The
 *    overlay stays visible as a dimmed lock page, but becomes temporarily tap-through for the STK
 *    startup window or recognized M-Pesa/STK payment surfaces.
 *  - [allowSettingsUntil]: the user tapped "turn on WiFi/data", so the Settings quick-panel is allowed
 *    for a short window. Only the Settings app is permitted during it — every other app still locks.
 */
object LockInteraction {

    @Volatile var paymentInProgress: Boolean = false
        private set

    @Volatile private var paymentPromptUntil: Long = 0L

    @Volatile var allowSettingsUntil: Long = 0L

    @Volatile private var allowedLaunchPackage: String = ""
    @Volatile private var allowedLaunchUntil: Long = 0L

    fun settingsAllowedNow(): Boolean = System.currentTimeMillis() < allowSettingsUntil

    fun setPaymentInProgress(active: Boolean) {
        paymentInProgress = active
        paymentPromptUntil = if (active) System.currentTimeMillis() + PAYMENT_PROMPT_STARTUP_MS else 0L
    }

    fun shouldHoldPaymentPrompt(currentForeground: String): Boolean =
        paymentInProgress &&
            (
                System.currentTimeMillis() < paymentPromptUntil ||
                    currentForeground.isBlank() ||
                    paymentPromptAllowedNow(currentForeground)
            )

    fun paymentPromptAllowedNow(packageName: String): Boolean =
        paymentInProgress &&
            packageName.isNotBlank() &&
            (
                packageName in PAYMENT_PROMPT_PACKAGES ||
                    packageName.contains(".stk", ignoreCase = true) ||
                    packageName.contains("simtoolkit", ignoreCase = true) ||
                    packageName.contains("mpesa", ignoreCase = true)
            )

    /** Permit the Settings app (only) for [seconds] so the WiFi / internet panel can be used. */
    fun grantSettings(seconds: Int = 60) {
        allowSettingsUntil = System.currentTimeMillis() + seconds * 1000L
    }

    /**
     * Permit the exact whitelisted package that the lock screen is launching. This avoids Samsung's
     * transient SystemUI/launcher events from slamming the overlay back before Messages, Phone or SIM
     * Toolkit reaches the foreground. It is intentionally package-specific and short-lived.
     */
    fun grantAllowedLaunch(packageName: String, seconds: Int = 8) {
        allowedLaunchPackage = packageName
        allowedLaunchUntil = System.currentTimeMillis() + seconds * 1000L
    }

    fun allowedLaunchActiveNow(): Boolean =
        allowedLaunchPackage.isNotBlank() && System.currentTimeMillis() < allowedLaunchUntil

    fun packageLaunchAllowedNow(packageName: String): Boolean =
        packageName.isNotBlank() &&
            packageName == allowedLaunchPackage &&
            System.currentTimeMillis() < allowedLaunchUntil

    fun clearAllowedLaunch() {
        allowedLaunchPackage = ""
        allowedLaunchUntil = 0L
    }

    private val PAYMENT_PROMPT_PACKAGES = setOf(
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.stk",
        "com.android.stk2",
        "com.android.stk3",
        "com.android.stk.simtoolkit",
        "com.android.systemui",
        "com.mediatek.stk",
        "com.mediatek.StkSelection",
        "com.qualcomm.qti.simsettings",
        "com.safaricom.mpesa",
        "com.safaricom.mpesa.lifestyle",
        "com.samsung.android.app.stk",
        "com.sec.android.app.stk",
    )

    private const val PAYMENT_PROMPT_STARTUP_MS = 15_000L
}
