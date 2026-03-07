package com.example.audiosummeryapp.db

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dao: RecordingSessionDao
) {
    //Observe
    fun observeCompletedSessions(): Flow<List<RecordingSessionEntity>> =
        dao.observeCompletedSessions()

    fun observeSession(id: String): Flow<RecordingSessionEntity?> =
        dao.observeSession(id)

    //Write
    suspend fun createSession(sessionFolder: File): RecordingSessionEntity {
        val timestamp = sessionFolder.name.removePrefix("session_")
        val createdAt = try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val displayName = "Recording · " +
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(Date(createdAt))

        val entity = RecordingSessionEntity(
            id                = sessionFolder.name,
            displayName       = displayName,
            createdAt         = createdAt,
            durationSecs      = 0,
            sessionFolderPath = sessionFolder.absolutePath,
            status            = SessionStatus.RECORDING
        )
        dao.insert(entity)
        return entity
    }

    // Called from handleStop() — finalises the session in DB
    suspend fun completeSession(sessionId: String, chunkCount: Int) {
        val durationSecs = chunkCount * 30   // approx; each chunk = 30s
        dao.markCompleted(sessionId, chunkCount, durationSecs)
    }

    //Called by TranscriptionManager once the transcript file is fully written.
     suspend fun setTranscriptReady(sessionId: String, transcriptFile: File) {
        dao.setTranscriptPath(sessionId, transcriptFile.absolutePath)
    }

    suspend fun setTranscriptError(sessionId: String, error: String) {
        dao.setTranscriptError(sessionId, error)
    }

    // Summary
    suspend fun setSummary(sessionId: String, summaryJson: String) {
        dao.setSummaryJson(sessionId, summaryJson)
    }

    suspend fun setSummaryError(sessionId: String, error: String) {
        dao.setSummaryError(sessionId, error)
    }

    suspend fun cleanupStaleRecordingSessions() = dao.deleteStaleRecordingSessions()
}