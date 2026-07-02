package com.example.data.repository

import com.example.data.local.dao.RecoveryCodeDao
import com.example.data.local.dao.SavingsConfigDao
import com.example.data.local.dao.SavingsLogDao
import com.example.data.local.entity.RecoveryCodeEntity
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsLogEntity
import com.example.data.local.entity.SavingsStatus
import com.example.domain.RecoveryCodeManager
import com.example.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The single source of truth for the app's data. Exposes Kotlin [Flow]s that the ViewModels collect,
 * and suspend functions that write to Room. No Android UI or network concerns live here.
 */
class SaveLockRepository(
    private val configDao: SavingsConfigDao,
    private val logDao: SavingsLogDao,
    private val recoveryDao: RecoveryCodeDao,
    private val recoveryCodeManager: RecoveryCodeManager
) {

    // ---- Config ---------------------------------------------------------------------------------

    /** Always emits a non-null config; if the row is missing it falls back to defaults. */
    val config: Flow<SavingsConfigEntity> =
        configDao.observe().map { it ?: SavingsConfigEntity() }

    /** Insert the default config row on first ever launch. Safe to call every startup. */
    suspend fun ensureSeeded() {
        if (configDao.get() == null) {
            configDao.upsert(SavingsConfigEntity())
        }
    }

    suspend fun getConfig(): SavingsConfigEntity = configDao.get() ?: SavingsConfigEntity()

    private suspend fun updateConfig(transform: (SavingsConfigEntity) -> SavingsConfigEntity) {
        val current = configDao.get() ?: SavingsConfigEntity()
        configDao.upsert(transform(current))
    }

    suspend fun setDailyAmount(amount: Int) = updateConfig { it.copy(dailyAmount = amount) }
    suspend fun setLockTime(time: String) = updateConfig { it.copy(lockTime = time) }
    suspend fun setMpesaNumber(number: String) = updateConfig { it.copy(mpesaNumber = number) }
    suspend fun setSavingEnabled(enabled: Boolean) = updateConfig { it.copy(savingEnabled = enabled) }

    suspend fun addReminderLeadHour(hours: Int) = updateConfig {
        if (hours in 1..23 && hours !in it.reminderLeadHours) {
            it.copy(reminderLeadHours = (it.reminderLeadHours + hours).sortedDescending())
        } else it
    }

    suspend fun removeReminderLeadHour(hours: Int) = updateConfig {
        it.copy(reminderLeadHours = it.reminderLeadHours.filter { h -> h != hours })
    }

    suspend fun toggleDistractionApp(packageName: String) = updateConfig { cfg ->
        cfg.copy(distractionApps = cfg.distractionApps.map { app ->
            if (app.packageName == packageName) app.copy(isRestricted = !app.isRestricted) else app
        })
    }

    // ---- Logs / streak / balance ---------------------------------------------------------------

    val logs: Flow<List<SavingsLogEntity>> = logDao.observeAll()
    val todayLog: Flow<SavingsLogEntity?> = logDao.observeByDate(DateUtils.today())
    val totalSaved: Flow<Int> = logDao.observeTotalSaved()

    /** Current consecutive-day streak of successful days (SAVED or RECOVERY_USED). */
    val streak: Flow<Int> = logs.map { computeStreak(it) }

    /** Convenience combined flow for the Dashboard header (total + streak + today). */
    val dashboardTotals: Flow<Triple<Int, Int, SavingsLogEntity?>> =
        combine(totalSaved, streak, todayLog) { total, streakDays, today ->
            Triple(total, streakDays, today)
        }

    suspend fun getTodayLog(): SavingsLogEntity? = logDao.getByDate(DateUtils.today())

    /** Record a successful save for today (called after a confirmed M-Pesa payment). */
    suspend fun markSavedToday(savedAmount: Int, checkoutRequestId: String?) {
        val cfg = getConfig()
        logDao.upsert(
            SavingsLogEntity(
                date = DateUtils.today(),
                targetAmount = cfg.dailyAmount,
                savedAmount = savedAmount,
                status = SavingsStatus.SAVED,
                timestamp = System.currentTimeMillis(),
                checkoutRequestId = checkoutRequestId
            )
        )
    }

    /** Mark today's lock as lifted via a recovery code (no money saved). */
    suspend fun markRecoveryUsedToday() {
        val cfg = getConfig()
        logDao.upsert(
            SavingsLogEntity(
                date = DateUtils.today(),
                targetAmount = cfg.dailyAmount,
                savedAmount = 0,
                status = SavingsStatus.RECOVERY_USED,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /** Mark today missed (called by the lock trigger if the deadline passed unpaid). */
    suspend fun markMissedTodayIfUnresolved() {
        val existing = getTodayLog()
        if (existing == null || existing.status == SavingsStatus.PENDING) {
            val cfg = getConfig()
            logDao.upsert(
                SavingsLogEntity(
                    date = DateUtils.today(),
                    targetAmount = cfg.dailyAmount,
                    savedAmount = 0,
                    status = SavingsStatus.MISSED,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /** True if today has already been resolved (saved or recovery), so no lock is needed. */
    suspend fun isTodayResolved(): Boolean {
        val log = getTodayLog() ?: return false
        return log.status == SavingsStatus.SAVED || log.status == SavingsStatus.RECOVERY_USED
    }

    // ---- Recovery codes ------------------------------------------------------------------------

    val recoveryCodes: Flow<List<RecoveryCodeEntity>> = recoveryDao.observeAll()

    /**
     * Generate a fresh batch of [count] codes, replacing any existing ones. Returns the plaintext
     * codes to display ONCE — they are never retrievable again.
     */
    suspend fun regenerateRecoveryCodes(count: Int = 10): List<String> {
        val generated = recoveryCodeManager.generate(count)
        recoveryDao.deleteAll()
        recoveryDao.insertAll(generated.map { it.entity })
        return generated.map { it.plaintext }
    }

    /** Ensure at least one batch exists (called at startup so the user always has codes). */
    suspend fun ensureRecoveryCodesExist(count: Int = 10): List<String>? {
        return if (recoveryDao.count() == 0) regenerateRecoveryCodes(count) else null
    }

    /**
     * Try to redeem [input] against the stored (hashed) codes. On success, marks the code used and
     * records today's log as RECOVERY_USED. Returns true if a valid, unused code matched. OFFLINE.
     */
    suspend fun redeemRecoveryCode(input: String): Boolean {
        val unused = recoveryDao.getUnused()
        val match = unused.firstOrNull { recoveryCodeManager.verify(input, it) } ?: return false
        recoveryDao.markUsed(match.id, System.currentTimeMillis())
        markRecoveryUsedToday()
        return true
    }

    private fun computeStreak(logsNewestFirst: List<SavingsLogEntity>): Int {
        if (logsNewestFirst.isEmpty()) return 0
        val successByDate = logsNewestFirst
            .filter { it.status == SavingsStatus.SAVED || it.status == SavingsStatus.RECOVERY_USED }
            .map { it.date }
            .toHashSet()
        if (successByDate.isEmpty()) return 0

        var cursor = DateUtils.parse(DateUtils.today())
        // A still-open today (not yet saved) must not break yesterday's streak: skip today if unsaved.
        if (cursor.toString() !in successByDate) cursor = cursor.minusDays(1)

        var streak = 0
        while (cursor.toString() in successByDate) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
