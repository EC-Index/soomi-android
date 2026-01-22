package com.soomi.baby.domain.model

/**
 * Sound Profile for SOOMI v2.6
 * 
 * Defines available sound profiles for baseline and intervention sounds.
 * Each profile affects both baseline playback and intervention output.
 * 
 * FREE profiles are available to all users.
 * PRO profiles are locked (coming soon in future versions).
 */
enum class SoundProfile(
    val displayName: String,
    val description: String,
    val isPro: Boolean
) {
    // === FREE PROFILES ===
    
    /** Default: Ocean-like breathing pattern with subtle amplitude and spectral drift */
    OCEAN_BREATH(
        displayName = "Ozean-Atem",
        description = "Sanftes Meeresrauschen mit natürlicher Wellenbewegung",
        isPro = false
    ),
    
    /** Classic pink noise - balanced frequency spectrum */
    CLASSIC_PINK(
        displayName = "Klassisches Rosa",
        description = "Ausgewogenes Rosa-Rauschen ohne Modulation",
        isPro = false
    ),
    
    /** Deep brown noise - rumbling, very low frequencies emphasized */
    DEEP_BROWN(
        displayName = "Tiefes Braun",
        description = "Tiefes, beruhigendes Rauschen",
        isPro = false
    ),
    
    // === PRO PROFILES (Locked in v2.6) ===
    
    /** Heartbeat rhythm overlaid on ocean sounds */
    HEARTBEAT_OCEAN(
        displayName = "Herzschlag-Ozean",
        description = "Sanfter Herzschlag mit Meeresrauschen",
        isPro = true
    ),
    
    /** Gentle rain with subtle drift */
    RAIN_DRIFT(
        displayName = "Regen-Drift",
        description = "Sanfter Regen mit natürlicher Variation",
        isPro = true
    ),
    
    /** Stable fan sound - consistent white noise */
    FAN_STABLE(
        displayName = "Ventilator",
        description = "Konstantes Ventilator-Geräusch",
        isPro = true
    ),
    
    /** Soft shushing sound with rhythm */
    SHUSH_SOFT(
        displayName = "Sanftes Shush",
        description = "Rhythmisches, weiches Shushing",
        isPro = true
    ),
    
    /** Travel/car-like rumble */
    TRAVEL_CALM(
        displayName = "Reise-Ruhe",
        description = "Auto- oder Zug-ähnliches Rauschen",
        isPro = true
    );
    
    companion object {
        /** Get all free profiles */
        fun freeProfiles(): List<SoundProfile> = entries.filter { !it.isPro }
        
        /** Get all pro profiles */
        fun proProfiles(): List<SoundProfile> = entries.filter { it.isPro }
        
        /** Default profile */
        val DEFAULT = OCEAN_BREATH
        
        /** Feature flag for Pro features - set to false for v2.6 beta */
        const val IS_PRO_ENABLED = false
    }
}
