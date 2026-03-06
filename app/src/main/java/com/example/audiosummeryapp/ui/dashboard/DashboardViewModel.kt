package com.example.audiosummeryapp.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiosummeryapp.model.RecordingSession
import com.example.audiosummeryapp.services.AudioChunkManager
import com.example.audiosummeryapp.services.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<RecordingSession>>(emptyList())
    val sessions: StateFlow<List<RecordingSession>> = _sessions.asStateFlow()

    init {
        // Load existing completed sessions on first open
        loadSessions()

        // Refresh whenever the service signals a new session is complete
        viewModelScope.launch {
            RecordingService.sessionCompleted.collect {
                loadSessions()
            }
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = scanCompletedSessions()
        }
    }


    //Scans audio_chunks/ for session subfolders (session_YYYYMMDD_HHmmss).
    // Each subfolder is one completed recording.

    private suspend fun scanCompletedSessions(): List<RecordingSession> =
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, "audio_chunks")
            if (!root.exists()) return@withContext emptyList()

            root.listFiles { f -> f.isDirectory && f.name.startsWith("session_") }
                ?.mapNotNull { folder -> buildSession(folder) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        }

    private fun buildSession(folder: File): RecordingSession? {
        val wavFiles = folder.listFiles { f -> f.extension == "wav" }
            ?.sortedBy { it.name }
            ?: return null
        if (wavFiles.isEmpty()) return null


        val createdAt = try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .parse(folder.name.removePrefix("session_"))?.time
                ?: folder.lastModified()
        } catch (e: Exception) {
            folder.lastModified()
        }

        val displayName = "Recording · " +
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(Date(createdAt))

        return RecordingSession(
            id = folder.name,
            displayName = displayName,
            chunkFiles = wavFiles,
            createdAt = createdAt,
            durationSecs = wavFiles.size * AudioChunkManager.CHUNK_DURATION_SECONDS
        )
    }
}