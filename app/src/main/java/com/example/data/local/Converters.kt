package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entity.DistractionAppRecord
import com.example.data.local.entity.LockMode
import com.example.data.local.entity.SavingsStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Room TypeConverters for the non-primitive columns. Lists of Ints are stored as CSV; the
 * distraction-app list is stored as JSON (Moshi); the status enum is stored by name.
 */
class Converters {

    @TypeConverter
    fun fromIntList(list: List<Int>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    fun toIntList(data: String?): List<Int> =
        if (data.isNullOrBlank()) emptyList()
        else data.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun fromStatus(status: SavingsStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): SavingsStatus =
        runCatching { SavingsStatus.valueOf(value) }.getOrDefault(SavingsStatus.PENDING)

    @TypeConverter
    fun fromLockMode(mode: LockMode): String = mode.name

    @TypeConverter
    fun toLockMode(value: String): LockMode =
        runCatching { LockMode.valueOf(value) }.getOrDefault(LockMode.CHOSEN_APPS)

    @TypeConverter
    fun fromDistractionApps(list: List<DistractionAppRecord>?): String =
        appsAdapter.toJson(list ?: emptyList())

    @TypeConverter
    fun toDistractionApps(json: String?): List<DistractionAppRecord> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { appsAdapter.fromJson(json) }.getOrNull() ?: emptyList()

    companion object {
        private val moshi: Moshi = Moshi.Builder().build()
        private val appsType =
            Types.newParameterizedType(List::class.java, DistractionAppRecord::class.java)
        private val appsAdapter = moshi.adapter<List<DistractionAppRecord>>(appsType)
    }
}
