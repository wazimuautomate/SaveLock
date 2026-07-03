package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.PlanPaymentDao
import com.example.data.local.dao.RecoveryCodeDao
import com.example.data.local.dao.SavingsConfigDao
import com.example.data.local.dao.SavingsLogDao
import com.example.data.local.dao.SavingsPlanDao
import com.example.data.local.entity.PlanPaymentEntity
import com.example.data.local.entity.RecoveryCodeEntity
import com.example.data.local.entity.SavingsConfigEntity
import com.example.data.local.entity.SavingsLogEntity
import com.example.data.local.entity.SavingsPlanEntity

@Database(
    entities = [
        SavingsConfigEntity::class,
        SavingsLogEntity::class,
        RecoveryCodeEntity::class,
        SavingsPlanEntity::class,
        PlanPaymentEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SaveLockDatabase : RoomDatabase() {

    abstract fun configDao(): SavingsConfigDao
    abstract fun logDao(): SavingsLogDao
    abstract fun recoveryDao(): RecoveryCodeDao
    abstract fun planDao(): SavingsPlanDao
    abstract fun planPaymentDao(): PlanPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: SaveLockDatabase? = null

        /**
         * v3 → v4: adds the two SMS-auto-unlock columns to savings_config. Written as a real
         * migration (not a destructive wipe) so the user keeps their plans, payments and setup.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE savings_config ADD COLUMN tillName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE savings_config ADD COLUMN smsAutoUnlockEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): SaveLockDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaveLockDatabase::class.java,
                    "savelock.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    // Personal app, single user: destructive fallback stays as a backstop only if we
                    // ever bump the version WITHOUT providing a migration above.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
