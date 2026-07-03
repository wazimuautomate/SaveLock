package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SavingsPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsPlanDao {

    @Query("SELECT * FROM savings_plan WHERE active = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<SavingsPlanEntity>>

    @Query("SELECT * FROM savings_plan ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingsPlanEntity>>

    @Query("SELECT * FROM savings_plan WHERE active = 1")
    suspend fun getActive(): List<SavingsPlanEntity>

    @Query("SELECT * FROM savings_plan WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavingsPlanEntity?

    @Insert
    suspend fun insert(plan: SavingsPlanEntity): Long

    @Update
    suspend fun update(plan: SavingsPlanEntity)

    @Query("UPDATE savings_plan SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
