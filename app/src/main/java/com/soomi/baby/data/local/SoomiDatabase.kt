package com.soomi.baby.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.soomi.baby.data.local.dao.LearningDao
import com.soomi.baby.data.local.dao.SessionDao
import com.soomi.baby.data.local.dao.TelemetryDao
import com.soomi.baby.data.local.entity.*

/**
 * Room database for SOOMI
 * 
 * Stores all local data:
 * - Sessions and events
 * - Learning effectiveness data
 * - Telemetry queue (if opted in)
 * 
 * PRIVACY: Never stores raw audio data.
 */
@Database(
    entities = [
        SessionEntity::class,
        ScoreSampleEntity::class,
        UnrestEventEntity::class,
        FsmEventEntity::class,
        MorningFeedbackEntity::class,
        InterventionEffectivenessEntity::class,
        TelemetryEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SoomiDatabase : RoomDatabase() {
    
    abstract fun sessionDao(): SessionDao
    abstract fun learningDao(): LearningDao
    abstract fun telemetryDao(): TelemetryDao
    
    companion object {
        private const val DATABASE_NAME = "soomi_database"
        
        @Volatile
        private var INSTANCE: SoomiDatabase? = null
        
        fun getInstance(context: Context): SoomiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): SoomiDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SoomiDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()  // For beta - proper migrations in prod
                .build()
        }
    }
}
