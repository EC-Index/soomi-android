# SOOMI - Baby Calming System

**SOOMI** is a privacy-first Android app that turns an old phone into a smart bedside "baby station." It listens for early signs of baby unrest and responds with gentle soothing sounds — all processed locally, with zero recordings and zero cloud dependency.

> ⚠️ **IMPORTANT**: SOOMI is not a medical device. It does not replace parental supervision. Always follow safe sleep guidelines.

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
| Language | Kotlin 1.9 |
| Min SDK | 26 (Android 8.0) |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| Local Storage | Room + DataStore |
| Audio | AudioRecord + AudioTrack |

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

## License

Proprietary - All Rights Reserved © 2024 SOOMI
