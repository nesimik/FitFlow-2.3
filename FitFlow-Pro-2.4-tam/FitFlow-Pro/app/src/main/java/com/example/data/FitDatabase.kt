package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        RoutineItemEntity::class,
        WorkoutEntity::class,
        WorkoutSetEntity::class,
        PrEntity::class,
        BodyMetricEntity::class,
        NoteEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FitDatabase : RoomDatabase() {

    abstract fun dao(): FitDao

    companion object {
        const val NAME = "fitflow_pro.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine_items ADD COLUMN customName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN supersetGroup INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN isDeload INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: FitDatabase? = null

        fun get(context: Context): FitDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                FitDatabase::class.java,
                NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // fallbackToDestructiveMigration() BİLEREK kaldırıldı: eksik bir migration
                // tüm antrenman geçmişini sessizce silmek yerine görünür bir hata vermeli.
                // Şema değişirse version artırılıp MIGRATION_x_y yazılmalıdır.
                .build()
                .also { INSTANCE = it }
        }
    }
}

