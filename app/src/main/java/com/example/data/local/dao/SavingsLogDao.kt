package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SavingsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsLogDao {

    @Query("SELECT * FROM savings_log ORDER BY date DESC")
    fun observeAll(): Flow<List<SavingsLogEntity>>

    @Query("SELECT * FROM savings_log WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<SavingsLogEntity?>

    @Query("SELECT * FROM savings_log WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): SavingsLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SavingsLogEntity)

    /** Running total of everything actually saved, for the Dashboard balance. */
    @Query("SELECT COALESCE(SUM(savedAmount), 0) FROM savings_log")
    fun observeTotalSaved(): Flow<Int>

    /** All logs newest-first, as a one-shot read (used for streak math). */
    @Query("SELECT * FROM savings_log ORDER BY date DESC")
    suspend fun getAllOrdered(): List<SavingsLogEntity>

    /** Finalize any past day still left PENDING as MISSED (called on the daily rollover / boot). */
    @Query("UPDATE savings_log SET status = 'MISSED', timestamp = :now WHERE status = 'PENDING' AND date < :today")
    suspend fun markStalePendingAsMissed(today: String, now: Long): Int
}
