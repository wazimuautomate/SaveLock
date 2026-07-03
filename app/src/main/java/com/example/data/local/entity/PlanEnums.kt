package com.example.data.local.entity

/** A plan is either an ongoing Savings or a target-based Goal. */
enum class PlanType { SAVINGS, GOAL }

/** FIXED = pay exactly [SavingsPlanEntity.amount]. FLEXIBLE = pay any amount >= that minimum. */
enum class AmountType { FIXED, FLEXIBLE }

/**
 * How often a save is due. DAILY / EVERY_2_DAYS / WEEKLY / MONTHLY are fixed. EVERY_N_DAYS and
 * EVERY_N_HOURS use [SavingsPlanEntity.periodValue] as the "N" the user chose.
 */
enum class PeriodType { DAILY, EVERY_2_DAYS, WEEKLY, MONTHLY, EVERY_N_DAYS, EVERY_N_HOURS }
