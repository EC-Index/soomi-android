# SOOMI v2.7 Implementation Summary

## New Simplified FSM for MVP

### States
| State | German | Description |
|-------|--------|-------------|
| `STOPPED` | Gestoppt | Session aus |
| `LISTENING` | Höre zu... | Session aktiv, nur Baseline (wenn gewählt) |
| `SOOTHING` | Beruhige... | Intervention läuft (Baby unruhig) |
| `COOLDOWN` | Abklingzeit | Baby ruhig, Sound läuft noch weiter mit Countdown |

### Transitions
```
STOPPED ──[Start]──> LISTENING

LISTENING ──[Unruhe ≥ 70 für 1.5s]──> SOOTHING

SOOTHING ──[Unruhe ≤ 35 für 3s]──> COOLDOWN

COOLDOWN ──[Timer = 0]──> LISTENING (Sound stoppt)
COOLDOWN ──[Unruhe ≥ 55]──> SOOTHING (Retrigger, Sound bleibt an)
```

### Thresholds (Configurable)
| Threshold | Default | Description |
|-----------|---------|-------------|
| `soothingTriggerThreshold` | 70 | Unruhe-Schwelle für SOOTHING |
| `soothingTriggerDurationMs` | 1500ms | Wie lange Schwelle gehalten |
| `cooldownTriggerThreshold` | 35 | Unruhe-Schwelle für COOLDOWN |
| `cooldownTriggerDurationMs` | 3000ms | Wie lange ruhig sein |
| `retriggerThreshold` | 55 | Unruhe für Retrigger in COOLDOWN |
| `cooldownDurationMs` | 45000ms | Cooldown Timer Dauer |

## Files Changed/Added

### New Files
- `audio/SimpleFsm.kt` - Neue vereinfachte State Machine

### Modified Files
- `domain/model/Models.kt` - Neue SoomiState enum (STOPPED, LISTENING, SOOTHING, COOLDOWN)
- `service/SoomiService.kt` - Verwendet SimpleFsm statt InterventionEngine
- `ui/screens/tonight/TonightScreen.kt` - Neue State-Anzeige mit Cooldown Timer

## UI Changes
- Status zeigt jetzt: Gestoppt / Höre zu... / Beruhige... / Abklingzeit
- Cooldown zeigt MM:SS Countdown
- Button: "Beruhigung starten" / "Beruhigung beenden"
- Version: v2.7

## Future (v3.0)
- Learning & Cascading interventions per patent
- Machine learning for optimal intervention timing
- Multi-level escalation system
