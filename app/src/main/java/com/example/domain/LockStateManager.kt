package com.example.domain

import com.example.data.local.entity.LockMode
import com.example.data.local.entity.PlanPaymentEntity
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsPlanEntity
import com.example.data.repository.SaveLockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single brain that answers "should the lock be active right now, and which packages does it
 * block?". Keeps a live in-memory snapshot of config + active plans + payments (updated from
 * repository Flows) so the AccessibilityService can decide instantly on every window change.
 *
 * Lock rule (owner's choice): the phone locks whenever ANY active plan is due-and-unpaid for its
 * current period ("locked until you pay each period"). Savings and Goals run in parallel and share
 * one lock — the lock lifts only when every currently-due plan is satisfied (paid or recovery code).
 */
class LockStateManager(
    private val repository: SaveLockRepository,
    scope: CoroutineScope
) {
    @Volatile private var config: SavingsConfigEntity = SavingsConfigEntity()
    @Volatile private var plans: List<SavingsPlanEntity> = emptyList()
    @Volatile private var payments: List<PlanPaymentEntity> = emptyList()

    private val _lockActive = MutableStateFlow(false)
    /** Observable lock-active flag (drives the foreground service notification text). */
    val lockActive: StateFlow<Boolean> = _lockActive.asStateFlow()

    init {
        scope.launch {
            repository.config.collect { config = it; recompute() }
        }
        scope.launch {
            repository.activePlans.collect { plans = it; recompute() }
        }
        scope.launch {
            repository.allPayments.collect { payments = it; recompute() }
        }
        // A period can become due while nothing else changes (e.g. a new day begins). Re-evaluate
        // every minute so the lock arms itself on time without needing a user action.
        scope.launch {
            while (true) {
                recompute()
                delay(60_000)
            }
        }
    }

    /** Recompute the cached flag; call after external changes (e.g. from the service ticker) too. */
    fun recompute(): Boolean {
        val active = isLockActiveNow()
        _lockActive.value = active
        return active
    }

    /**
     * Re-read config/plans/payments straight from the DB, then recompute. Use this right after a write
     * that should lift the lock (payment, recovery code, SMS/C2B credit) — [recompute] alone can run on
     * a still-stale cached snapshot because the Room Flows update asynchronously, which previously left
     * the lock overlay up even after "Unlocked". Returns the fresh lock-active flag.
     */
    suspend fun refreshNow(): Boolean {
        config = repository.getConfig()
        plans = repository.getActivePlans()
        payments = repository.getAllPayments()
        return recompute()
    }

    /** Lock is active when saving is enabled AND at least one active plan is due-and-unpaid now. */
    fun isLockActiveNow(): Boolean {
        if (!config.savingEnabled) return false
        val now = System.currentTimeMillis()
        return plans.any { PlanLogic.isLockingNow(it, payments, now) }
    }

    fun currentConfig(): SavingsConfigEntity = config

    fun lockMode(): LockMode = config.lockMode

    /**
     * Given the app currently in the foreground, decide whether to block it. Returns true only when
     * the lock is active AND (in chosen-apps mode) the package is on the user's restricted list.
     * Emergency/allowed packages are decided by the caller (AccessibilityService).
     */
    fun shouldBlockDistractionPackage(foregroundPackage: String): Boolean {
        if (!isLockActiveNow()) return false
        return when (config.lockMode) {
            LockMode.FULL_LOCKDOWN -> true // caller already filtered out allowed/emergency packages
            LockMode.CHOSEN_APPS -> foregroundPackage in config.restrictedPackages
        }
    }

    /** The user's restricted-app packages (chosen-apps mode). */
    fun restrictedPackages(): Set<String> = config.restrictedPackages.toSet()
}
