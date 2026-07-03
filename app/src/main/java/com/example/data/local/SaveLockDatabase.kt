package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.dao.RecoveryCodeDao
import com.example.data.local.dao.SavingsConfigDao
import com.example.data.local.dao.SavingsLogDao
import com.example.data.local.entity.RecoveryCodeEntity
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsLogEntity

@Database(
    entities = [
        SavingsConfigEntity::class,
        SavingsLogEntity::class,
        RecoveryCodeEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SaveLockDatabase : RoomDatabase() {

    abstract fun configDao(): SavingsConfigDao
    abstract fun logDao(): SavingsLogDao
    abstract fun recoveryDao(): RecoveryCodeDao

    companion object {
        @Volatile
        private var INSTANCE: SaveLockDatabase? = null

        fun get(context: Context): SaveLockDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaveLockDatabase::class.java,
                    "savelock.db"
                )
                    // Personal app, single user: a destructive fallback is acceptable if we ever
                    // bump the schema version without writing a migration.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
