package com.soomi.baby.ui

/**
 * Localized strings for SOOMI app
 * Supports English (en) and German (de)
 */
object LocalizedStrings {
    
    enum class Language(val code: String, val displayName: String, val flag: String) {
        ENGLISH("en", "English", "🇬🇧"),
        GERMAN("de", "Deutsch", "🇩🇪")
    }
    
    data class Strings(
        // Language Selection
        val selectLanguage: String,
        val continueButton: String,
        
        // Onboarding
        val welcomeTitle: String,
        val welcomeDesc: String,
        val smartDetectionTitle: String,
        val smartDetectionDesc: String,
        val privacyTitle: String,
        val privacyDesc: String,
        val readyTitle: String,
        val readyDesc: String,
        val skip: String,
        val back: String,
        val next: String,
        val getStarted: String,
        
        // Tonight Screen
        val tonightTitle: String,
        val baselineSound: String,
        val off: String,
        val gentle: String,
        val medium: String,
        val startSession: String,
        val stopSession: String,
        val sootheNow: String,
        val panicStop: String,
        val resume: String,
        val privacyNotice: String,
        
        // States
        val stateIdle: String,
        val stateBaseline: String,
        val stateCalm: String,
        val stateRising: String,
        val stateSoothing: String,
        val stateCrisis: String,
        val statePaused: String,
        
        // Progress Screen
        val progressTitle: String,
        val recentNights: String,
        val noDataYet: String,
        val noDataDesc: String,
        val improving: String,
        val stable: String,
        val moreRestless: String,
        val nightsTracked: String,
        val totalEvents: String,
        val unrestEvents: String,
        val soothingMinutes: String,
        
        // Settings Screen
        val settingsTitle: String,
        val privacyInfo: String,
        val privacyInfoDesc: String,
        val sensitivitySection: String,
        val earlyThreshold: String,
        val crisisThreshold: String,
        val audioSection: String,
        val maxVolume: String,
        val telemetrySection: String,
        val telemetryTitle: String,
        val telemetryDesc: String,
        val dataSection: String,
        val resetLearning: String,
        val resetConfirmTitle: String,
        val resetConfirmDesc: String,
        val cancel: String,
        val reset: String,
        val languageSection: String,
        val changeLanguage: String,
        
        // Audio Output
        val audioOutputSection: String,
        val audioOutput: String,
        val phoneSpeaker: String,
        val bluetoothSpeaker: String,
        val autoRecommended: String,
        val currentDevice: String,
        val testSound: String,
        val reconnectBluetooth: String,
        val audioFocusLost: String,
        
        // Single Phone Mode
        val singlePhoneModeTitle: String,
        val singlePhoneModeDesc: String,
        val singlePhoneCard: String,
        val singlePhoneBullet1: String,
        val singlePhoneBullet2: String,
        val singlePhoneBullet3: String,
        
        // Offline Mode
        val offlineModeNote: String,
        
        // Morning Check-in
        val morningTitle: String,
        val howDidItHelp: String,
        val helpedA: String,
        val helpedB: String,
        val helpedC: String,
        val wasItAnnoying: String,
        val annoyingYes: String,
        val annoyingNo: String,
        val annoyingSometimes: String,
        val notes: String,
        val notesHint: String,
        val submit: String,
        
        // Permissions
        val micPermissionTitle: String,
        val micPermissionDesc: String,
        val allow: String,
        val notNow: String,
        
        // General
        val appName: String
    )
    
    val english = Strings(
        // Language Selection
        selectLanguage = "Select Language",
        continueButton = "Continue",
        
        // Onboarding
        welcomeTitle = "Welcome to SOOMI",
        welcomeDesc = "A gentle helper for calmer nights.\n\nSOOMI listens for early signs of baby unrest and responds with soothing sounds before crying escalates.",
        smartDetectionTitle = "Smart Detection",
        smartDetectionDesc = "SOOMI uses your phone's microphone to detect stirring and crying patterns.\n\nWhen unrest is detected early, gentle soothing sounds help your baby settle back to sleep.",
        privacyTitle = "Privacy First",
        privacyDesc = "Your privacy is sacred:\n\n• Audio is processed locally only\n• Nothing is recorded or saved\n• Works fully offline\n• No camera access needed",
        readyTitle = "Ready for Tonight?",
        readyDesc = "Place your phone near the crib (not in it), select your baseline sound preference, and tap Start.\n\nSOOMI will take it from there.",
        skip = "Skip",
        back = "Back",
        next = "Next",
        getStarted = "Get Started",
        
        // Tonight Screen
        tonightTitle = "Tonight",
        baselineSound = "Baseline Sound",
        off = "Off",
        gentle = "Gentle",
        medium = "Medium",
        startSession = "Start Session",
        stopSession = "Stop Session",
        sootheNow = "Soothe Now",
        panicStop = "Panic Stop",
        resume = "Resume",
        privacyNotice = "Audio is processed locally and never recorded",
        
        // States
        stateIdle = "Ready",
        stateBaseline = "Listening...",
        stateCalm = "Baby is calm",
        stateRising = "Unrest detected",
        stateSoothing = "Soothing...",
        stateCrisis = "Crisis mode",
        statePaused = "Paused",
        
        // Progress Screen
        progressTitle = "Progress",
        recentNights = "Recent Nights",
        noDataYet = "No nights tracked yet",
        noDataDesc = "Start your first session tonight\nto begin tracking progress",
        improving = "Improving",
        stable = "Stable",
        moreRestless = "More restless",
        nightsTracked = "nights tracked",
        totalEvents = "total events",
        unrestEvents = "unrest events",
        soothingMinutes = "min soothing",
        
        // Settings Screen
        settingsTitle = "Settings",
        privacyInfo = "Privacy First",
        privacyInfoDesc = "All audio processing happens on your device. Nothing is recorded, saved, or sent anywhere.",
        sensitivitySection = "Sensitivity",
        earlyThreshold = "Early Detection Threshold",
        crisisThreshold = "Crisis Threshold",
        audioSection = "Audio",
        maxVolume = "Maximum Volume",
        telemetrySection = "Analytics",
        telemetryTitle = "Share Anonymous Usage Data",
        telemetryDesc = "Help improve SOOMI by sharing anonymous statistics (no audio data)",
        dataSection = "Data",
        resetLearning = "Reset Learning Data",
        resetConfirmTitle = "Reset Learning?",
        resetConfirmDesc = "This will clear all learned intervention preferences. SOOMI will start fresh.",
        cancel = "Cancel",
        reset = "Reset",
        languageSection = "Language",
        changeLanguage = "Change Language",
        
        // Audio Output
        audioOutputSection = "Audio Output",
        audioOutput = "Output Device",
        phoneSpeaker = "Phone Speaker",
        bluetoothSpeaker = "Bluetooth Speaker",
        autoRecommended = "Auto (recommended)",
        currentDevice = "Current device",
        testSound = "Test Sound",
        reconnectBluetooth = "Reconnect Bluetooth",
        audioFocusLost = "Paused (audio focus lost)",
        
        // Single Phone Mode
        singlePhoneModeTitle = "Single Phone Mode",
        singlePhoneModeDesc = "Optimized for using one phone as monitor and speaker",
        singlePhoneCard = "Only one phone? No problem.",
        singlePhoneBullet1 = "SOOMI can run on this phone",
        singlePhoneBullet2 = "Optional: connect a small Bluetooth speaker for better sound",
        singlePhoneBullet3 = "Works offline (airplane mode supported)",
        
        // Offline Mode
        offlineModeNote = "Offline mode: You can use SOOMI in airplane mode. No Wi-Fi required.",
        
        // Morning Check-in
        morningTitle = "Good Morning!",
        howDidItHelp = "How much did SOOMI help last night?",
        helpedA = "A lot",
        helpedB = "A little",
        helpedC = "Not really",
        wasItAnnoying = "Were the sounds annoying?",
        annoyingYes = "Yes",
        annoyingNo = "No",
        annoyingSometimes = "Sometimes",
        notes = "Notes (optional)",
        notesHint = "Any feedback or observations...",
        submit = "Submit",
        
        // Permissions
        micPermissionTitle = "Microphone Permission",
        micPermissionDesc = "SOOMI needs microphone access to hear when your baby stirs.\n\nAudio is processed locally and never recorded or uploaded.",
        allow = "Allow",
        notNow = "Not Now",
        
        // General
        appName = "SOOMI"
    )
    
    val german = Strings(
        // Language Selection
        selectLanguage = "Sprache wählen",
        continueButton = "Weiter",
        
        // Onboarding
        welcomeTitle = "Willkommen bei SOOMI",
        welcomeDesc = "Ein sanfter Helfer für ruhigere Nächte.\n\nSOOMI erkennt frühe Anzeichen von Unruhe bei deinem Baby und reagiert mit beruhigenden Klängen, bevor das Weinen eskaliert.",
        smartDetectionTitle = "Intelligente Erkennung",
        smartDetectionDesc = "SOOMI nutzt das Mikrofon deines Handys, um Bewegungen und Weinmuster zu erkennen.\n\nBei früher Unruhe helfen sanfte Klänge deinem Baby, wieder einzuschlafen.",
        privacyTitle = "Datenschutz zuerst",
        privacyDesc = "Deine Privatsphäre ist uns heilig:\n\n• Audio wird nur lokal verarbeitet\n• Nichts wird aufgezeichnet oder gespeichert\n• Funktioniert komplett offline\n• Kein Kamerazugriff nötig",
        readyTitle = "Bereit für heute Nacht?",
        readyDesc = "Platziere dein Handy in der Nähe des Bettchens (nicht darin), wähle deine bevorzugte Hintergrundmusik und tippe auf Start.\n\nSOOMI übernimmt den Rest.",
        skip = "Überspringen",
        back = "Zurück",
        next = "Weiter",
        getStarted = "Los geht's",
        
        // Tonight Screen
        tonightTitle = "Heute Nacht",
        baselineSound = "Hintergrundklang",
        off = "Aus",
        gentle = "Sanft",
        medium = "Mittel",
        startSession = "Sitzung starten",
        stopSession = "Sitzung beenden",
        sootheNow = "Jetzt beruhigen",
        panicStop = "Sofort stoppen",
        resume = "Fortsetzen",
        privacyNotice = "Audio wird lokal verarbeitet und nie aufgezeichnet",
        
        // States
        stateIdle = "Bereit",
        stateBaseline = "Höre zu...",
        stateCalm = "Baby ist ruhig",
        stateRising = "Unruhe erkannt",
        stateSoothing = "Beruhige...",
        stateCrisis = "Krisenmodus",
        statePaused = "Pausiert",
        
        // Progress Screen
        progressTitle = "Fortschritt",
        recentNights = "Letzte Nächte",
        noDataYet = "Noch keine Nächte erfasst",
        noDataDesc = "Starte heute Abend deine erste Sitzung\num den Fortschritt zu verfolgen",
        improving = "Verbesserung",
        stable = "Stabil",
        moreRestless = "Unruhiger",
        nightsTracked = "Nächte erfasst",
        totalEvents = "Ereignisse gesamt",
        unrestEvents = "Unruhe-Ereignisse",
        soothingMinutes = "Min. beruhigt",
        
        // Settings Screen
        settingsTitle = "Einstellungen",
        privacyInfo = "Datenschutz zuerst",
        privacyInfoDesc = "Alle Audioverarbeitung findet auf deinem Gerät statt. Nichts wird aufgezeichnet, gespeichert oder irgendwohin gesendet.",
        sensitivitySection = "Empfindlichkeit",
        earlyThreshold = "Früherkennung-Schwellwert",
        crisisThreshold = "Krisen-Schwellwert",
        audioSection = "Audio",
        maxVolume = "Maximale Lautstärke",
        telemetrySection = "Analyse",
        telemetryTitle = "Anonyme Nutzungsdaten teilen",
        telemetryDesc = "Hilf SOOMI zu verbessern durch anonyme Statistiken (keine Audiodaten)",
        dataSection = "Daten",
        resetLearning = "Lerndaten zurücksetzen",
        resetConfirmTitle = "Lerndaten zurücksetzen?",
        resetConfirmDesc = "Dies löscht alle gelernten Einstellungen. SOOMI startet von vorne.",
        cancel = "Abbrechen",
        reset = "Zurücksetzen",
        languageSection = "Sprache",
        changeLanguage = "Sprache ändern",
        
        // Audio Output
        audioOutputSection = "Audioausgabe",
        audioOutput = "Ausgabegerät",
        phoneSpeaker = "Handy-Lautsprecher",
        bluetoothSpeaker = "Bluetooth-Lautsprecher",
        autoRecommended = "Automatisch (empfohlen)",
        currentDevice = "Aktuelles Gerät",
        testSound = "Testton",
        reconnectBluetooth = "Bluetooth verbinden",
        audioFocusLost = "Pausiert (Audio-Fokus verloren)",
        
        // Single Phone Mode
        singlePhoneModeTitle = "Ein-Handy-Modus",
        singlePhoneModeDesc = "Optimiert für die Nutzung eines Handys als Monitor und Lautsprecher",
        singlePhoneCard = "Nur ein Handy? Kein Problem.",
        singlePhoneBullet1 = "SOOMI kann auf diesem Handy laufen",
        singlePhoneBullet2 = "Optional: Verbinde einen kleinen Bluetooth-Lautsprecher für besseren Klang",
        singlePhoneBullet3 = "Funktioniert offline (Flugmodus wird unterstützt)",
        
        // Offline Mode
        offlineModeNote = "Offline-Modus: Du kannst SOOMI im Flugmodus nutzen. Kein WLAN erforderlich.",
        
        // Morning Check-in
        morningTitle = "Guten Morgen!",
        howDidItHelp = "Wie sehr hat SOOMI letzte Nacht geholfen?",
        helpedA = "Sehr",
        helpedB = "Ein bisschen",
        helpedC = "Nicht wirklich",
        wasItAnnoying = "Waren die Klänge störend?",
        annoyingYes = "Ja",
        annoyingNo = "Nein",
        annoyingSometimes = "Manchmal",
        notes = "Notizen (optional)",
        notesHint = "Feedback oder Beobachtungen...",
        submit = "Absenden",
        
        // Permissions
        micPermissionTitle = "Mikrofon-Berechtigung",
        micPermissionDesc = "SOOMI braucht Mikrofonzugriff, um zu hören wenn dein Baby unruhig wird.\n\nAudio wird lokal verarbeitet und nie aufgezeichnet oder hochgeladen.",
        allow = "Erlauben",
        notNow = "Nicht jetzt",
        
        // General
        appName = "SOOMI"
    )
    
    fun getStrings(languageCode: String): Strings {
        return when (languageCode) {
            "de" -> german
            else -> english
        }
    }
}
