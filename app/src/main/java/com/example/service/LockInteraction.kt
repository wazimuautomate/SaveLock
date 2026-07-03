package com.example.service

/**
 * Tiny shared state that lets the lock UI tell the enforcement service to *briefly* step aside for a
 * sanctioned system surface — WITHOUT lifting the lock or opening a general escape hatch:
 *
 *  - [paymentInProgress]: an STK payment is running, so the M-Pesa PIN dialog must be usable. The
 *    overlay stays VISIBLE (the phone still looks locked) but becomes non-focusable and stops
 *    re-asserting, so the PIN prompt shows on top of it. No other app becomes reachable.
 *  - [allowSettingsUntil]: the user tapped "turn on WiFi/data", so the Settings quick-panel is allowed
 *    for a short window. Only the Settings app is permitted during it — every other app still locks.
 */
object LockInteraction {

    @Volatile var paymentInProgress: Boolean = false

    @Volatile var allowSettingsUntil: Long = 0L

    fun settingsAllowedNow(): Boolean = System.currentTimeMillis() < allowSettingsUntil

    /** Permit the Settings app (only) for [seconds] so the WiFi / internet panel can be used. */
    fun grantSettings(seconds: Int = 60) {
        allowSettingsUntil = System.currentTimeMillis() + seconds * 1000L
    }
}
