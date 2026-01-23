package com.soomi.baby.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SOOMI v3.0 - Gradient Calculator
 * 
 * Berechnet dZ/dt (Änderungsrate des Z-Werts) für Predictive Intervention.
 * Verwendet einen gleitenden Durchschnitt über die letzten N Messungen
 * für stabile Trend-Erkennung.
 * 
 * Beispiel:
 * - Z steigt von 20 auf 30 in 2 Sekunden → dZ/dt = +5/s
 * - Z fällt von 50 auf 40 in 5 Sekunden → dZ/dt = -2/s
 */
class GradientCalculator(
    private val windowSize: Int = 5,           // Anzahl Messungen für gleitenden Durchschnitt
    private val measurementIntervalMs: Long = 500  // Erwartetes Messintervall
) {
    companion object {
        private const val TAG = "GradientCalculator"
    }
    
    // Ringpuffer für Z-Werte
    private val zBuffer = ArrayDeque<ZMeasurement>(windowSize + 1)
    
    // Berechnete Gradienten
    private val _currentGradient = MutableStateFlow(0f)
    val currentGradient: StateFlow<Float> = _currentGradient.asStateFlow()
    
    private val _smoothedGradient = MutableStateFlow(0f)
    val smoothedGradient: StateFlow<Float> = _smoothedGradient.asStateFlow()
    
    // Trend-Status
    private val _trend = MutableStateFlow(Trend.STABLE)
    val trend: StateFlow<Trend> = _trend.asStateFlow()
    
    // Statistiken
    private var totalMeasurements = 0L
    private var lastGradients = ArrayDeque<Float>(windowSize)
    
    /**
     * Fügt eine neue Z-Wert Messung hinzu und berechnet den Gradienten.
     * 
     * @param z Der aktuelle Z-Wert (0-100)
     * @param timestamp Zeitstempel der Messung (optional, default = now)
     * @return Der berechnete Gradient (dZ/dt in Einheiten pro Sekunde)
     */
    fun addMeasurement(z: Float, timestamp: Long = System.currentTimeMillis()): Float {
        val measurement = ZMeasurement(z, timestamp)
        
        // Zum Buffer hinzufügen
        zBuffer.addLast(measurement)
        if (zBuffer.size > windowSize + 1) {
            zBuffer.removeFirst()
        }
        
        totalMeasurements++
        
        // Gradient berechnen wenn genug Daten
        if (zBuffer.size >= 2) {
            val instantGradient = calculateInstantGradient()
            _currentGradient.value = instantGradient
            
            // Gleitenden Durchschnitt aktualisieren
            lastGradients.addLast(instantGradient)
            if (lastGradients.size > windowSize) {
                lastGradients.removeFirst()
            }
            
            val smoothed = lastGradients.average().toFloat()
            _smoothedGradient.value = smoothed
            
            // Trend bestimmen
            _trend.value = determineTrend(smoothed)
            
            if (totalMeasurements % 10 == 0L) {  // Logging alle 5 Sekunden
                Log.d(TAG, "Z=$z, dZ/dt=${"%.2f".format(instantGradient)}, " +
                           "smoothed=${"%.2f".format(smoothed)}, trend=${_trend.value}")
            }
            
            return smoothed
        }
        
        return 0f
    }
    
    /**
     * Berechnet den instantanen Gradienten zwischen den letzten beiden Messungen.
     */
    private fun calculateInstantGradient(): Float {
        if (zBuffer.size < 2) return 0f
        
        val newest = zBuffer.last()
        val oldest = zBuffer.first()
        
        val deltaZ = newest.z - oldest.z
        val deltaT = (newest.timestamp - oldest.timestamp) / 1000f  // In Sekunden
        
        return if (deltaT > 0) {
            deltaZ / deltaT
        } else {
            0f
        }
    }
    
    /**
     * Bestimmt den Trend basierend auf dem geglätteten Gradienten.
     */
    private fun determineTrend(gradient: Float): Trend {
        return when {
            gradient > 5f -> Trend.RISING_FAST      // Schnell steigend (>5/s)
            gradient > 2f -> Trend.RISING           // Steigend (2-5/s)
            gradient > 0.5f -> Trend.RISING_SLOW    // Langsam steigend (0.5-2/s)
            gradient < -5f -> Trend.FALLING_FAST    // Schnell fallend (<-5/s)
            gradient < -2f -> Trend.FALLING         // Fallend (-5 bis -2/s)
            gradient < -0.5f -> Trend.FALLING_SLOW  // Langsam fallend (-2 bis -0.5/s)
            else -> Trend.STABLE                    // Stabil (-0.5 bis 0.5/s)
        }
    }
    
    /**
     * Prüft ob Predictive Intervention getriggert werden sollte.
     * 
     * @param currentZ Aktueller Z-Wert
     * @param threshold dZ/dt Schwellenwert (default: 5)
     * @param maxZ Maximaler Z-Wert für Predictive (default: 50, darüber direkt SOOTHING)
     * @return true wenn Predictive getriggert werden sollte
     */
    fun shouldTriggerPredictive(
        currentZ: Float,
        threshold: Float = 5f,
        maxZ: Float = 50f
    ): Boolean {
        // Nicht triggern wenn Z bereits über Schwelle
        if (currentZ >= maxZ) return false
        
        // Triggern wenn Gradient hoch genug und Trend steigend
        val gradient = _smoothedGradient.value
        val trend = _trend.value
        
        return gradient >= threshold && 
               (trend == Trend.RISING || trend == Trend.RISING_FAST)
    }
    
    /**
     * Prüft ob der Trend sich beruhigt hat (für PREDICTIVE → LISTENING).
     * 
     * @param threshold dZ/dt unter dem als "beruhigt" gilt
     * @return true wenn Gradient unter Schwelle
     */
    fun hasCalmingTrend(threshold: Float = 1f): Boolean {
        return _smoothedGradient.value < threshold
    }
    
    /**
     * Prüft ob der Trend weiter eskaliert (für PREDICTIVE → SOOTHING).
     * 
     * @param zThreshold Z-Wert Schwelle
     * @return true wenn Z über Schwelle
     */
    fun shouldEscalatePredictive(currentZ: Float, zThreshold: Float = 35f): Boolean {
        return currentZ >= zThreshold
    }
    
    /**
     * Prognostiziert den Z-Wert in X Sekunden basierend auf aktuellem Trend.
     * 
     * @param currentZ Aktueller Z-Wert
     * @param secondsAhead Sekunden in die Zukunft
     * @return Prognostizierter Z-Wert
     */
    fun predictZ(currentZ: Float, secondsAhead: Float = 10f): Float {
        val predictedZ = currentZ + (_smoothedGradient.value * secondsAhead)
        return predictedZ.coerceIn(0f, 100f)
    }
    
    /**
     * Prognostiziert die Zeit bis Z einen bestimmten Wert erreicht.
     * 
     * @param currentZ Aktueller Z-Wert
     * @param targetZ Ziel Z-Wert
     * @return Geschätzte Zeit in Sekunden (oder null wenn nicht erreichbar mit aktuellem Trend)
     */
    fun predictTimeToReach(currentZ: Float, targetZ: Float): Float? {
        val gradient = _smoothedGradient.value
        
        // Wenn steigend und Ziel höher, oder fallend und Ziel niedriger
        if ((gradient > 0 && targetZ > currentZ) || (gradient < 0 && targetZ < currentZ)) {
            val deltaZ = targetZ - currentZ
            return deltaZ / gradient
        }
        
        return null
    }
    
    /**
     * Setzt den Calculator zurück.
     */
    fun reset() {
        zBuffer.clear()
        lastGradients.clear()
        _currentGradient.value = 0f
        _smoothedGradient.value = 0f
        _trend.value = Trend.STABLE
        totalMeasurements = 0
        Log.d(TAG, "Calculator reset")
    }
    
    /**
     * Gibt Debug-Informationen zurück.
     */
    fun getDebugInfo(): GradientDebugInfo {
        return GradientDebugInfo(
            bufferSize = zBuffer.size,
            currentGradient = _currentGradient.value,
            smoothedGradient = _smoothedGradient.value,
            trend = _trend.value,
            totalMeasurements = totalMeasurements,
            oldestZ = zBuffer.firstOrNull()?.z,
            newestZ = zBuffer.lastOrNull()?.z
        )
    }
}

/**
 * Einzelne Z-Wert Messung
 */
private data class ZMeasurement(
    val z: Float,
    val timestamp: Long
)

/**
 * Trend-Kategorien für dZ/dt
 */
enum class Trend {
    RISING_FAST,    // > +5/s - Baby wird schnell unruhig
    RISING,         // +2 bis +5/s - Baby wird unruhig
    RISING_SLOW,    // +0.5 bis +2/s - Leichte Zunahme
    STABLE,         // -0.5 bis +0.5/s - Stabil
    FALLING_SLOW,   // -2 bis -0.5/s - Langsame Beruhigung
    FALLING,        // -5 bis -2/s - Beruhigung
    FALLING_FAST;   // < -5/s - Schnelle Beruhigung
    
    /**
     * Deutscher Display-Name
     */
    fun displayNameDe(): String = when (this) {
        RISING_FAST -> "Schnell steigend"
        RISING -> "Steigend"
        RISING_SLOW -> "Leicht steigend"
        STABLE -> "Stabil"
        FALLING_SLOW -> "Leicht fallend"
        FALLING -> "Fallend"
        FALLING_FAST -> "Schnell fallend"
    }
    
    /**
     * Ist steigender Trend?
     */
    fun isRising(): Boolean = this == RISING || this == RISING_FAST || this == RISING_SLOW
    
    /**
     * Ist fallender Trend?
     */
    fun isFalling(): Boolean = this == FALLING || this == FALLING_FAST || this == FALLING_SLOW
}

/**
 * Debug-Info für den Gradient Calculator
 */
data class GradientDebugInfo(
    val bufferSize: Int,
    val currentGradient: Float,
    val smoothedGradient: Float,
    val trend: Trend,
    val totalMeasurements: Long,
    val oldestZ: Float?,
    val newestZ: Float?
)
