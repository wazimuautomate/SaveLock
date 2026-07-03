package com.example.domain

import com.example.data.local.entity.PeriodType
import com.example.data.local.entity.PlanPaymentEntity
import com.example.data.local.entity.PlanType
import com.example.data.local.entity.SavingsPlanEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Pure math for a plan: which period we're in, whether it's paid, whether the plan is locking, and
 * progress. No Android/DB here — callers pass the plan and its payments.
 *
 * Lock rule (owner's choice): a plan locks the phone whenever the CURRENT period isn't yet paid
 * ("locked until you pay"). A payment is enough when the sum for the current period reaches the
 * required amount (the fixed amount, or the flexible minimum).
 */
object PlanLogic {

    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun toDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    private fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    fun periodLengthMillis(plan: SavingsPlanEntity): Long = when (plan.period) {
        PeriodType.DAILY -> DAY
        PeriodType.EVERY_2_DAYS -> 2 * DAY
        PeriodType.WEEKLY -> 7 * DAY
        PeriodType.EVERY_N_DAYS -> max(1, plan.periodValue) * DAY
        PeriodType.EVERY_N_HOURS -> max(1, plan.periodValue) * HOUR
        PeriodType.MONTHLY -> 30 * DAY // approximate; MONTHLY uses calendar math below for indexing
    }

    /** Which period we're in now (0-based, anchored on createdAt). */
    fun currentPeriodIndex(plan: SavingsPlanEntity, now: Long): Long {
        if (now <= plan.createdAt) return 0
        return if (plan.period == PeriodType.MONTHLY) {
            ChronoUnit.MONTHS.between(toDateTime(plan.createdAt), toDateTime(now))
        } else {
            (now - plan.createdAt) / periodLengthMillis(plan)
        }
    }

    fun periodStartMillis(plan: SavingsPlanEntity, index: Long): Long =
        if (plan.period == PeriodType.MONTHLY) toMillis(toDateTime(plan.createdAt).plusMonths(index))
        else plan.createdAt + index * periodLengthMillis(plan)

    /** When the next period begins (used to schedule the next lock check). */
    fun nextBoundaryMillis(plan: SavingsPlanEntity, now: Long): Long =
        periodStartMillis(plan, currentPeriodIndex(plan, now) + 1)

    /** Amount required to satisfy one period (fixed amount, or flexible minimum). */
    fun requiredAmount(plan: SavingsPlanEntity): Int = plan.amount

    fun savedTotal(payments: List<PlanPaymentEntity>): Int = payments.sumOf { it.amount }

    fun currentPeriodPaid(plan: SavingsPlanEntity, payments: List<PlanPaymentEntity>, now: Long): Int {
        val idx = currentPeriodIndex(plan, now)
        return payments.filter { it.planId == plan.id && it.periodIndex == idx }.sumOf { it.amount }
    }

    fun isGoalCompleted(plan: SavingsPlanEntity, payments: List<PlanPaymentEntity>, now: Long): Boolean {
        if (plan.type != PlanType.GOAL) return false
        if (plan.goalTotal > 0 && savedTotal(payments.filter { it.planId == plan.id }) >= plan.goalTotal) return true
        if (plan.goalDurationDays > 0 && now >= plan.createdAt + plan.goalDurationDays * DAY) return true
        return false
    }

    /** True if this plan should be locking the phone right now. */
    fun isLockingNow(plan: SavingsPlanEntity, payments: List<PlanPaymentEntity>, now: Long): Boolean {
        if (!plan.active) return false
        if (isGoalCompleted(plan, payments, now)) return false
        return currentPeriodPaid(plan, payments, now) < requiredAmount(plan)
    }

    /** Progress bar value 0..1. Goals: total saved / target. Savings: this period paid / required. */
    fun progressFraction(plan: SavingsPlanEntity, payments: List<PlanPaymentEntity>, now: Long): Float {
        val mine = payments.filter { it.planId == plan.id }
        return when (plan.type) {
            PlanType.GOAL ->
                if (plan.goalTotal <= 0) 0f
                else (savedTotal(mine).toFloat() / plan.goalTotal).coerceIn(0f, 1f)
            PlanType.SAVINGS ->
                if (requiredAmount(plan) <= 0) 0f
                else (currentPeriodPaid(plan, mine, now).toFloat() / requiredAmount(plan)).coerceIn(0f, 1f)
        }
    }
}
