package com.example.audiosummeryapp.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.TreeMap
import java.util.concurrent.TimeUnit

/**
 * Manages transcription of audio chunks via OpenAI Whisper.
 *
 * Strategy:
 * - Each chunk is transcribed concurrently (parallel HTTP calls)
 * - Results are held in a TreeMap keyed by chunk index
 * - Transcript is flushed to disk IN ORDER as soon as all preceding
 *   chunks are available — so chunk 3 never writes before chunk 2.
 *
 * Output: <sessionFolder>/transcript.txt  (appended in order)
 */
class TranscriptionManager(
    private val sessionFolder    : File,
    private val apiKey           : String,
    private val onTranscriptReady: (suspend (File) -> Unit)? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    companion object {
        private const val TAG             = "TranscriptionManager"
        private const val WHISPER_URL     = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL           = "whisper-1"
        const val TRANSCRIPT_FILENAME     = "transcript.txt"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Ordered buffer: chunkIndex → transcript text
    private val pendingResults = TreeMap<Int, String>()
    private val mutex          = Mutex()

    // Track the next index we should write to disk
    private var nextWriteIndex = 0

    // Track how many chunks have been enqueued vs completed
    private var enqueuedCount  = 0
    private var completedCount = 0

    val transcriptFile: File get() = File(sessionFolder, TRANSCRIPT_FILENAME)

    /**
     * Enqueue a chunk for transcription. Launches immediately on IO dispatcher.
     * Order is preserved via [pendingResults] + [nextWriteIndex].
     */
    fun enqueueChunk(chunkFile: File, chunkIndex: Int) {
        enqueuedCount++
        scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Transcribing chunk $chunkIndex: ${chunkFile.name}")
            val text = transcribeWithRetry(chunkFile, chunkIndex)
            if (text != null) {
                flushOrdered(chunkIndex, text)
            } else {
                Log.e(TAG, "Chunk $chunkIndex transcription failed after retries")
            }
            completedCount++
            if (completedCount == enqueuedCount) {
                Log.d(TAG, "All chunks transcribed — transcript complete")
                onTranscriptReady?.invoke(transcriptFile)
            }
        }
    }


    // Core logic
    private suspend fun flushOrdered(chunkIndex: Int, text: String) {
        mutex.withLock {
            pendingResults[chunkIndex] = text

            // Write every chunk that is now available in order
            while (pendingResults.containsKey(nextWriteIndex)) {
                val line = pendingResults.remove(nextWriteIndex) ?: break
                appendToTranscript(nextWriteIndex, line)
                nextWriteIndex++
            }
        }
    }

    private fun appendToTranscript(index: Int, text: String) {
        try {
            // Append with a newline separator between chunks
            val separator = if (transcriptFile.exists() && transcriptFile.length() > 0) "\n" else ""
            transcriptFile.appendText(separator + text.trim())
            Log.d(TAG, "Transcript chunk $index saved (${text.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write transcript chunk $index", e)
        }
    }

    // Whisper API
    private suspend fun transcribeWithRetry(
        file      : File,
        chunkIndex: Int,
        maxRetries: Int = 2
    ): String? {
        repeat(maxRetries) { attempt ->
            try {
                val result = callWhisperApi(file)
                if (result != null) return result
            } catch (e: Exception) {
                Log.w(TAG, "Chunk $chunkIndex attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(2_000L * (attempt + 1)) // 2s backoff
                }
            }
        }
        return null
    }

    private fun callWhisperApi(file: File): String? {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", MODEL)
//            .addFormDataPart("language", "en")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url(WHISPER_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "Whisper API error ${response.code}: $body")
                return null
            }
            return JSONObject(body ?: return null).getString("text")
        }
    }
}