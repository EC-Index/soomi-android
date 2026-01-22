# SOOMI v2.6 Implementation Summary

## Overview
This document summarizes all changes implemented for SOOMI Android App Version 2.6.

---

## A) UI Copy Changes (TonightScreen) ✅

### 1. Unrest Score Label
- **Before**: `"Z: 0"`
- **After**: `"Unruhe 0"` (no colon)
- **File**: `TonightScreen.kt` - `UnrestStatusDisplay()` composable
- **Accessibility**: Updated `contentDescription` to `"Unruhe-Wert ${score.value.toInt()}"`

### 2. Button Labels
- **Start Button**: `"Sitzung starten"` → `"Beruhigung starten"`
- **Stop Button**: `"Sitzung beenden"` → `"Beruhigung beenden"`
- **File**: `TonightScreen.kt` - `SessionButton()` composable
- **Accessibility**: Updated `semantics { contentDescription = buttonText }`

### 3. Cooldown Countdown Display
- **Format**: `"Abklingzeit: MM:SS"` (e.g., "Abklingzeit: 00:45")
- **Updates**: Every 1 second
- **Trigger**: When FSM enters `COOLDOWN` state
- **Implementation**: 
  - `TonightUiState.cooldownRemainingSeconds` state variable
  - `startCooldownTimer()` / `stopCooldownTimer()` in ViewModel
  - `formatCooldownText()` formatting function
  - Displayed in `UnrestStatusDisplay()` when `state == SoomiState.COOLDOWN`

---

## B) Sound Library Module + Selection ✅

### 1. SoundProfile Enum
**File**: `domain/model/SoundProfile.kt`

**FREE Profiles:**
| Enum Value | Display Name | Description |
|------------|--------------|-------------|
| `OCEAN_BREATH` | Ozean-Atem | Default, Living Noise |
| `CLASSIC_PINK` | Klassisches Rosa | Plain pink noise |
| `DEEP_BROWN` | Tiefes Braun | Deep brown noise |

**PRO Profiles (Locked):**
| Enum Value | Display Name |
|------------|--------------|
| `HEARTBEAT_OCEAN` | Herzschlag-Ozean |
| `RAIN_DRIFT` | Regen-Drift |
| `FAN_STABLE` | Ventilator |
| `SHUSH_SOFT` | Sanftes Shush |
| `TRAVEL_CALM` | Reise-Ruhe |

**Feature Flag**: `SoundProfile.IS_PRO_ENABLED = false`

### 2. Sound Library UI Screen
**File**: `ui/screens/soundlibrary/SoundLibraryScreen.kt`

Features:
- List of sound profiles
- Free profiles: selectable with checkmark indicator
- Pro profiles: locked with "PRO" badge + lock icon
- "Pro Coming Soon" dialog when tapping locked profile
- "Test Sound" button for each free profile (plays 2s at Level 1)

### 3. Persistence
**File**: `data/preferences/SoomiPreferences.kt`

- New key: `SOUND_PROFILE = stringPreferencesKey("sound_profile")`
- Default: `SoundProfile.OCEAN_BREATH`
- Flow: `soundProfile: Flow<SoundProfile>`
- Setter: `setSoundProfile(profile: SoundProfile)`

### 4. Navigation
**File**: `ui/navigation/Navigation.kt`

- New route: `Routes.SOUND_LIBRARY = "sound_library"`
- Music note icon in TonightScreen top bar opens Sound Library
- Test sound functionality integrated with `AudioOutputEngine`

### 5. Profile Display on TonightScreen
- `SelectedProfileDisplay()` composable shows current profile
- Tappable to navigate to Sound Library
- Shows: "Klangprofil: {ProfileDisplayName}"

---

## C) Living Noise Engine ✅

### File: `audio/AudioOutputEngine.kt`

### OCEAN_BREATH Implementation

#### 1. Slow Amplitude Oscillation (LFO)
```kotlin
// Parameters
lfoRateHz = 0.08f      // ~12.5s cycle
lfoDepth = 0.08f       // +/- 8%

// Calculation
val lfoValue = sin(lfoPhase)
val amplitudeModulation = 1.0 + (lfoValue * lfoDepth)
val clampedModulation = amplitudeModulation.coerceIn(0.85, 1.15)  // Prevent pumping
```

#### 2. Spectral Drift (1-pole Lowpass Filter)
```kotlin
// Parameters
DRIFT_RATE_HZ = 0.015f    // Very slow drift
DRIFT_CUTOFF_MIN = 900f   // Hz
DRIFT_CUTOFF_MAX = 1600f  // Hz

// Filter: y[n] = (1-a)*x[n] + a*y[n-1]
filterCoeff = exp(-2π * cutoff / sampleRate)
filterState = (1 - filterCoeff) * sample + filterCoeff * filterState
```

### Profile-Specific Settings
| Profile | LFO Rate | LFO Depth | Spectral Drift |
|---------|----------|-----------|----------------|
| OCEAN_BREATH | 0.08 Hz | 8% | Yes |
| CLASSIC_PINK | 0 Hz | 0% | No |
| DEEP_BROWN | 0.03 Hz | 3% | Minimal |

### Zero-Allocation Guarantee
- All buffers pre-allocated at engine initialization
- No object creation in audio callback
- Filter coefficients updated only when cutoff changes significantly (>10 Hz)

### Safe Profile Switching
- `pendingProfileChange` atomic variable
- Applied at next buffer boundary
- No crashes during active playback

---

## D) Implementation Details

### Files Created/Modified

#### New Files:
1. `domain/model/SoundProfile.kt` - Sound profile enum
2. `ui/screens/soundlibrary/SoundLibraryScreen.kt` - Sound Library UI + ViewModel
3. `test/.../SoundProfilePersistenceTest.kt` - Tests for persistence and LFO

#### Modified Files:
1. `data/preferences/SoomiPreferences.kt` - Added soundProfile persistence
2. `data/repository/Repositories.kt` - Added soundProfile to SettingsRepository
3. `audio/AudioOutputEngine.kt` - Living Noise implementation
4. `ui/screens/tonight/TonightScreen.kt` - UI copy changes, cooldown, profile display
5. `ui/navigation/Navigation.kt` - Added Sound Library route
6. `README.md` - Documentation updates

### Tests Added

**File**: `SoundProfilePersistenceTest.kt`

1. `default profile is OCEAN_BREATH`
2. `free profiles list contains only non-pro profiles`
3. `pro profiles list contains only pro profiles`
4. `IS_PRO_ENABLED is false for v2_6 beta`
5. `all profiles have display names in German`
6. `profile can be serialized and deserialized by name`
7. `amplitude modulation clamping prevents pumping`
8. `extreme LFO depth is clamped properly`
9. `zero LFO depth produces no modulation`
10. `ocean breath LFO rate is approximately 12_5 seconds per cycle`
11. `spectral drift cutoff range is valid`
12. `filter coefficient calculation is stable`

---

## Integration Instructions

### Step 1: Copy Files
Copy all files from `soomi-v26-update/` to your project, maintaining directory structure:
- `app/src/main/java/com/soomi/baby/...`
- `app/src/test/java/com/soomi/baby/...`

### Step 2: Update build.gradle.kts
No changes required - all dependencies already present.

### Step 3: Update Database (if needed)
SoundProfile is stored in DataStore, not Room - no migrations required.

### Step 4: Run Tests
```bash
./gradlew test
```

### Step 5: Build & Test on Device
```bash
./gradlew assembleDebug
```

---

## What Was NOT Changed

Per requirements:
- ❌ No cloud uploads added
- ❌ No audio recording added
- ❌ No new permissions beyond microphone
- ✅ All processing remains offline-first and privacy-first

---

## Notes for Reviewers

1. **Living Noise is subtle**: The +/-8% amplitude modulation is intentionally minimal to avoid audible "pumping"

2. **Pro profiles are stubs**: They use similar base sounds but will have unique implementations when Pro is enabled

3. **Cooldown timer starts at 45s**: This matches the default `ThresholdConfig.cooldownTimeMs` value. For dynamic cooldown, integrate with FSM's actual cooldown end time.

4. **German-first UI**: All new strings are in German per existing app language.
