package com.soomi.baby.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SOOMI v3.0 Database Migrations
 * 
 * Migration von v2.7 auf v3.0:
 * - Neue Tabelle: intervention_events
 * - Neue Tabelle: sound_profile_scores
 */
object DatabaseMigrations {
    
    /**
     * Migration 3 → 4: v3.0 Learning Tables
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            
            // Tabelle: intervention_events
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS intervention_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    zStart REAL NOT NULL,
                    zEnd REAL NOT NULL,
                    zPeak REAL NOT NULL,
                    deltaZ REAL NOT NULL,
                    durationSec INTEGER NOT NULL,
                    effectivenessScore REAL NOT NULL,
                    soundType TEXT NOT NULL,
                    levelUsed TEXT NOT NULL,
                    baselineMode TEXT NOT NULL,
                    contextTag TEXT NOT NULL,
                    hourOfDay INTEGER NOT NULL,
                    eventIndexInSession INTEGER NOT NULL,
                    wasExploration INTEGER NOT NULL DEFAULT 0,
                    wasEffective INTEGER NOT NULL DEFAULT 0,
                    wasHighlyEffective INTEGER NOT NULL DEFAULT 0,
                    triggerType TEXT NOT NULL DEFAULT 'AUTO',
                    dZdtAtTrigger REAL,
                    FOREIGN KEY (sessionId) REFERENCES sessions(id) ON DELETE CASCADE
                )
            """)
            
            // Indices für intervention_events
            database.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_events_sessionId ON intervention_events(sessionId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_events_timestamp ON intervention_events(timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_events_soundType ON intervention_events(soundType)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_intervention_events_contextTag ON intervention_events(contextTag)")
            
            // Tabelle: sound_profile_scores
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS sound_profile_scores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    soundType TEXT NOT NULL,
                    startLevel TEXT NOT NULL,
                    baselineMode TEXT NOT NULL,
                    contextTag TEXT NOT NULL,
                    effectivenessScore REAL NOT NULL DEFAULT 0,
                    usageCount INTEGER NOT NULL DEFAULT 0,
                    successCount INTEGER NOT NULL DEFAULT 0,
                    highSuccessCount INTEGER NOT NULL DEFAULT 0,
                    avgDeltaZ REAL NOT NULL DEFAULT 0,
                    avgDurationSec REAL NOT NULL DEFAULT 0,
                    avgTimeToCalm REAL NOT NULL DEFAULT 0,
                    lastUpdated INTEGER NOT NULL,
                    lastUsed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """)
            
            // Indices für sound_profile_scores
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_sound_profile_scores_composite 
                ON sound_profile_scores(soundType, startLevel, baselineMode, contextTag)
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sound_profile_scores_effectivenessScore ON sound_profile_scores(effectivenessScore)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sound_profile_scores_contextTag ON sound_profile_scores(contextTag)")
        }
    }
    
    /**
     * Liste aller Migrations
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_3_4
    )
}

/**
 * Beispiel für SoomiDatabase Update:
 * 
 * @Database(
 *     entities = [
 *         SessionEntity::class,
 *         ScoreSampleEntity::class,
 *         UnrestEventEntity::class,
 *         FsmEventEntity::class,
 *         MorningFeedbackEntity::class,
 *         InterventionEffectivenessEntity::class,
 *         // v3.0:
 *         InterventionEventEntity::class,
 *         SoundProfileScoreEntity::class
 *     ],
 *     version = 4,  // Erhöht von 3
 *     exportSchema = true
 * )
 * abstract class SoomiDatabase : RoomDatabase() {
 *     abstract fun sessionDao(): SessionDao
 *     abstract fun learningDao(): LearningDao
 *     abstract fun learningDaoV3(): LearningDaoV3  // Neu
 *     
 *     companion object {
 *         fun build(context: Context): SoomiDatabase {
 *             return Room.databaseBuilder(
 *                 context,
 *                 SoomiDatabase::class.java,
 *                 "soomi_database"
 *             )
 *             .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
 *             .build()
 *         }
 *     }
 * }
 */
