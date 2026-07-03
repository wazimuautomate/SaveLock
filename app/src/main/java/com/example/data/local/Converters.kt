package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entity.LockMode
import com.example.data.local.entity.SavingsStatus

/**
 * Room TypeConverters for the non-primitive columns. Int lists are CSV; String lists (package names)
 * are newline-separated; enums are stored by name.
 */
class Converters {

    @TypeConverter
    fun fromIntList(list: List<Int>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    fun toIntList(data: String?): List<Int> =
        if (data.isNullOrBlank()) emptyList()
        else data.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun fromStringList(list: List<String>?): String = list?.joinToString("\n") ?: ""

    @TypeConverter
    fun toStringList(data: String?): List<String> =
        if (data.isNullOrBlank()) emptyList() else data.split("\n").filter { it.isNotBlank() }

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
}
