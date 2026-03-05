package com.example.audiosummeryapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI State

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

data class RecordingUiState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val elapsedSeconds: Int = 0,
    val statusMessage: String = "Tap to start recording",
    val amplitudeLevel: Float = 0f
)

//ViewModel

@HiltViewModel
class RecordingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun startRecording() {
        _uiState.update {
            it.copy(
                status = RecordingStatus.RECORDING,
                statusMessage = "Recording...",
                elapsedSeconds = 0
            )
        }
        startTimer()
        // TODO: start RecordingService via Intent
    }

    fun pauseRecording() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                status = RecordingStatus.PAUSED,
                statusMessage = "Paused"
            )
        }
        // TODO: send pause command to RecordingService
    }

    fun resumeRecording() {
        _uiState.update {
            it.copy(
                status = RecordingStatus.RECORDING,
                statusMessage = "Recording..."
            )
        }
        startTimer()
        // TODO: send resume command to RecordingService
    }

    fun stopRecording() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                status = RecordingStatus.STOPPED,
                statusMessage = "Stopped"
            )
        }
        // TODO: send stop command to RecordingService
    }

    // Internal helpers

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    // Simulate amplitude pulses for waveform (will be replaced by real AudioRecord data)
    fun updateAmplitude(level: Float) {
        _uiState.update { it.copy(amplitudeLevel = level) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}