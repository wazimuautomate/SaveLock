package com.example.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.di.ServiceLocator
import com.example.util.DateUtils
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily exact lock-check alarm (AlarmManager) and the lead-time reminders (WorkManager).
 * Everything is re-armed after each fire and after boot, which is what makes the lock reliable on
 * OEMs that kill background work.
 */
object AlarmScheduler {

    const val ACTION_LOCK_CHECK = "com.example.action.LOCK_CHECK"
    private const val LOCK_REQUEST_CODE = 1001
    private const val REMINDER_TAG = "savelock_reminder"

    /** Exact alarm at the next lock time; falls back to inexact-while-idle if exact isn't permitted. */
    fun scheduleDailyLockCheck(context: Context, lockTime: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = DateUtils.nextLockOccurrenceMillis(lockTime)
        val pi = lockCheckPendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Exact-alarm permission not granted — still fire (a bit late) rather than not at all.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelDailyLockCheck(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(lockCheckPendingIntent(context))
    }

    private fun lockCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockCheckReceiver::class.java).apply { action = ACTION_LOCK_CHECK }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, LOCK_REQUEST_CODE, intent, flags)
    }

    /** One WorkManager job per lead hour, at (lockTime - leadHours). Re-enqueued daily. */
    fun scheduleReminders(context: Context, lockTime: String, leadHours: List<Int>) {
        val wm = WorkManager.getInstance(context)
        leadHours.forEach { lead ->
            val delay = DateUtils.nextReminderMillis(lockTime, lead) - System.currentTimeMillis()
            if (delay <= 0) return@forEach
            val work = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_LEAD_HOURS to lead))
                .addTag(REMINDER_TAG)
                .build()
            wm.enqueueUniqueWork("${REMINDER_TAG}_$lead", ExistingWorkPolicy.REPLACE, work)
        }
    }

    fun cancelReminders(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_TAG)
    }

    /** Read the current config and (re)schedule everything, or cancel it all if saving is disabled. */
    suspend fun rescheduleAll(context: Context) {
        val cfg = ServiceLocator.repository.getConfig()
        if (cfg.savingEnabled) {
            scheduleDailyLockCheck(context, cfg.lockTime)
            scheduleReminders(context, cfg.lockTime, cfg.reminderLeadHours)
        } else {
            cancelDailyLockCheck(context)
            cancelReminders(context)
        }
    }
}
