package com.example.audiosummeryapp

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiosummeryapp.services.RecordingService
import com.example.audiosummeryapp.services.ServiceRecordingStatus
import com.example.audiosummeryapp.services.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

//UI State

enum class RecordingStatus {
    IDLE, RECORDING, PAUSED, STOPPED, ERROR
}

data class RecordingUiState(
    val status        : RecordingStatus = RecordingStatus.IDLE,
    val elapsedSeconds: Int             = 0,
    val statusMessage : String          = "Tap to start recording",
    val amplitudeLevel: Float           = 0f,
    val errorMessage  : String?         = null
)

//ViewModel

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Map ServiceState → RecordingUiState so the UI never imports service classes
    val uiState: StateFlow<RecordingUiState> = RecordingService.serviceState
        .map { it.toUiState() }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordingUiState()
        )

    //Public actions

    fun startRecording()  = sendCommand(RecordingService.ACTION_START)
    fun pauseRecording()  = sendCommand(RecordingService.ACTION_PAUSE)
    fun resumeRecording() = sendCommand(RecordingService.ACTION_RESUME)
    fun stopRecording()   = sendCommand(RecordingService.ACTION_STOP)

    fun resetAfterStop() {
        RecordingService.resetState()
    }

    //Private helpers

    private fun sendCommand(action: String) {
        val intent = Intent(context, RecordingService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

// Mapper: ServiceState → RecordingUiState

private fun ServiceState.toUiState() = RecordingUiState(
    status = when (status) {
        ServiceRecordingStatus.IDLE         -> RecordingStatus.IDLE
        ServiceRecordingStatus.RECORDING    -> RecordingStatus.RECORDING
        ServiceRecordingStatus.PAUSED_CALL,
        ServiceRecordingStatus.PAUSED_FOCUS -> RecordingStatus.PAUSED
        ServiceRecordingStatus.STOPPED      -> RecordingStatus.STOPPED
        ServiceRecordingStatus.ERROR        -> RecordingStatus.ERROR
    },
    elapsedSeconds = elapsedSeconds,
    statusMessage  = statusMessage.ifEmpty {
        when (status) {
            ServiceRecordingStatus.IDLE         -> "Tap to start recording"
            ServiceRecordingStatus.RECORDING    -> "Recording..."
            ServiceRecordingStatus.PAUSED_CALL  -> "Paused - Phone call"
            ServiceRecordingStatus.PAUSED_FOCUS -> "Paused - Audio focus lost"
            ServiceRecordingStatus.STOPPED      -> "Stopped"
            ServiceRecordingStatus.ERROR        -> errorMessage ?: "An error occurred"
        }
    },
    amplitudeLevel = amplitudeLevel,
    errorMessage   = errorMessage
)
