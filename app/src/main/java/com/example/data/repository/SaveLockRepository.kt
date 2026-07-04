package com.example.data.repository

import com.example.data.local.dao.PlanPaymentDao
import com.example.data.local.dao.RecoveryCodeDao
import com.example.data.local.dao.SavingsConfigDao
import com.example.data.local.dao.SavingsLogDao
import com.example.data.local.dao.SavingsPlanDao
import com.example.data.local.entity.PlanPaymentEntity
import com.example.data.local.entity.RecoveryCodeEntity
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsLogEntity
import com.example.data.local.entity.SavingsPlanEntity
import com.example.data.local.entity.SavingsStatus
import com.example.domain.PlanLogic
import com.example.domain.RecoveryCodeManager
import com.example.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The single source of truth for the app's data. Exposes Kotlin [Flow]s that the ViewModels collect,
 * and suspend functions that write to Room. No Android UI or network concerns live here.
 */
class SaveLockRepository(
    private val configDao: SavingsConfigDao,
    private val logDao: SavingsLogDao,
    private val recoveryDao: RecoveryCodeDao,
    private val planDao: SavingsPlanDao,
    private val planPaymentDao: PlanPaymentDao,
    private val recoveryCodeManager: RecoveryCodeManager
) {

    // ---- Savings / Goal plans -------------------------------------------------------------------

    /** All currently-active plans (Savings + Goals), newest first. Drives the Home list + lock. */
    val activePlans: Flow<List<SavingsPlanEntity>> = planDao.observeActive()

    /** Every plan ever created (active or not) — used to name payments in history. */
    val allPlans: Flow<List<SavingsPlanEntity>> = planDao.observeAll()

    /** Every plan payment ever made — the lock logic and progress bars read this. */
    val allPayments: Flow<List<PlanPaymentEntity>> = planPaymentDao.observeAll()

    /** Grand total actually saved across all plans (real money only, excludes recovery unlocks). */
    val planTotalSaved: Flow<Int> =
        allPayments.map { list -> list.filter { !it.viaRecovery }.sumOf { it.amount } }

    suspend fun getActivePlans(): List<SavingsPlanEntity> = planDao.getActive()

    /** One-shot read of all payments (for immediate lock re-evaluation after a write). */
    suspend fun getAllPayments(): List<PlanPaymentEntity> = planPaymentDao.getAll()

    suspend fun getPlan(id: Long): SavingsPlanEntity? = planDao.getById(id)

    suspend fun createPlan(plan: SavingsPlanEntity): Long {
        val id = planDao.insert(plan)
        setLockStarted(false)
        return id
    }

    /** Edit an existing plan (keeps its id, createdAt anchor and payments). */
    suspend fun updatePlan(plan: SavingsPlanEntity) = planDao.update(plan)

    /**
     * Stop a plan (deactivate). We KEEP its payments so the History screen still shows what was
     * saved — the plan row stays (inactive) purely so history can still show its name.
     */
    suspend fun deletePlan(id: Long) {
        planDao.deactivate(id)
    }

    /**
     * Record a payment (or recovery unlock) toward [planId]. The period index is computed from the
     * plan's schedule so we know which period this satisfies. Safe no-op if the plan is gone.
     */
    suspend fun recordPlanPayment(
        planId: Long,
        amount: Int,
        checkoutRequestId: String?,
        viaRecovery: Boolean = false
    ) {
        val plan = planDao.getById(planId) ?: return
        val now = System.currentTimeMillis()
        planPaymentDao.insert(
            PlanPaymentEntity(
                planId = planId,
                amount = amount,
                periodIndex = PlanLogic.currentPeriodIndex(plan, now),
                timestamp = now,
                checkoutRequestId = checkoutRequestId,
                viaRecovery = viaRecovery
            )
        )
    }

    /**
     * Credit an externally-confirmed real payment (from the M-Pesa confirmation SMS or the C2B
     * webhook) toward whichever plans are currently locking. The [receipt] (M-Pesa code / TransID)
     * dedups it so the same payment is never counted twice — safe to call from both the SMS receiver
     * and the online reconcile poller for the same transaction.
     *
     * The amount is spread across due plans (oldest first), each getting up to its current-period
     * shortfall, until the money runs out. Returns true if it credited at least one plan (which will
     * lift the lock via the payments Flow). Fully local — no network.
     */
    suspend fun applyExternalPayment(receipt: String, amount: Int): Boolean {
        if (receipt.isBlank() || amount <= 0) return false
        if (planPaymentDao.countByReceipt(receipt) > 0) return false // already processed this code
        val now = System.currentTimeMillis()
        var remaining = amount
        var creditedAny = false
        for (plan in planDao.getActive()) {
            if (remaining <= 0) break
            val payments = planPaymentDao.getForPlan(plan.id)
            if (PlanLogic.isGoalCompleted(plan, payments, now)) continue
            val idx = PlanLogic.currentPeriodIndex(plan, now)
            val paid = payments.filter { it.periodIndex == idx }.sumOf { it.amount }
            val shortfall = PlanLogic.requiredAmount(plan) - paid
            if (shortfall <= 0) continue
            val credit = minOf(shortfall, remaining)
            recordPlanPayment(plan.id, credit, checkoutRequestId = receipt, viaRecovery = false)
            remaining -= credit
            creditedAny = true
        }
        // Mirror the STK-success path: crediting the plan payment(s) is what lifts the lock (via the
        // payments Flow). We deliberately do NOT touch the legacy daily log here, so streak/history
        // stay consistent regardless of whether a payment arrived via STK, SMS or C2B.
        return creditedAny
    }

    /** How much is still owed for [planId]'s CURRENT period (what "Save now" should charge). */
    suspend fun amountDueNow(planId: Long): Int {
        val plan = planDao.getById(planId) ?: return 0
        val now = System.currentTimeMillis()
        val paid = planPaymentDao.sumForPeriod(plan.id, PlanLogic.currentPeriodIndex(plan, now))
        val remaining = PlanLogic.requiredAmount(plan) - paid
        return if (remaining > 0) remaining else PlanLogic.requiredAmount(plan)
    }

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
    suspend fun setLockMode(mode: com.example.data.local.entity.LockMode) =
        updateConfig { it.copy(lockMode = mode) }
    suspend fun setMpesaNumber(number: String) = updateConfig { it.copy(mpesaNumber = number) }
    suspend fun setSavingEnabled(enabled: Boolean) = updateConfig { it.copy(savingEnabled = enabled) }
    suspend fun setLockStarted(started: Boolean) = updateConfig { it.copy(lockStarted = started) }
    suspend fun setTillName(name: String) = updateConfig { it.copy(tillName = name.trim()) }
    suspend fun setSmsAutoUnlock(enabled: Boolean) = updateConfig { it.copy(smsAutoUnlockEnabled = enabled) }

    suspend fun addReminderLeadHour(hours: Int) = updateConfig {
        if (hours in 1..23 && hours !in it.reminderLeadHours) {
            it.copy(reminderLeadHours = (it.reminderLeadHours + hours).sortedDescending())
        } else it
    }

    suspend fun removeReminderLeadHour(hours: Int) = updateConfig {
        it.copy(reminderLeadHours = it.reminderLeadHours.filter { h -> h != hours })
    }

    suspend fun toggleDistractionApp(packageName: String) = updateConfig { cfg ->
        val set = cfg.restrictedPackages.toMutableSet()
        if (!set.add(packageName)) set.remove(packageName)
        cfg.copy(restrictedPackages = set.toList())
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

    /** Ensure today has a row (PENDING) so it shows in history and the lock check has a target. */
    suspend fun ensureTodayPending() {
        if (getTodayLog() == null) {
            val cfg = getConfig()
            logDao.upsert(
                SavingsLogEntity(
                    date = DateUtils.today(),
                    targetAmount = cfg.dailyAmount,
                    savedAmount = 0,
                    status = SavingsStatus.PENDING,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /** Mark any past day still left PENDING as MISSED (daily rollover / boot). */
    suspend fun finalizeStalePendingDays() {
        logDao.markStalePendingAsMissed(DateUtils.today(), System.currentTimeMillis())
    }

    // ---- Recovery codes ------------------------------------------------------------------------

    val recoveryCodes: Flow<List<RecoveryCodeEntity>> = recoveryDao.observeAll()

    /**
     * Generate a fresh batch of [count] codes, replacing any existing ones. Returns the plaintext
     * codes to display ONCE — they are never retrievable again.
     */
    suspend fun regenerateRecoveryCodes(count: Int = 3): List<String> {
        // Hashing is CPU-heavy — run it OFF the main thread so the UI never freezes/crashes (ANR).
        val generated = withContext(Dispatchers.Default) { recoveryCodeManager.generate(count) }
        recoveryDao.deleteAll()
        recoveryDao.insertAll(generated.map { it.entity })
        return generated.map { it.plaintext }
    }

    /** Ensure at least one batch exists (called at startup so the user always has codes). */
    suspend fun ensureRecoveryCodesExist(count: Int = 3): List<String>? {
        return if (recoveryDao.count() == 0) regenerateRecoveryCodes(count) else null
    }

    /**
     * Try to redeem [input] against the stored (hashed) codes. On success, marks the code used and
     * satisfies the CURRENT period of every plan that is currently locking (a viaRecovery "payment"
     * for the shortfall), so the lock lifts everywhere. Returns true if a valid, unused code matched.
     * Fully OFFLINE — no network needed.
     */
    suspend fun redeemRecoveryCode(input: String): Boolean {
        val unused = recoveryDao.getUnused()
        // Verifying is CPU-heavy (PBKDF2) — do it off the main thread.
        val match = withContext(Dispatchers.Default) {
            unused.firstOrNull { recoveryCodeManager.verify(input, it) }
        } ?: return false
        recoveryDao.markUsed(match.id, System.currentTimeMillis())

        // Clear the lock: for each active plan that is due-and-unpaid this period, log the shortfall
        // as a recovery unlock (no real money) so its current period counts as satisfied.
        val now = System.currentTimeMillis()
        for (plan in planDao.getActive()) {
            val payments = planPaymentDao.getForPlan(plan.id)
            if (PlanLogic.isGoalCompleted(plan, payments, now)) continue
            val idx = PlanLogic.currentPeriodIndex(plan, now)
            val paid = payments.filter { it.periodIndex == idx }.sumOf { it.amount }
            val shortfall = PlanLogic.requiredAmount(plan) - paid
            if (shortfall > 0) {
                recordPlanPayment(plan.id, shortfall, checkoutRequestId = null, viaRecovery = true)
            }
        }
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
