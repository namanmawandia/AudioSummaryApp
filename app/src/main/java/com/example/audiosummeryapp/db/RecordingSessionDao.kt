package com.example.audiosummeryapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {

    // ── Observe ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM recording_sessions WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun observeCompletedSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<RecordingSessionEntity?>

    //One-shot direct fetch,does not use Flow, safe to call from suspend functions.
    @Query("SELECT * FROM recording_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): RecordingSessionEntity?


    //Write
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecordingSessionEntity)

    @Update
    suspend fun update(session: RecordingSessionEntity)

    /** Called when recording stops — mark complete and set final chunk count + duration. */
    @Query("""
        UPDATE recording_sessions 
        SET status = 'COMPLETED', chunkCount = :chunkCount, durationSecs = :durationSecs
        WHERE id = :id
    """)
    suspend fun markCompleted(id: String, chunkCount: Int, durationSecs: Int)

    // Transcript
    @Query("UPDATE recording_sessions SET transcriptPath = :path, transcriptError = NULL WHERE id = :id")
    suspend fun setTranscriptPath(id: String, path: String)

    @Query("UPDATE recording_sessions SET transcriptError = :error WHERE id = :id")
    suspend fun setTranscriptError(id: String, error: String)

    // Summary
    @Query("UPDATE recording_sessions SET summaryJson = :json, summaryError = NULL WHERE id = :id")
    suspend fun setSummaryJson(id: String, json: String)

    @Query("UPDATE recording_sessions SET summaryError = :error, summaryJson = NULL WHERE id = :id")
    suspend fun setSummaryError(id: String, error: String)


    //Cleanup
    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun delete(id: String)

    /** Remove stale RECORDING rows on app restart (service was killed mid-recording). */
    @Query("DELETE FROM recording_sessions WHERE status = 'RECORDING'")
    suspend fun deleteStaleRecordingSessions()
}