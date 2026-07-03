package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Owns the notification channels and builds the app's notifications.
 *
 * Channels:
 *  - [CHANNEL_SAVINGS_REMINDERS] ("reminders") — the 2h/1h-before nudges.
 *  - [CHANNEL_LOCK_ALERTS] ("status") — lock-triggered / payment-success / payment-failed.
 *  - [CHANNEL_FOREGROUND] — the persistent low-priority notification for the supervising service.
 */
object NotificationManagerHelper {
    const val CHANNEL_SAVINGS_REMINDERS = "savings_reminders"
    const val CHANNEL_LOCK_ALERTS = "lock_alerts"
    const val CHANNEL_FOREGROUND = "foreground_service"

    const val ID_FOREGROUND = 1
    const val ID_LOCK_ACTIVE = 2
    const val ID_PAYMENT = 3
    private const val ID_REMINDER_BASE = 100

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SAVINGS_REMINDERS,
                    "Savings Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Reminders to complete daily savings before the deadline." }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LOCK_ALERTS,
                    "Lock & Payment Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Lock-triggered alerts and payment results." }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FOREGROUND,
                    "Running in background",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Keeps SaveLock supervising your day so the lock is reliable." }
            )
        }
    }

    /** Persistent, low-priority notification required for the supervising foreground service. */
    fun buildForegroundNotification(context: Context, contentText: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("SaveLock is active")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .build()

    /** Fired when a plan becomes due-and-unpaid and the lock turns on. [amount] 0 = unspecified. */
    fun showLockActive(context: Context, amount: Int) {
        val body = if (amount > 0) "You still owe KES $amount. Pay or use a recovery code to unlock."
        else "A savings payment is due. Pay a plan or use a recovery code to unlock."
        notify(
            context,
            CHANNEL_LOCK_ALERTS,
            ID_LOCK_ACTIVE,
            "Apps locked — save to unlock",
            body,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /** A lead-time reminder before the deadline. */
    fun showReminder(context: Context, leadHours: Int, amount: Int) {
        val whenText = if (leadHours == 1) "1 hour" else "$leadHours hours"
        notify(
            context,
            CHANNEL_SAVINGS_REMINDERS,
            ID_REMINDER_BASE + leadHours,
            "Save in $whenText",
            "Save KES $amount before the deadline to keep your streak and avoid the lock.",
            NotificationCompat.PRIORITY_DEFAULT
        )
    }

    fun showPaymentResult(context: Context, success: Boolean, amount: Int) {
        notify(
            context,
            CHANNEL_LOCK_ALERTS,
            ID_PAYMENT,
            if (success) "Saved! KES $amount" else "Payment failed",
            if (success) "Your daily save is done and apps are unlocked."
            else "The M-Pesa payment didn't go through. Please try again.",
            NotificationCompat.PRIORITY_HIGH
        )
    }

    /** Remove the "apps locked" notification once the day is resolved. */
    fun clearLockActive(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID_LOCK_ACTIVE)
    }

    /** Generic helper retained for the Settings test buttons. */
    fun showNotification(context: Context, channelId: String, id: Int, title: String, message: String) {
        notify(
            context, channelId, id, title, message,
            if (channelId == CHANNEL_LOCK_ALERTS) NotificationCompat.PRIORITY_HIGH
            else NotificationCompat.PRIORITY_DEFAULT
        )
    }

    private fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        message: String,
        priority: Int
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, builder.build())
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
}
