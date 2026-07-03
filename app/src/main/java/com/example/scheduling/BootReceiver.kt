package com.example.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.ServiceLocator
import com.example.service.SaveLockForegroundService
import kotlinx.coroutines.launch

/**
 * Re-arms everything after a reboot (alarms are cleared on boot). Also restarts the lock if the
 * device booted with any plan due-and-unpaid — this is what makes the lock "survive a restart".
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        val app = context.applicationContext
        ServiceLocator.appScope.launch {
            try {
                AlarmScheduler.rescheduleAll(app)
                if (ServiceLocator.lockStateManager.recompute()) {
                    SaveLockForegroundService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
