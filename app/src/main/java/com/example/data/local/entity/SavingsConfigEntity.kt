package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's whole configuration. There is only ever ONE row (id == [SINGLETON_ID]), so reading it
 * is simple and writing is an upsert. Feeds the Settings and Dashboard screens.
 */
@Entity(tableName = "savings_config")
data class SavingsConfigEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** Daily target in whole KES. */
    val dailyAmount: Int = 500,
    /** Lock trigger time, 24-hour "HH:mm" (matches the Settings UI string). */
    val lockTime: String = "20:00",
    /** Hours-before-lock to send a reminder, e.g. [2, 1]. */
    val reminderLeadHours: List<Int> = listOf(2, 1),
    /** M-Pesa number in 2547XXXXXXXX form. Empty until the user sets it. */
    val mpesaNumber: String = "",
    /** Master switch: when false, no locks/reminders fire. */
    val savingEnabled: Boolean = true,
    /** How strict the lock is once the deadline passes. Defaults to the gentler chosen-apps mode. */
    val lockMode: LockMode = LockMode.CHOSEN_APPS,
    /** Candidate distraction apps with their restricted flags. */
    val distractionApps: List<DistractionAppRecord> = defaultDistractionApps()
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
