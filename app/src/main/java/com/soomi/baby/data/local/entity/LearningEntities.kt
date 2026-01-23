package com.soomi.baby.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SOOMI v3.0 - Intervention Event Entity
 * 
 * Speichert jedes einzelne Interventions-Event mit allen relevanten Daten
 * für das Learning System. Dies ist die Basis für deltaZ-basiertes Lernen.
 */
@Entity(
    tableName = "intervention_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp"),
        Index("soundType"),
        Index("contextTag")
    ]
)
data class InterventionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Session-Referenz
    val sessionId: Long,
    
    // Zeitstempel
    val timestamp: Long = System.currentTimeMillis(),
    
    // Z-Werte für deltaZ Berechnung
    val zStart: Float,              // Z-Wert bei Interventionsstart
    val zEnd: Float,                // Z-Wert bei Interventionsende
    val zPeak: Float,               // Höchster Z-Wert während Intervention
    
    // Berechnete Metriken
    val deltaZ: Float,              // zStart - zEnd (positiv = erfolgreich)
    val durationSec: Int,           // Dauer der Intervention in Sekunden
    val effectivenessScore: Float,  // deltaZ / durationSec (normalisiert)
    
    // Verwendete Einstellungen
    val soundType: String,          // BROWN_NOISE, WHITE_NOISE, etc.
    val levelUsed: String,          // LEVEL_1, LEVEL_2, LEVEL_3
    val baselineMode: String,       // OFF, GENTLE, MEDIUM
    
    // Kontext
    val contextTag: String,         // NIGHT, EARLY_MORNING, POST_FEED, etc.
    val hourOfDay: Int,             // 0-23, für zeitbasierte Analyse
    val eventIndexInSession: Int,   // Wievieltes Event in dieser Session (1, 2, 3...)
    
    // Learning-Flags
    val wasExploration: Boolean = false,    // War dies ein Explorations-Versuch?
    val wasEffective: Boolean = false,      // deltaZ > 15?
    val wasHighlyEffective: Boolean = false, // deltaZ > 30 in < 60s?
    
    // Trigger-Info
    val triggerType: String = "AUTO",       // AUTO, MANUAL, PREDICTIVE
    val dZdtAtTrigger: Float? = null        // Gradient bei Trigger (für PREDICTIVE)
)

/**
 * SOOMI v3.0 - Sound Profile Score Entity
 * 
 * Speichert die Effektivitätsbewertung für jede Kombination aus
 * Sound-Typ, Level, Baseline-Mode und Kontext.
 * 
 * Dies ermöglicht kontextabhängige Profilauswahl:
 * "Nachts funktioniert Brown Noise mit Level 2 am besten"
 */
@Entity(
    tableName = "sound_profile_scores",
    indices = [
        Index(value = ["soundType", "startLevel", "baselineMode", "contextTag"], unique = true),
        Index("effectivenessScore"),
        Index("contextTag")
    ]
)
data class SoundProfileScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Profil-Identifikation (Composite Key)
    val soundType: String,          // BROWN_NOISE, WHITE_NOISE, etc.
    val startLevel: String,         // LEVEL_1, LEVEL_2, LEVEL_3
    val baselineMode: String,       // OFF, GENTLE, MEDIUM
    val contextTag: String,         // NIGHT, EARLY_MORNING, etc.
    
    // Aggregierte Metriken
    val effectivenessScore: Float = 0f,     // Gewichteter Durchschnitt (0-100)
    val usageCount: Int = 0,                // Wie oft verwendet
    val successCount: Int = 0,              // Wie oft erfolgreich (deltaZ > 15)
    val highSuccessCount: Int = 0,          // Wie oft sehr erfolgreich (deltaZ > 30)
    
    // Durchschnittswerte
    val avgDeltaZ: Float = 0f,              // Durchschnittliche Z-Reduktion
    val avgDurationSec: Float = 0f,         // Durchschnittliche Dauer
    val avgTimeToCalm: Float = 0f,          // Zeit bis Z < 25
    
    // Zeitstempel
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Context Tags für zeitliche und situative Klassifizierung
 */
object ContextTags {
    const val NIGHT = "NIGHT"                   // 22:00 - 06:00
    const val EARLY_MORNING = "EARLY_MORNING"   // 04:00 - 07:00
    const val EVENING = "EVENING"               // 18:00 - 22:00
    const val DAYTIME = "DAYTIME"               // 07:00 - 18:00
    const val POST_FEED = "POST_FEED"           // 30 Min nach Fütterung (manuell)
    const val HIGH_ACTIVITY = "HIGH_ACTIVITY"   // 3+ Events in 2h
    const val SETTLING = "SETTLING"             // Erste 30 Min der Session
    const val DEEP_NIGHT = "DEEP_NIGHT"         // 00:00 - 04:00
    
    /**
     * Bestimmt den primären Context-Tag basierend auf Uhrzeit
     */
    fun fromHourOfDay(hour: Int): String {
        return when (hour) {
            in 0..3 -> DEEP_NIGHT
            in 4..6 -> EARLY_MORNING
            in 7..17 -> DAYTIME
            in 18..21 -> EVENING
            else -> NIGHT  // 22, 23
        }
    }
    
    /**
     * Prüft ob HIGH_ACTIVITY vorliegt
     */
    fun isHighActivity(eventsInLast2Hours: Int): Boolean {
        return eventsInLast2Hours >= 3
    }
    
    /**
     * Prüft ob SETTLING (erste 30 Min der Session)
     */
    fun isSettling(sessionStartTime: Long): Boolean {
        val elapsed = System.currentTimeMillis() - sessionStartTime
        return elapsed < 30 * 60 * 1000  // 30 Minuten
    }
}

/**
 * Trigger Types für Interventions-Events
 */
object TriggerTypes {
    const val AUTO = "AUTO"             // Automatisch durch Z > Threshold
    const val MANUAL = "MANUAL"         // Manuell vom Benutzer ausgelöst
    const val PREDICTIVE = "PREDICTIVE" // v3.0: Durch dZ/dt Trend erkannt
    const val RETRIGGER = "RETRIGGER"   // Während Cooldown erneut getriggert
}
