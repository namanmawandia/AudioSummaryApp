package com.example.audiosummeryapp.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingSession(
    val id          : String,
    val displayName : String,
    val chunkFiles  : List<File>,
    val createdAt   : Long,
    val durationSecs: Int
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            .format(Date(createdAt))

    val formattedDuration: String
        get() {
            val m = durationSecs / 60
            val s = durationSecs % 60
            return if (m > 0) "${m}m ${s}s" else "${s}s"
        }

    val chunkCount: Int get() = chunkFiles.size
}