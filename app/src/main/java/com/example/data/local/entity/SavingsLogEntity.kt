package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Outcome of one day. Stored as a String in the DB via a TypeConverter. */
enum class SavingsStatus {
    /** Day is open; not yet saved and deadline not necessarily passed. */
    PENDING,
    /** Target met via a successful M-Pesa payment. */
    SAVED,
    /** Deadline passed with no save. */
    MISSED,
    /** Lock was lifted with an offline recovery code instead of paying. */
    RECOVERY_USED
}

/**
 * One row per calendar day, keyed by the local date ("yyyy-MM-dd") so we can upsert the day's result.
 * Feeds the History screen, the streak, and the total-saved balance.
 */
@Entity(tableName = "savings_log")
data class SavingsLogEntity(
    @PrimaryKey val date: String,          // "yyyy-MM-dd" local date
    val targetAmount: Int,                 // KES target that day
    val savedAmount: Int,                  // KES actually saved that day
    val status: SavingsStatus,
    val timestamp: Long,                   // epoch millis of last update
    /** Daraja CheckoutRequestID for the payment that closed this day, if any. */
    val checkoutRequestId: String? = null
)
