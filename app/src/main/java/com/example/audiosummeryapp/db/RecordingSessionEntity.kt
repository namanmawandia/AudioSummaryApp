package com.example.audiosummeryapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SessionStatus { RECORDING, COMPLETED, ERROR }

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey
    val id                : String,         // session folder name e.g. "session_20260306_004130"
    val displayName       : String,         // "Recording · Mar 6, 12:41 AM"
    val createdAt         : Long,           // epoch ms
    val durationSecs      : Int,            // updated on COMPLETED
    val sessionFolderPath : String,         // absolute path to session folder
    val status            : SessionStatus  = SessionStatus.RECORDING,
    val chunkCount        : Int            = 0,
    val transcriptPath    : String?        = null,   // set when transcript.txt is ready
    val transcriptError   : String?       = null,
    val summaryJson       : String?       = null,   // JSON: {title, summary, actionItems[], keyPoints[]}
    val summaryError      : String?       = null
)