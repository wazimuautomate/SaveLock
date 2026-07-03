package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Savings or Goal. Many can run in parallel. Period math is anchored on [createdAt].
 * For SAVINGS, the goal fields are unused. For GOAL, [goalTotal] is the target and
 * [goalDurationDays] is the total time allowed to reach it.
 */
@Entity(tableName = "savings_plan")
data class SavingsPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PlanType,
    val name: String,
    val amountType: AmountType,
    /** FIXED: the exact per-period amount. FLEXIBLE: the per-period minimum. */
    val amount: Int,
    val period: PeriodType,
    /** "N" for EVERY_N_DAYS / EVERY_N_HOURS; ignored for the others. */
    val periodValue: Int = 1,
    // Goal-only:
    val goalTotal: Int = 0,
    val goalDurationDays: Int = 0,
    val createdAt: Long,
    val active: Boolean = true
)
