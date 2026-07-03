package com.example.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.data.local.entity.LockMode
import com.example.di.ServiceLocator
import com.example.util.NotificationManagerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Low-priority foreground service that supervises the day while the lock is active. A persistent
 * notification (Android 14+ requirement) keeps the process alive so the lock survives task-kill
 * better on aggressive OEMs. It stops itself as soon as the lock is no longer active (day resolved,
 * saving disabled, or before the deadline).
 *
 * This does NOT lock anything by itself — the AccessibilityService does the blocking. The service is
 * just a resilience anchor.
 */
class SaveLockForegroundService : Service() {

    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = when (ServiceLocator.lockStateManager.lockMode()) {
            LockMode.FULL_LOCKDOWN -> "Full lockdown active — save or use a recovery code to unlock."
            LockMode.CHOSEN_APPS -> "Distraction apps are locked until you save today."
        }
        startForegroundCompat(NotificationManagerHelper.buildForegroundNotification(this, text))

        if (observeJob == null) {
            observeJob = ServiceLocator.appScope.launch {
                ServiceLocator.lockStateManager.lockActive.collect { active ->
                    val fullLockdown = ServiceLocator.lockStateManager.lockMode() == LockMode.FULL_LOCKDOWN
                    if (active && fullLockdown) {
                        ShadeGuard.show(applicationContext) // block the notification shade pull-down
                    } else {
                        ShadeGuard.hide(applicationContext)
                    }
                    if (!active) stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationManagerHelper.ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationManagerHelper.ID_FOREGROUND, notification)
        }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        ShadeGuard.hide(applicationContext)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SaveLockForegroundService::class.java)
            // Starting a foreground service from the background is restricted on Android 12+; wrap
            // so a disallowed start never crashes. The AccessibilityService enforces the lock either
            // way — this service is only a resilience anchor.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SaveLockForegroundService::class.java))
        }
    }
}
