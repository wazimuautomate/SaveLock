package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SavingsConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsConfigDao {

    @Query("SELECT * FROM savings_config WHERE id = 0 LIMIT 1")
    fun observe(): Flow<SavingsConfigEntity?>

    @Query("SELECT * FROM savings_config WHERE id = 0 LIMIT 1")
    suspend fun get(): SavingsConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: SavingsConfigEntity)
}
