package com.example.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.di.ServiceLocator
import com.example.util.NotificationManagerHelper

/**
 * Shows a lead-time reminder notification, unless the day is already saved or saving is off.
 * WorkManager may run this a little late under Doze — acceptable for a nudge (the exact-time
 * guarantee is reserved for the lock alarm).
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val lead = inputData.getInt(KEY_LEAD_HOURS, 1)
        val repo = ServiceLocator.repository
        val cfg = repo.getConfig()
        if (cfg.savingEnabled && !repo.isTodayResolved()) {
            NotificationManagerHelper.showReminder(applicationContext, lead, cfg.dailyAmount)
        }
        return Result.success()
    }

    companion object {
        const val KEY_LEAD_HOURS = "lead_hours"
    }
}
