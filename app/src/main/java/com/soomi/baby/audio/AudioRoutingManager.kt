package com.soomi.baby.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import com.soomi.baby.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AudioRoutingManager
 * 
 * Manages audio output device selection and audio focus for SOOMI.
 * 
 * Features:
 * - Enumerate available output devices (phone speaker, Bluetooth)
 * - Select output device (manual or auto)
 * - Handle Bluetooth connect/disconnect gracefully
 * - Manage audio focus (pause when phone call, etc.)
 * - Log routing events for debugging
 * 
 * IMPORTANT:
 * - Prefers A2DP (media audio) over SCO (call audio) for Bluetooth
 * - Falls back gracefully to phone speaker if Bluetooth disconnects
 * - Never crashes - always has a fallback
 */
class AudioRoutingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRoutingManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Current state
    private val _selectedDeviceType = MutableStateFlow(OutputDeviceType.AUTO)
    val selectedDeviceType: StateFlow<OutputDeviceType> = _selectedDeviceType.asStateFlow()
    
    private val _currentDevice = MutableStateFlow(AudioOutputDevice.PHONE_SPEAKER)
    val currentDevice: StateFlow<AudioOutputDevice> = _currentDevice.asStateFlow()
    
    private val _availableDevices = MutableStateFlow<List<AudioOutputDevice>>(listOf(AudioOutputDevice.PHONE_SPEAKER))
    val availableDevices: StateFlow<List<AudioOutputDevice>> = _availableDevices.asStateFlow()
    
    private val _audioFocusState = MutableStateFlow(AudioFocusState.NONE)
    val audioFocusState: StateFlow<AudioFocusState> = _audioFocusState.asStateFlow()
    
    private val _routingEvents = MutableStateFlow<List<AudioRoutingEvent>>(emptyList())
    val routingEvents: StateFlow<List<AudioRoutingEvent>> = _routingEvents.asStateFlow()
    
    // Audio focus request
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // Bluetooth state receiver
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false
    
    // Callback for when device changes
    var onDeviceChanged: ((AudioOutputDevice) -> Unit)? = null
    var onAudioFocusChanged: ((AudioFocusState) -> Unit)? = null
    
    init {
        refreshAvailableDevices()
        registerBluetoothReceiver()
    }
    
    /**
     * Get list of available output devices
     */
    fun refreshAvailableDevices(): List<AudioOutputDevice> {
        val devices = mutableListOf<AudioOutputDevice>()
        
        // Always add phone speaker
        devices.add(AudioOutputDevice.PHONE_SPEAKER)
        
        // Check for Bluetooth A2DP devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            
            for (device in audioDevices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                        val name = device.productName?.toString() ?: "Bluetooth Speaker"
                        devices.add(AudioOutputDevice(
                            id = device.id,
                            name = name,
                            type = OutputDeviceType.BLUETOOTH,
                            isConnected = true
                        ))
                        Log.d(TAG, "Found Bluetooth A2DP device: $name")
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        // Skip SCO (call audio) - we want A2DP only
                        Log.d(TAG, "Skipping Bluetooth SCO device")
                    }
                }
            }
        }
        
        _availableDevices.value = devices
        Log.d(TAG, "Available devices: ${devices.map { it.name }}")
        
        // Update current device based on selection type
        updateCurrentDevice()
        
        return devices
    }
    
    /**
     * Select output device type
     */
    fun selectDeviceType(type: OutputDeviceType) {
        Log.d(TAG, "Selecting device type: $type")
        _selectedDeviceType.value = type
        
        val previousDevice = _currentDevice.value
        updateCurrentDevice()
        
        if (previousDevice != _currentDevice.value) {
            logRoutingEvent(AudioRoutingEvent(
                eventType = AudioRoutingEventType.DEVICE_SELECTED,
                fromDevice = previousDevice.name,
                toDevice = _currentDevice.value.name,
                reason = "User selected $type"
            ))
            onDeviceChanged?.invoke(_currentDevice.value)
        }
    }
    
    /**
     * Update current device based on selection type and availability
     */
    private fun updateCurrentDevice() {
        val devices = _availableDevices.value
        val selectedType = _selectedDeviceType.value
        
        val newDevice = when (selectedType) {
            OutputDeviceType.PHONE_SPEAKER -> {
                devices.find { it.type == OutputDeviceType.PHONE_SPEAKER }
                    ?: AudioOutputDevice.PHONE_SPEAKER
            }
            OutputDeviceType.BLUETOOTH -> {
                devices.find { it.type == OutputDeviceType.BLUETOOTH }
                    ?: run {
                        // Bluetooth not available, fall back to phone speaker
                        Log.w(TAG, "Bluetooth selected but not available, falling back to phone speaker")
                        AudioOutputDevice.PHONE_SPEAKER
                    }
            }
            OutputDeviceType.AUTO -> {
                // Prefer Bluetooth if available, else phone speaker
                devices.find { it.type == OutputDeviceType.BLUETOOTH }
                    ?: devices.find { it.type == OutputDeviceType.PHONE_SPEAKER }
                    ?: AudioOutputDevice.PHONE_SPEAKER
            }
        }
        
        _currentDevice.value = newDevice
        Log.d(TAG, "Current device: ${newDevice.name}")
    }
    
    /**
     * Handle Bluetooth disconnection gracefully
     */
    fun handleBluetoothDisconnect(deviceName: String? = null) {
        Log.w(TAG, "Bluetooth device disconnected: $deviceName")
        
        val previousDevice = _currentDevice.value
        
        // Refresh available devices
        refreshAvailableDevices()
        
        // If we were using Bluetooth, fall back to phone speaker
        if (previousDevice.type == OutputDeviceType.BLUETOOTH) {
            logRoutingEvent(AudioRoutingEvent(
                eventType = AudioRoutingEventType.FALLBACK_TRIGGERED,
                fromDevice = previousDevice.name,
                toDevice = AudioOutputDevice.PHONE_SPEAKER.name,
                reason = "Bluetooth disconnected"
            ))
            onDeviceChanged?.invoke(_currentDevice.value)
        }
    }
    
    /**
     * Request audio focus for playback
     */
    fun requestAudioFocus(): Boolean {
        Log.d(TAG, "Requesting audio focus")
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .build()
        
        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                Log.d(TAG, "Audio focus granted")
                _audioFocusState.value = AudioFocusState.GAINED
                logRoutingEvent(AudioRoutingEvent(
                    eventType = AudioRoutingEventType.AUDIO_FOCUS_GAINED,
                    reason = "Focus request granted"
                ))
                onAudioFocusChanged?.invoke(AudioFocusState.GAINED)
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.d(TAG, "Audio focus delayed")
                false
            }
            else -> {
                Log.w(TAG, "Audio focus request failed: $result")
                false
            }
        }
    }
    
    /**
     * Abandon audio focus
     */
    fun abandonAudioFocus() {
        Log.d(TAG, "Abandoning audio focus")
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        _audioFocusState.value = AudioFocusState.NONE
    }
    
    /**
     * Handle audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        val newState = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                logRoutingEvent(AudioRoutingEvent(
                    eventType = AudioRoutingEventType.AUDIO_FOCUS_GAINED,
                    reason = "Focus regained"
                ))
                AudioFocusState.GAINED
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently")
                logRoutingEvent(AudioRoutingEvent(
                    eventType = AudioRoutingEventType.AUDIO_FOCUS_LOST,
                    reason = "Permanent loss (e.g., phone call)"
                ))
                AudioFocusState.LOST_PERMANENT
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
                logRoutingEvent(AudioRoutingEvent(
                    eventType = AudioRoutingEventType.AUDIO_FOCUS_LOST,
                    reason = "Transient loss"
                ))
                AudioFocusState.LOST_TRANSIENT
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus ducked")
                AudioFocusState.DUCKED
            }
            else -> _audioFocusState.value
        }
        
        _audioFocusState.value = newState
        onAudioFocusChanged?.invoke(newState)
    }
    
    /**
     * Register Bluetooth state receiver
     */
    private fun registerBluetoothReceiver() {
        if (isReceiverRegistered) return
        
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        Log.d(TAG, "Bluetooth device connected")
                        refreshAvailableDevices()
                        logRoutingEvent(AudioRoutingEvent(
                            eventType = AudioRoutingEventType.DEVICE_CONNECTED,
                            toDevice = "Bluetooth"
                        ))
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        handleBluetoothDisconnect(device?.name)
                        logRoutingEvent(AudioRoutingEvent(
                            eventType = AudioRoutingEventType.DEVICE_DISCONNECTED,
                            fromDevice = device?.name ?: "Bluetooth"
                        ))
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        Log.d(TAG, "A2DP connection state changed: $state")
                        refreshAvailableDevices()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        
        try {
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Bluetooth receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver", e)
        }
    }
    
    /**
     * Log a routing event
     */
    private fun logRoutingEvent(event: AudioRoutingEvent) {
        val events = _routingEvents.value.toMutableList()
        events.add(event)
        // Keep only last 50 events
        if (events.size > 50) {
            events.removeAt(0)
        }
        _routingEvents.value = events
        Log.d(TAG, "Routing event: ${event.eventType} - ${event.reason}")
    }
    
    /**
     * Get a summary of current audio state for display
     */
    fun getAudioStateSummary(): String {
        val device = _currentDevice.value
        val focus = _audioFocusState.value
        return "Output: ${device.name}, Focus: ${focus.name}"
    }
    
    /**
     * Check if Bluetooth is available
     */
    fun isBluetoothAvailable(): Boolean {
        return _availableDevices.value.any { it.type == OutputDeviceType.BLUETOOTH }
    }
    
    /**
     * Play a test sound to verify output
     */
    fun playTestSound(audioOutputEngine: AudioOutputEngine) {
        Log.d(TAG, "Playing test sound on: ${_currentDevice.value.name}")
        // This will be called from the UI with the actual AudioOutputEngine
    }
    
    /**
     * Release resources
     */
    fun release() {
        abandonAudioFocus()
        
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister Bluetooth receiver", e)
            }
        }
        
        Log.d(TAG, "AudioRoutingManager released")
    }
}
