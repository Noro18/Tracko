package com.example.tracko.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.tracko.data.local.dao.AppStateDao
import com.example.tracko.data.local.dao.HabitDao
import com.example.tracko.data.local.dao.HabitLogDao
import com.example.tracko.data.local.entities.AppStateEntity
import com.example.tracko.data.local.entities.HabitEntity
import com.example.tracko.data.local.entities.HabitLogEntity

/**
 * The app's SQLite database (data model §2) — owns the single connection
 * every DAO and repository shares. Version 1; schema JSON is exported
 * (`exportSchema = true` + the `room.schemaLocation` KSP arg) and committed
 * to `app/schemas/` so future migrations verify against it.
 *
 * Never `fallbackToDestructiveMigration()`: history must survive upgrades.
 */
@TypeConverters(Converter::class)
@Database(
    entities = [HabitEntity::class, HabitLogEntity::class, AppStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackoDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun habitLogDao(): HabitLogDao

    abstract fun appStateDao(): AppStateDao

    companion object {
        @Volatile private var INSTANCE: TrackoDatabase? = null

        fun getInstance(context: Context): TrackoDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TrackoDatabase::class.java,
                    "tracko_db",
                )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
