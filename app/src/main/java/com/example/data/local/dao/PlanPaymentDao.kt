package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.local.entity.PlanPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanPaymentDao {

    @Query("SELECT * FROM plan_payment ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PlanPaymentEntity>>

    @Query("SELECT * FROM plan_payment WHERE planId = :planId ORDER BY timestamp DESC")
    suspend fun getForPlan(planId: Long): List<PlanPaymentEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM plan_payment WHERE planId = :planId")
    suspend fun totalForPlan(planId: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM plan_payment WHERE planId = :planId AND periodIndex = :periodIndex")
    suspend fun sumForPeriod(planId: Long, periodIndex: Long): Int

    @Insert
    suspend fun insert(payment: PlanPaymentEntity)

    @Query("DELETE FROM plan_payment WHERE planId = :planId")
    suspend fun deleteForPlan(planId: Long)
}
