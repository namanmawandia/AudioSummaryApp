package com.example.audiosummeryapp.services

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import com.example.audiosummeryapp.db.SessionRepository
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// Service State

enum class ServiceRecordingStatus {
    IDLE, RECORDING, PAUSED_CALL, PAUSED_FOCUS, STOPPED, ERROR
}

data class ServiceState(
    val status        : ServiceRecordingStatus = ServiceRecordingStatus.IDLE,
    val elapsedSeconds: Int                    = 0,
    val statusMessage : String                 = "",
    val amplitudeLevel: Float                  = 0f,
    val errorMessage  : String?                = null
)

//RecordingService

@AndroidEntryPoint
class RecordingService : LifecycleService() {

    companion object {
        private const val TAG = "RecordingService"

        // Intent actions (UI → Service)
        const val ACTION_START  = "action_start"
        const val ACTION_PAUSE  = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_STOP   = "action_stop"

        // Shared state observed by ViewModel
        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        private val _sessionCompleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val sessionCompleted: SharedFlow<String> = _sessionCompleted.asSharedFlow()

        fun resetState() {
            _serviceState.value = ServiceState()   // default = IDLE, 0 elapsed, empty message
        }
    }

    // Injecting dependencies
     @Inject
     lateinit var sessionRepository: SessionRepository

    // System Services
    private lateinit var notificationManager : NotificationManager
    private lateinit var audioManager        : AudioManager
    private lateinit var telephonyManager    : TelephonyManager

    //Core components
    private lateinit var chunkManager : AudioChunkManager
    private val telephonyExecutor     = Executors.newSingleThreadExecutor()
    private var transcriptionManager: TranscriptionManager? = null

    // Timer
    private var elapsedSeconds = 0

    //field to track session id
    private var currentSessionId: String = ""
    private var chunkCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager        = getSystemService(AudioManager::class.java)
        telephonyManager    = getSystemService(TelephonyManager::class.java)

        RecordingNotificationManager.createChannel(this)
        initChunkManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForeground(
            RecordingNotificationManager.NOTIFICATION_ID,
            RecordingNotificationManager.buildRecordingNotification(this, "00:00")
        )

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_RESUME,
            RecordingNotificationManager.ACTION_RESUME -> handleResume()
            ACTION_PAUSE,
            RecordingNotificationManager.ACTION_PAUSE  -> handlePause("Paused")
            ACTION_STOP,
            RecordingNotificationManager.ACTION_STOP   -> handleStop()
            else -> handleStart()
        }
        return START_STICKY   // restart if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        chunkManager.stopRecording()
        releaseAudioFocus()
        unregisterPhoneStateListener()
        unregisterAudioDeviceCallback()
        telephonyExecutor.shutdown()
        Log.d(TAG, "Service destroyed")
    }

    //Command Handlers

    private fun handleStart() {
        if (!hasEnoughStorage()) {
            emitError("Recording stopped - Low storage")
            stopSelf()
            return
        }
        requestAudioFocus()
        registerPhoneStateListener()
        registerAudioDeviceCallback()

        chunkManager.startRecording(lifecycleScope)

        // OpenAI key provided here; it will be deleted automatically after 48 hrs of uploading the app
        val sessionFolder = chunkManager.sessionFolder
        if (sessionFolder != null) {
            currentSessionId = sessionFolder.name
            lifecycleScope.launch {
                sessionRepository.createSession(sessionFolder)
            }
            transcriptionManager = TranscriptionManager(
                sessionFolder = sessionFolder,
                apiKey        = "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",
                onTranscriptReady = { file ->
                    CoroutineScope(Dispatchers.IO).launch {
                        sessionRepository.setTranscriptReady(currentSessionId, file)
                    }
                        Log.d(TAG, "Transcript path saved to Room: ${file.absolutePath}")
                }
            )
        }

        chunkCount = 0
        emitStatus(ServiceRecordingStatus.RECORDING, "Recording...")
        startTimer()
    }

    private fun handlePause(reason: String) {
        chunkManager.pauseCapture()
        releaseAudioFocus()
        val notification = RecordingNotificationManager.buildPausedNotification(this, reason)
        notificationManager.notify(RecordingNotificationManager.NOTIFICATION_ID, notification)
        emitStatus(
            if (reason.contains("call", ignoreCase = true))
                ServiceRecordingStatus.PAUSED_CALL
            else
                ServiceRecordingStatus.PAUSED_FOCUS,
            reason
        )
    }

    private fun handleResume() {
        if (!hasEnoughStorage()) {
            emitError("Recording stopped - Low storage")
            stopSelf()
            return
        }
        requestAudioFocus()
        chunkManager.resumeCapture()
        emitStatus(ServiceRecordingStatus.RECORDING, "Recording...")
        startTimer()
    }

    private fun handleStop() {
        chunkManager.stopRecording()
        releaseAudioFocus()
        unregisterPhoneStateListener()
        unregisterAudioDeviceCallback()

        emitStatus(ServiceRecordingStatus.STOPPED, "Stopped")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        lifecycleScope.launch {
            if (currentSessionId.isNotEmpty()) {
                sessionRepository.completeSession(currentSessionId, chunkCount)
            }
            stopSelf()
        }
    }

    // Timer
    private fun startTimer() {
        lifecycleScope.launch {
            while (isActive && _serviceState.value.status == ServiceRecordingStatus.RECORDING) {
                delay(1_000)
                elapsedSeconds++
                val timeString = formatTime(elapsedSeconds)
                _serviceState.update { it.copy(elapsedSeconds = elapsedSeconds) }
                // Keep notification timer in sync
                notificationManager.notify(
                    RecordingNotificationManager.NOTIFICATION_ID,
                    RecordingNotificationManager.buildRecordingNotification(this@RecordingService, timeString)
                )
            }
        }
    }

    // Phone Call Handling
    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    private fun registerPhoneStateListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE not granted — phone call detection disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(telephonyExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            legacyPhoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyPhoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallState(state: Int) {
        val currentStatus = _serviceState.value.status
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (currentStatus == ServiceRecordingStatus.RECORDING) {
                    Log.d(TAG, "Phone call started — pausing")
                    handlePause("Paused - Phone call")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (currentStatus == ServiceRecordingStatus.PAUSED_CALL) {
                    Log.d(TAG, "Phone call ended — resuming")
                    handleResume()
                }
            }
        }
    }

    // Audio Focus Handling
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_serviceState.value.status == ServiceRecordingStatus.RECORDING) {
                    Log.d(TAG, "Audio focus lost — pausing")
                    handlePause("Paused - Audio focus lost")
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (_serviceState.value.status == ServiceRecordingStatus.PAUSED_FOCUS) {
                    Log.d(TAG, "Audio focus regained — resuming")
                    handleResume()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    // Audio Device changes
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val device = addedDevices.firstOrNull() ?: return
            val message = when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset connected"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headset connected"
                else -> return
            }
            Log.d(TAG, message)
            showSourceChangedNotification(message)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val device = removedDevices.firstOrNull() ?: return
            val message = when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset disconnected"
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headset disconnected"
                else -> return
            }
            Log.d(TAG, message)
            showSourceChangedNotification(message)
        }
    }

    private fun registerAudioDeviceCallback() {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    private fun unregisterAudioDeviceCallback() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    private fun showSourceChangedNotification(message: String) {
        notificationManager.notify(
            RecordingNotificationManager.NOTIFICATION_ID,
            RecordingNotificationManager.buildSourceChangedNotification(this, message)
        )
    }

    // Chunk Manager Initilization
    private fun initChunkManager() {
        chunkManager = AudioChunkManager(
            context            = this,
            onChunkReady       = { file, index -> handleChunkReady(file, index) },
            onAmplitudeUpdate  = { level ->
                _serviceState.update { it.copy(amplitudeLevel = level) }
            },
            onSilenceDetected  = {
                Log.w(TAG, "Silence detected")
                notificationManager.notify(
                    RecordingNotificationManager.NOTIFICATION_ID,
                    RecordingNotificationManager.buildSourceChangedNotification(
                        this, "No audio detected - Check microphone"
                    )
                )
            },
            onStorageLow = {
                emitError("Recording stopped - Low storage")
                handleStop()
            }
        )
    }

    private fun handleChunkReady(file: File, index: Int) {
        Log.d(TAG, "Chunk $index ready: ${file.name}")
        chunkCount = index + 1
        transcriptionManager?.enqueueChunk(file, index)
    }

    //State Helpers
    private fun emitStatus(status: ServiceRecordingStatus, message: String) {
        _serviceState.update { it.copy(status = status, statusMessage = message) }
    }

    private fun emitError(message: String) {
        _serviceState.update {
            it.copy(
                status       = ServiceRecordingStatus.ERROR,
                statusMessage = message,
                errorMessage  = message
            )
        }
    }

    private fun hasEnoughStorage(): Boolean {
        return filesDir.freeSpace > AudioChunkManager.LOW_STORAGE_BYTES
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
