package com.renotify.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StoredNotification::class, Rule::class], version = 7, exportSchema = false)
abstract class ReNotifyDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao

    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var instance: ReNotifyDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN scheduledFor INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN customIcon TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT,
                        `appLabel` TEXT,
                        `mode` TEXT NOT NULL,
                        `keywords` TEXT NOT NULL,
                        `startMinute` INTEGER,
                        `endMinute` INTEGER,
                        `enabled` INTEGER NOT NULL,
                        `matchCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN scope TEXT NOT NULL DEFAULT 'both'"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN delivery INTEGER")
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN delivery INTEGER NOT NULL DEFAULT 7"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN action TEXT NOT NULL DEFAULT 'hide'"
                )
            }
        }

        fun get(context: Context): ReNotifyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReNotifyDatabase::class.java,
                    "renotify.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7,
                ).build().also { instance = it }
            }
    }
}
