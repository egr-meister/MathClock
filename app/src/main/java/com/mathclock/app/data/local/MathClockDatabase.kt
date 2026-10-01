package com.mathclock.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        CalculationEntity::class,
        PracticeSessionEntity::class,
        TimeQuestionEntity::class,
        ProgressTotalsEntity::class,
    ],
    version = MathClockDatabase.VERSION,
    exportSchema = true,
)
abstract class MathClockDatabase : RoomDatabase() {
    abstract fun calculationDao(): CalculationDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        const val VERSION = 1
        private const val NAME = "mathclock.db"

        fun build(context: Context): MathClockDatabase =
            Room.databaseBuilder(context.applicationContext, MathClockDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No destructive fallback: a missing migration must fail loudly in testing.
                .build()
    }
}

/**
 * Schema history lives in app/schemas (exported by Room). Version 1 is the first released schema,
 * so there are no migrations yet. Every future version bump must add an explicit Migration here
 * (or a Room AutoMigration) and commit the new exported schema JSON.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
