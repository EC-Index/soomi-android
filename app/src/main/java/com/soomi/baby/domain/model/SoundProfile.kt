package com.soomi.baby.domain.model

/**
 * Sound Profile for SOOMI v2.6
 * 
 * Defines available sound profiles for soothing.
 * Free profiles are available to all users.
 * Pro profiles are locked behind a future Pro subscription.
 */
enum class SoundProfile(
    val displayName: String,
    val description: String,
    val isPro: Boolean = false
) {
    // === FREE PROFILES ===
    OCEAN_BREATH(
        displayName = "Ozean-Atem",
        description = "Lebendiges Rauschen mit sanfter Wellenbewegung",
        isPro = false
    ),
    CLASSIC_PINK(
        displayName = "Klassisches Rosa",
        description = "Gleichmäßiges rosa Rauschen",
        isPro = false
    ),
    DEEP_BROWN(
        displayName = "Tiefes Braun",
        description = "Tiefes, beruhigendes braunes Rauschen",
        isPro = false
    ),
    
    // === PRO PROFILES (locked) ===
    HEARTBEAT_OCEAN(
        displayName = "Herzschlag-Ozean",
        description = "Ozeanwellen mit sanftem Herzschlag",
        isPro = true
    ),
    RAIN_DRIFT(
        displayName = "Regen-Drift",
        description = "Sanfter Regen mit wechselnder Intensität",
        isPro = true
    ),
    FAN_STABLE(
        displayName = "Ventilator",
        description = "Gleichmäßiges Ventilatorgeräusch",
        isPro = true
    ),
    SHUSH_SOFT(
        displayName = "Sanftes Shush",
        description = "Rhythmisches Shush-Muster",
        isPro = true
    ),
    TRAVEL_CALM(
        displayName = "Reise-Ruhe",
        description = "Auto-/Zuggeräusche für unterwegs",
        isPro = true
    );
    
    companion object {
        /**
         * Feature flag for Pro profiles
         * Set to true when Pro subscription is available
         */
        const val IS_PRO_ENABLED = false
        
        /**
         * Default profile for new users
         */
        val DEFAULT = OCEAN_BREATH
        
        /**
         * Get all free profiles
         */
        fun freeProfiles(): List<SoundProfile> = entries.filter { !it.isPro }
        
        /**
         * Get all pro profiles
         */
        fun proProfiles(): List<SoundProfile> = entries.filter { it.isPro }
        
        /**
         * Get all available profiles (respects IS_PRO_ENABLED)
         */
        fun availableProfiles(): List<SoundProfile> {
            return if (IS_PRO_ENABLED) {
                entries.toList()
            } else {
                freeProfiles()
            }
        }
    }
}
