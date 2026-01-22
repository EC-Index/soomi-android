# SOOMI - Baby Calming System

**SOOMI** is a privacy-first Android app that turns an old phone into a smart bedside "baby station." It listens for early signs of baby unrest and responds with gentle soothing sounds — all processed locally, with zero recordings and zero cloud dependency.

> ⚠️ **IMPORTANT**: SOOMI is not a medical device. It does not replace parental supervision. Always follow safe sleep guidelines.

---

## Version 2.6 Features

### 🎵 Sound Profiles (Sound Library)
SOOMI now offers multiple sound profiles for both baseline and intervention sounds:

**Free Profiles:**
- **Ozean-Atem (Ocean Breath)** - Default. Gentle ocean-like sound with natural wave movement using Living Noise technology
- **Klassisches Rosa (Classic Pink)** - Balanced pink noise without modulation
- **Tiefes Braun (Deep Brown)** - Deep, calming brown noise with subtle variation

**Pro Profiles (Coming Soon):**
- Herzschlag-Ozean (Heartbeat Ocean)
- Regen-Drift (Rain Drift)
- Ventilator (Fan Stable)
- Sanftes Shush (Shush Soft)
- Reise-Ruhe (Travel Calm)

Access the Sound Library via the music note icon (♪) in the top right corner of the Tonight screen.

### 🌊 Living Noise Technology
The default Ocean Breath profile uses "Living Noise" to create natural-sounding audio that doesn't become monotonous:

- **Slow Amplitude Oscillation (LFO)**: ~12.5 second breathing cycle with ±8% subtle amplitude variation
- **Spectral Drift**: Filter cutoff slowly drifts between 900-1600 Hz for natural tonal movement
- **Zero Allocations**: Optimized for stable playback on low-end Android devices

### 📊 Cooldown Display
After any intervention ends, the UI now shows:
- "Abklingzeit: MM:SS" countdown timer
- Live countdown updates every second
- Clear visual indication that retrigger is temporarily disabled

### 🔤 UI Copy Updates (German)
- "Z: 0" → "Unruhe 0" (Unrest score display)
- "Sitzung starten" → "Beruhigung starten" (Start calming)
- "Sitzung beenden" → "Beruhigung beenden" (Stop calming)
- Updated accessibility labels for screen readers

---

## Features

### 🎧 Real-Time Audio Monitoring
- Computes an "Unrest Score" (0-100) from live microphone input
- Detects patterns typical of baby stirring and crying
- **Privacy-first**: Audio is processed in memory only — never recorded or uploaded

### 🌊 Smart Interventions
- **Baseline Modes**: OFF / Gentle / Medium continuous background noise
- **Early Smoothing**: Detects rising unrest early and starts gentle intervention
- **Crisis Response**: Fast-attack mode for sudden 0→100 crying
- **Effectiveness Learning**: Tracks what works and adapts over time

### 📊 Progress Tracking
- Night summaries with unrest events and soothing time
- Morning check-in for parent feedback
- 7-day trend visualization (improving / stable / worse)

### 🔒 Privacy & Safety
- **Zero recordings** — audio buffers are processed and immediately discarded
- **Works fully offline** — no WiFi required during the night
- **No camera permissions** — microphone only
- **Telemetry opt-in only** — and never includes audio data

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9+ |
| Min SDK | 26 (Android 8.0) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| Local Storage | Room + DataStore |
| Audio | AudioRecord + AudioTrack |

---

## Audio Technical Details

### Living Noise Implementation
The Ocean Breath profile generates "living" noise without using audio samples:

```
Pink Noise → 1-pole Lowpass Filter (drifting cutoff) → LFO Amplitude Modulation → Output
```

**LFO Parameters:**
- Rate: 0.08 Hz (~12.5s cycle)
- Depth: ±8%
- Clamped to 0.85-1.15x to prevent audible pumping

**Spectral Drift:**
- 1-pole lowpass filter
- Cutoff oscillates slowly between 900-1600 Hz
- Drift rate: 0.015 Hz
- Creates natural ocean-like tonal variation

**Performance Constraints:**
- Zero allocations in audio callback (pre-allocated buffers)
- Filter coefficients computed outside hot path
- Stable on low-end Android devices with 2GB RAM

### Sound Profile Selection
- Persisted in DataStore
- Profile changes applied at buffer boundary (safe switching)
- Affects both baseline playback and intervention output

---

## Building the Project

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Steps

```bash
# Clone
git clone https://github.com/your-org/soomi-android.git
cd soomi-android

# Build
./gradlew assembleDebug

# Run tests
./gradlew test
```

---

## Joining the Closed Beta

1. Join the Google Group for testers
2. Accept the Play Store invite link (sent via email)
3. Install from Play Store (Internal/Closed Testing track)

---

## Privacy Statement

### What SOOMI Does NOT Do
- ❌ Record audio to disk
- ❌ Send audio to any server
- ❌ Access the camera
- ❌ Collect personal information
- ❌ Track location

### What SOOMI Stores Locally
- Session statistics (times, event counts)
- User preferences (baseline mode, sound profile, thresholds)
- Learning data (which interventions work best)
- **Never raw audio**

### Telemetry (Optional, Default OFF)
If you opt in:
- We collect: session duration, intervention counts, effectiveness rates
- We NEVER collect: audio, video, location, personal data

---

## Safe Use Guidelines

1. **Place phone near crib, not in it**
2. **Keep device plugged in** for overnight use
3. **Test volume during day** to ensure appropriate levels
4. **This is not a baby monitor** — doesn't replace supervision
5. **Follow safe sleep guidelines** from AAP

---

## Changelog

### v2.6 (Current)
- Added Sound Library with 3 free profiles (Ocean Breath, Classic Pink, Deep Brown)
- Implemented Living Noise technology for Ocean Breath profile
- Added cooldown countdown display ("Abklingzeit: MM:SS")
- Updated UI copy: "Beruhigung starten/beenden", "Unruhe X"
- Added music note icon for Sound Library access
- Pro profiles locked (coming in future release)

### v2.4
- Improved baby cry detection during active playback
- Pitch-based recognition for better accuracy
- Noise filtering improvements

### v2.0
- Complete UI redesign with Material 3
- German localization
- Single-phone mode optimization

---

## License

Proprietary - All Rights Reserved © 2024-2025 SOOMI
