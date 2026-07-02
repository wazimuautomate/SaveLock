package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationManagerHelper {
    const val CHANNEL_SAVINGS_REMINDERS = "savings_reminders"
    const val CHANNEL_LOCK_ALERTS = "lock_alerts"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Savings Reminders Channel
            val savingsChannel = NotificationChannel(
                CHANNEL_SAVINGS_REMINDERS,
                "Savings Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to complete daily savings and maintain your lock streak."
            }

            // 2. Lock Alerts Channel
            val lockChannel = NotificationChannel(
                CHANNEL_LOCK_ALERTS,
                "Lock Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for incoming app lockout schedules and restricted status."
            }

            notificationManager.createNotificationChannel(savingsChannel)
            notificationManager.createNotificationChannel(lockChannel)
        }
    }

    fun showNotification(context: Context, channelId: String, id: Int, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(
                if (channelId == CHANNEL_LOCK_ALERTS) NotificationCompat.PRIORITY_HIGH 
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }
}
