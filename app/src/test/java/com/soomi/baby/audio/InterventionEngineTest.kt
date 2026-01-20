package com.soomi.baby.audio

import com.soomi.baby.domain.model.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InterventionEngineTest {
    
    private lateinit var mockAudioOutput: AudioOutputEngine
    private lateinit var engine: InterventionEngine
    
    @Before
    fun setup() {
        mockAudioOutput = mockk(relaxed = true)
        engine = InterventionEngine(mockAudioOutput)
    }
    
    @Test
    fun `initial state is IDLE`() {
        assertEquals(SoomiState.IDLE, engine.state.value)
    }
    
    @Test
    fun `start transitions to BASELINE`() {
        every { mockAudioOutput.initialize() } returns true
        
        engine.start()
        
        assertEquals(SoomiState.BASELINE, engine.state.value)
    }
    
    @Test
    fun `stop transitions to IDLE`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        engine.stop()
        
        assertEquals(SoomiState.IDLE, engine.state.value)
    }
    
    @Test
    fun `panicStop transitions to PAUSED`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        engine.panicStop()
        
        assertEquals(SoomiState.PAUSED, engine.state.value)
        verify { mockAudioOutput.forceStop() }
    }
    
    @Test
    fun `resume from PAUSED transitions to BASELINE`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        engine.panicStop()
        
        engine.resume()
        
        assertEquals(SoomiState.BASELINE, engine.state.value)
    }
    
    @Test
    fun `low score in BASELINE stays in BASELINE`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        engine.processScore(UnrestScore(5f))
        engine.processScore(UnrestScore(8f))
        
        assertEquals(SoomiState.BASELINE, engine.state.value)
    }
    
    @Test
    fun `elevated score triggers EARLY_SMOOTH after confirmation`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        // Send elevated scores to trigger confirmation
        repeat(5) {
            engine.processScore(UnrestScore(25f))
            Thread.sleep(100)
        }
        
        assertEquals(SoomiState.EARLY_SMOOTH, engine.state.value)
    }
    
    @Test
    fun `very high score triggers CRISIS`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        // Send crisis-level score
        engine.processScore(UnrestScore(95f))
        
        assertEquals(SoomiState.CRISIS, engine.state.value)
    }
    
    @Test
    fun `manualSoothe triggers intervention`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        engine.manualSoothe()
        
        assertEquals(SoomiState.CRISIS, engine.state.value)
        verify { mockAudioOutput.setVolume(any()) }
    }
    
    @Test
    fun `setBaselineMode updates engine`() {
        every { mockAudioOutput.initialize() } returns true
        engine.start()
        
        engine.setBaselineMode(BaselineMode.MEDIUM)
        
        // Should apply medium baseline volume
        verify { mockAudioOutput.setVolume(InterventionLevel.LEVEL_2) }
    }
    
    @Test
    fun `config can be updated`() {
        val newConfig = ThresholdConfig(
            zEarlyThreshold = 25f,
            zCrisisThreshold = 90f
        )
        
        engine.config = newConfig
        
        assertEquals(25f, engine.config.zEarlyThreshold, 0.01f)
        assertEquals(90f, engine.config.zCrisisThreshold, 0.01f)
    }
}
