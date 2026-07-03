package com.example.service

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Resolves the ONLY three apps a locked user may open during Full Lockdown, so they can pay offline:
 *  - SIM Toolkit  → pay via the M-Pesa menu / `*334#` with no mobile data
 *  - Messages     → read the M-Pesa confirmation code
 *  - Dialer       → dial USSD / make calls
 *
 * The enforcement service uses [packages] for its allow-list; the lock screen uses [entries] to show
 * tappable launcher buttons. There is no framework API for the SIM Toolkit, so we probe known package
 * names across OEMs and keep whichever is actually installed.
 */
object AllowedApps {

    enum class Kind { SIM_TOOLKIT, MESSAGES, DIALER }

    data class Entry(val kind: Kind, val label: String, val packageName: String, val launch: Intent?)

    private val STK_CANDIDATES = listOf(
        "com.android.stk",
        "com.android.stk2",
        "com.android.stk.simtoolkit",
        "com.samsung.android.app.stk",
        "com.sec.android.app.stk",
    )

    /** The set of package names to allow through the lockdown (never null entries). */
    fun packages(context: Context): Set<String> =
        entries(context).map { it.packageName }.filter { it.isNotBlank() }.toSet()

    /** Launcher entries for the lock screen (only the ones actually present on this phone). */
    fun entries(context: Context): List<Entry> {
        val pm = context.packageManager
        val result = mutableListOf<Entry>()

        // SIM Toolkit — first installed candidate with a launch intent.
        STK_CANDIDATES.firstNotNullOfOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { pkg to it }
        }?.let { (pkg, intent) ->
            result += Entry(Kind.SIM_TOOLKIT, "SIM Toolkit", pkg, intent.newTask())
        }

        // Messages — the default SMS app.
        Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { it.isNotBlank() }?.let { pkg ->
            result += Entry(Kind.MESSAGES, "Messages", pkg, pm.getLaunchIntentForPackage(pkg)?.newTask())
        }

        // Dialer — the default dialer; ACTION_DIAL reliably opens it (for *334# / calls).
        val dialerPkg = runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull().orEmpty()
        result += Entry(
            Kind.DIALER,
            "Phone",
            dialerPkg.ifBlank { "com.android.dialer" },
            Intent(Intent.ACTION_DIAL).newTask(),
        )

        return result
    }

    private fun Intent.newTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
