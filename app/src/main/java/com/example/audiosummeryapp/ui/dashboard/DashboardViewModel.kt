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

    /**
     * Scans context.filesDir/audio_chunks for WAV files.
     * Currently all chunks live in a flat folder — each "session" is
     * grouped by a shared timestamp prefix we stamp on filenames.
     *
     * File naming convention used by AudioChunkManager:
     *   chunk_0000.wav, chunk_0001.wav, …
     *
     * Since there's only one flat folder right now, we treat the entire
     * folder as ONE session per recording run. When Room is added later,
     * replace this logic with a simple DAO query.
     */
    private suspend fun scanCompletedSessions(): List<RecordingSession> =
        withContext(Dispatchers.IO) {
            val chunksDir = File(context.filesDir, "audio_chunks")
            if (!chunksDir.exists()) return@withContext emptyList()

            // Group files by session prefix  (e.g. "session_20240510_143022")
            // For now AudioChunkManager uses a flat structure, so we group by
            // the recording run stored in a simple sessions index file we write
            // at stop time. If no index exists yet, fall back to a single group.
            val indexFile = File(context.filesDir, "sessions_index.txt")

            if (indexFile.exists()) {
                // Each line: sessionId|startEpoch|chunkCount|displayName
                indexFile.readLines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line -> parseSessionLine(line, chunksDir) }
                    .sortedByDescending { it.createdAt }
            } else {
                // Fallback: treat all WAVs as one session
                val wavFiles = chunksDir.listFiles { f -> f.extension == "wav" }
                    ?.sortedBy { it.name }
                    ?: return@withContext emptyList()

                if (wavFiles.isEmpty()) return@withContext emptyList()

                val created = wavFiles.first().lastModified()
                listOf(
                    RecordingSession(
                        id           = "session_legacy",
                        displayName  = formatDisplayName(created),
                        chunkFiles   = wavFiles,
                        createdAt    = created,
                        durationSecs = wavFiles.size * AudioChunkManager.CHUNK_DURATION_SECONDS
                    )
                )
            }
        }

    private fun parseSessionLine(line: String, chunksDir: File): RecordingSession? {
        return try {
            val parts = line.split("|")
            val sessionId   = parts[0]
            val startEpoch  = parts[1].toLong()
            val chunkCount  = parts[2].toInt()
            val displayName = parts[3]

            val files = (0 until chunkCount).mapNotNull { idx ->
                val f = File(chunksDir, "${sessionId}_chunk_${idx.toString().padStart(4, '0')}.wav")
                if (f.exists()) f else null
            }
            if (files.isEmpty()) return null

            RecordingSession(
                id           = sessionId,
                displayName  = displayName,
                chunkFiles   = files,
                createdAt    = startEpoch,
                durationSecs = files.size * AudioChunkManager.CHUNK_DURATION_SECONDS
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDisplayName(epoch: Long): String =
        "Recording · " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epoch))
}