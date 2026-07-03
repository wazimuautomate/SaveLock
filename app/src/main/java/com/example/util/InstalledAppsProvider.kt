package com.example.util

import android.content.Context
import android.content.Intent

/** Lists the real launchable apps installed on THIS phone, so the user picks from what they have. */
object InstalledAppsProvider {

    data class AppInfo(val packageName: String, val label: String)

    fun launchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) null // never list SaveLock itself
                    else AppInfo(pkg, ri.loadLabel(pm).toString())
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }
}
