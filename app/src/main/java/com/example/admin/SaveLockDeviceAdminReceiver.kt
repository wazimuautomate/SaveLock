package com.example.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Registering as a Device Admin adds "uninstall friction": Android won't let the app be uninstalled
 * until admin is turned off first. This is a speed bump against impulse-quitting — NOT true
 * un-removability. The user can always disable admin (from here or Settings) and can always escape
 * via Safe Mode / factory reset. That escapability is an intentional safety boundary.
 */
class SaveLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off removes SaveLock's uninstall protection. After this you can uninstall the app."

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, SaveLockDeviceAdminReceiver::class.java)
    }
}
