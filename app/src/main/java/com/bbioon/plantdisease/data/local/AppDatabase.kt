package com.bbioon.plantdisease.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bbioon.plantdisease.data.model.ScanRecord

@Database(entities = [ScanRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scans ADD COLUMN scientificName TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN scientificDiseaseName TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN pathogenType TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN severity TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN diseaseStage TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN spreadRisk TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN symptoms TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN cause TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN prevention TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN favorableConditions TEXT")
                db.execSQL("ALTER TABLE scans ADD COLUMN notes TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plant_disease.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
