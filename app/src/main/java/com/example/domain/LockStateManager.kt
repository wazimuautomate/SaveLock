package com.example.domain

import com.example.data.local.entity.LockMode
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsStatus
import com.example.data.repository.SaveLockRepository
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single brain that answers "should the lock be active right now, and which packages does it
 * block?". Keeps a live in-memory snapshot of config + today's status (updated from repository
 * Flows) so the AccessibilityService can decide instantly on every window change without a DB hit.
 */
class LockStateManager(
    private val repository: SaveLockRepository,
    scope: CoroutineScope
) {
    @Volatile private var config: SavingsConfigEntity = SavingsConfigEntity()
    @Volatile private var todayResolved: Boolean = false

    private val _lockActive = MutableStateFlow(false)
    /** Observable lock-active flag (drives the foreground service notification text). */
    val lockActive: StateFlow<Boolean> = _lockActive.asStateFlow()

    init {
        scope.launch {
            repository.config.collect {
                config = it
                recompute()
            }
        }
        scope.launch {
            repository.todayLog.collect { today ->
                todayResolved = today?.let {
                    it.status == SavingsStatus.SAVED || it.status == SavingsStatus.RECOVERY_USED
                } ?: false
                recompute()
            }
        }
    }

    /** Recompute the cached flag; call periodically (e.g. from the service ticker) too. */
    fun recompute(): Boolean {
        val active = isLockActiveNow()
        _lockActive.value = active
        return active
    }

    /**
     * Lock is active when: saving is enabled, we're at/after the lock time, and today isn't resolved.
     */
    fun isLockActiveNow(): Boolean {
        val cfg = config
        if (!cfg.savingEnabled) return false
        if (todayResolved) return false
        return DateUtils.isPastLockTime(cfg.lockTime)
    }

    fun currentConfig(): SavingsConfigEntity = config

    fun lockMode(): LockMode = config.lockMode

    /**
     * Given the app currently in the foreground, decide whether to block it. Returns true only when
     * the lock is active AND [foregroundPackage] is NOT an always-allowed package AND (in chosen-apps
     * mode) it's on the user's restricted list. Emergency/allowed packages are decided by the caller
     * (AccessibilityService) which owns the hard exclusion list.
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
