package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.local.entity.RecoveryCodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryCodeDao {

    @Query("SELECT * FROM recovery_code ORDER BY id ASC")
    fun observeAll(): Flow<List<RecoveryCodeEntity>>

    @Query("SELECT * FROM recovery_code WHERE used = 0")
    suspend fun getUnused(): List<RecoveryCodeEntity>

    @Insert
    suspend fun insertAll(codes: List<RecoveryCodeEntity>)

    @Query("UPDATE recovery_code SET used = 1, usedAt = :usedAt WHERE id = :id")
    suspend fun markUsed(id: Long, usedAt: Long)

    /** Wipe all codes (used when the user regenerates a fresh set). */
    @Query("DELETE FROM recovery_code")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM recovery_code")
    suspend fun count(): Int
}
