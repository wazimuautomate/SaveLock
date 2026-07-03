package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single payment (or recovery unlock) toward a plan. [periodIndex] is which period of that plan
 * this payment satisfies, so we can tell whether the current period is paid.
 */
@Entity(
    tableName = "plan_payment",
    indices = [Index("planId")]
)
data class PlanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val amount: Int,
    val periodIndex: Long,
    val timestamp: Long,
    val checkoutRequestId: String? = null,
    /** True if this "payment" was actually a recovery-code unlock (no money). */
    val viaRecovery: Boolean = false
)
