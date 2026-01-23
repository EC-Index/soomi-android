package com.soomi.baby.domain.model

/**
 * SOOMI v3.0 - Erweiterte Zustandsmaschine
 * 
 * Zustände:
 * - STOPPED:    Session nicht aktiv
 * - IDLE:       (Legacy) Idle-Zustand
 * - BASELINE:   (Legacy) Nur Baseline läuft
 * - LISTENING:  Überwachung aktiv, nur Baseline
 * - PREDICTIVE: v3.0 NEU - Soft-Start durch Trend-Erkennung
 * - SOOTHING:   Intervention aktiv
 * - COOLDOWN:   Baby beruhigt, Sound fadet aus
 */
enum class SoomiState {
    STOPPED,
    IDLE,
    BASELINE,
    LISTENING,
    PREDICTIVE,  // v3.0: Neuer Zustand für präventive Intervention
    SOOTHING,
    COOLDOWN;
    
    /**
     * Prüft ob Session aktiv ist
     */
    fun isActive(): Boolean = this != STOPPED
    
    /**
     * Prüft ob gerade interveniert wird
     */
    fun isIntervening(): Boolean = this == SOOTHING || this == PREDICTIVE
    
    /**
     * Prüft ob Sound aktiv sein sollte
     */
    fun shouldPlaySound(): Boolean = when (this) {
        STOPPED -> false
        IDLE -> false
        BASELINE -> true
        LISTENING -> true  // Baseline kann laufen
        PREDICTIVE -> true
        SOOTHING -> true
        COOLDOWN -> true
    }
    
    /**
     * Deutscher Display-Name
     */
    fun displayNameDe(): String = when (this) {
        STOPPED -> "Gestoppt"
        IDLE -> "Bereit"
        BASELINE -> "Baseline"
        LISTENING -> "Überwachung"
        PREDICTIVE -> "Früherkennung"
        SOOTHING -> "Beruhigung"
        COOLDOWN -> "Abklingzeit"
    }
}

/**
 * Intervention Level mit Volume Multiplier
 */
enum class InterventionLevel(val volumeMultiplier: Float) {
    OFF(0f),
    LEVEL_1(0.3f),   // Sanft - 30%
    LEVEL_2(0.6f),   // Mittel - 60%
    LEVEL_3(0.9f);   // Intensiv - 90%
    
    /**
     * Nächstes Level (für Eskalation)
     */
    fun escalate(): InterventionLevel = when (this) {
        OFF -> LEVEL_1
        LEVEL_1 -> LEVEL_2
        LEVEL_2 -> LEVEL_3
        LEVEL_3 -> LEVEL_3
    }
    
    /**
     * Vorheriges Level (für De-Eskalation)
     */
    fun deescalate(): InterventionLevel = when (this) {
        OFF -> OFF
        LEVEL_1 -> OFF
        LEVEL_2 -> LEVEL_1
        LEVEL_3 -> LEVEL_2
    }
    
    /**
     * Deutscher Display-Name
     */
    fun displayNameDe(): String = when (this) {
        OFF -> "Aus"
        LEVEL_1 -> "Sanft"
        LEVEL_2 -> "Mittel"
        LEVEL_3 -> "Intensiv"
    }
}

/**
 * Sound Types
 */
enum class SoundType {
    BROWN_NOISE,
    WHITE_NOISE,
    PINK_NOISE,
    SHUSH,
    HEARTBEAT,
    RAIN,
    OCEAN;
    
    /**
     * Deutscher Display-Name
     */
    fun displayNameDe(): String = when (this) {
        BROWN_NOISE -> "Braunes Rauschen"
        WHITE_NOISE -> "Weißes Rauschen"
        PINK_NOISE -> "Rosa Rauschen"
        SHUSH -> "Shush"
        HEARTBEAT -> "Herzschlag"
        RAIN -> "Regen"
        OCEAN -> "Ozean"
    }
}

/**
 * Baseline Mode
 */
enum class BaselineMode {
    OFF,
    GENTLE,
    MEDIUM;
    
    /**
     * Deutscher Display-Name
     */
    fun displayNameDe(): String = when (this) {
        OFF -> "Aus"
        GENTLE -> "Sanft"
        MEDIUM -> "Mittel"
    }
    
    /**
     * Entsprechendes Intervention Level
     */
    fun toLevel(): InterventionLevel = when (this) {
        OFF -> InterventionLevel.OFF
        GENTLE -> InterventionLevel.LEVEL_1
        MEDIUM -> InterventionLevel.LEVEL_2
    }
}

/**
 * Intervention Config
 */
data class InterventionConfig(
    // Start Thresholds
    val startThreshold: Float = 50f,        // Z-Wert für Interventionsstart
    val startConfirmSec: Int = 3,           // Bestätigungszeit
    
    // Calm Thresholds
    val calmThreshold: Float = 25f,         // Z-Wert für "Baby ist ruhig"
    val calmConfirmSec: Int = 5,            // Bestätigungszeit
    
    // Retrigger
    val retriggerThreshold: Float = 60f,    // Z-Wert für Retrigger während Cooldown
    val retriggerConfirmSec: Int = 2,       // Bestätigungszeit
    
    // Timing
    val minSoothingSec: Int = 10,           // Minimale Beruhigungszeit
    val cooldownSec: Int = 45,              // Cooldown-Dauer
    
    // Escalation
    val maxEscalationsPerEvent: Int = 2,    // Max Eskalationen pro Event
    
    // v3.0: Predictive
    val predictiveEnabled: Boolean = true,  // Predictive Intervention aktiviert
    val predictiveDzDtThreshold: Float = 5f, // dZ/dt für Predictive Start
    val predictiveZThreshold: Float = 35f,  // Z-Wert für Übergang zu SOOTHING
    val predictiveDzDtCalmThreshold: Float = 1f // dZ/dt unter dem LISTENING erreicht wird
)

/**
 * Unrest Score (Z-Wert)
 */
data class UnrestScore(
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Zone basierend auf Z-Wert
     */
    fun getZone(): UnrestZone = when {
        value < 35f -> UnrestZone.CALM
        value < 70f -> UnrestZone.ACTIVE
        else -> UnrestZone.UNREST
    }
}

/**
 * Unrest Zones für UI
 */
enum class UnrestZone {
    CALM,     // 0-35: Grün
    ACTIVE,   // 35-70: Gelb
    UNREST;   // 70-100: Rot
    
    fun displayNameDe(): String = when (this) {
        CALM -> "Ruhig"
        ACTIVE -> "Aktiv"
        UNREST -> "Unruhig"
    }
}
