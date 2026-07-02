package com.example.data.local.entity

/**
 * How aggressively the lock behaves once the deadline passes with the day unsaved.
 *
 * SAFETY NOTE (applies to BOTH modes): emergency **phone calls** and the **messaging app** are
 * ALWAYS allowed and can never be blocked. This is a hard rule enforced in the AccessibilityService,
 * so the user can never be cut off from calling for help. The ultimate escape for either mode is
 * always: pay, use a recovery code, or boot the phone into Safe Mode (which disables the service).
 */
enum class LockMode {
    /** Blocks only the distraction apps the user ticked. Phone, Messages, Settings, everything else
     *  stays usable. The gentler, default mode. */
    CHOSEN_APPS,

    /** Blocks EVERYTHING except phone calls and the messaging app — including Settings. The strict
     *  mode the user explicitly requested. Escape only via pay / recovery code / Safe Mode. */
    FULL_LOCKDOWN
}
