package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single recovery code, stored ONLY as a salted hash — never in plaintext. The plaintext is shown
 * to the user exactly once at generation time and then discarded. [maskedDisplay] is a non-secret
 * label (e.g. "••••-••••") so the Recovery Codes list can show used/unused state without leaking the code.
 */
@Entity(tableName = "recovery_code")
data class RecoveryCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codeHash: String,          // Base64 PBKDF2 hash of the canonical code
    val salt: String,              // Base64 per-code random salt
    val maskedDisplay: String,     // safe label for the list screen
    val used: Boolean = false,
    val usedAt: Long? = null,      // epoch millis when consumed
    val createdAt: Long
)
