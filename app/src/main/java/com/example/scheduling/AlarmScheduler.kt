package com.example.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import com.example.di.ServiceLocator
import com.example.domain.PlanLogic

/**
 * Schedules an exact lock-check alarm (AlarmManager) at the next moment ANY active plan starts a new
 * period (a new period that isn't yet paid means the lock should arm). Re-armed after each fire and
 * after boot, which is what makes the lock reliable on OEMs that kill background work.
 */
object AlarmScheduler {

    const val ACTION_LOCK_CHECK = "com.example.action.LOCK_CHECK"
    private const val LOCK_REQUEST_CODE = 1001
    private const val REMINDER_TAG = "savelock_reminder"

    /** Exact alarm at [triggerAt]; falls back to inexact-while-idle if exact isn't permitted. */
    fun scheduleLockCheckAt(context: Context, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
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

    fun cancelReminders(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_TAG)
    }

    /**
     * Read the current plans and arm the alarm for the earliest upcoming period boundary, or cancel
     * everything if saving is disabled / there are no active plans.
     */
    suspend fun rescheduleAll(context: Context) {
        val cfg = ServiceLocator.repository.getConfig()
        val plans = ServiceLocator.repository.getActivePlans()
        cancelReminders(context) // lead-time reminders were removed in favour of per-plan periods
        if (cfg.savingEnabled && cfg.lockStarted && plans.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val next = plans.minOf { PlanLogic.nextBoundaryMillis(it, now) }
            scheduleLockCheckAt(context, next)
        } else {
            cancelDailyLockCheck(context)
        }
    }
}
