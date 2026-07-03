package com.example.util

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.admin.SaveLockDeviceAdminReceiver
import com.example.service.AppBlockerAccessibilityService

/**
 * Reads the status of, and opens the system screens for, the permissions SaveLock needs. None of
 * these can be granted silently on Android 13+ — the user must flip each toggle themselves.
 */
object PermissionsHelper {

    // ---- Status checks -------------------------------------------------------------------------

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${AppBlockerAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java)
        return am?.canScheduleExactAlarms() == true
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(SaveLockDeviceAdminReceiver.componentName(context))
    }

    // ---- Open the relevant system screen -------------------------------------------------------

    fun openAccessibilitySettings(context: Context) =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openOverlaySettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    )

    fun openNotificationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    /** Ask the system to ignore battery optimizations for us (the "Unrestricted" battery setting). */
    fun openBatteryOptimizationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    )

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(
                context,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun requestDeviceAdmin(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SaveLockDeviceAdminReceiver.componentName(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable uninstall protection: you'll need to turn this off before uninstalling SaveLock."
            )
        start(context, intent)
    }

    /** Fallback app-details screen (for battery menus that vary by OEM, e.g. Samsung). */
    fun openAppDetails(context: Context) = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
