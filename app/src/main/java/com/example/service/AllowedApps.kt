package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        "com.android.stk3",
        "com.android.stk.simtoolkit",
        "com.samsung.android.app.stk",
        "com.sec.android.app.stk",
        "com.mediatek.stk",
        "com.mediatek.StkSelection",
        "com.qualcomm.qti.simsettings",
    )

    private val MESSAGE_CANDIDATES = listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
    )

    private val DIALER_CANDIDATES = listOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.sec.android.app.dialertab",
        "com.samsung.android.incallui",
        "com.android.incallui",
    )

    /** The set of package names to allow through the lockdown (never null entries). */
    fun packages(context: Context): Set<String> {
        val pm = context.packageManager
        val installedStk = STK_CANDIDATES.filter { pm.isInstalled(it) }
        val installedMessages = MESSAGE_CANDIDATES.filter { pm.isInstalled(it) }
        val installedDialers = DIALER_CANDIDATES.filter { pm.isInstalled(it) }
        return (
            entries(context).map { it.packageName } +
                installedStk +
                installedMessages +
                installedDialers
        ).filter { it.isNotBlank() }.toSet()
    }

    /** Launcher entries for the lock screen (only the ones actually present on this phone). */
    fun entries(context: Context): List<Entry> {
        val pm = context.packageManager
        val result = mutableListOf<Entry>()

        // SIM Toolkit — first installed candidate with a launch intent, then label/package scan.
        val simToolkit = STK_CANDIDATES.firstNotNullOfOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { pkg to it }
        } ?: findLaunchableSimToolkit(pm)
        simToolkit?.let { (pkg, intent) ->
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

    private fun PackageManager.isInstalled(packageName: String): Boolean =
        runCatching { getPackageInfo(packageName, 0); true }.getOrDefault(false)

    private fun findLaunchableSimToolkit(pm: PackageManager): Pair<String, Intent>? {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .firstNotNullOfOrNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@firstNotNullOfOrNull null
                val label = info.loadLabel(pm)?.toString().orEmpty()
                val looksLikeStk = pkg.contains(".stk", ignoreCase = true) ||
                    label.contains("SIM Toolkit", ignoreCase = true) ||
                    label.equals("STK", ignoreCase = true)
                if (!looksLikeStk) return@firstNotNullOfOrNull null
                pm.getLaunchIntentForPackage(pkg)?.let { pkg to it }
            }
    }
}
