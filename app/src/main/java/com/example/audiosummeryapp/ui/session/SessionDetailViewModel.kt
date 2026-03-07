package com.example.audiosummeryapp.ui.session

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiosummeryapp.db.SessionRepository
import com.example.audiosummeryapp.db.RecordingSessionEntity
import com.example.audiosummeryapp.services.TranscriptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

//Summary data model

data class SummaryData(
    val title      : String,
    val summary    : String,
    val actionItems: List<String>,
    val keyPoints  : List<String>
)

// UI states
sealed class TranscriptUiState {
    object Loading   : TranscriptUiState()
    data class Ready (val text: String)  : TranscriptUiState()
    data class Error (val message: String) : TranscriptUiState()
    object Empty     : TranscriptUiState()   // no transcript yet (still transcribing)
}

sealed class SummaryUiState {
    object Idle        : SummaryUiState()   // not yet requested
    object Generating  : SummaryUiState()
    data class Streaming(val partialText: String) : SummaryUiState()
    data class Ready   (val data: SummaryData)    : SummaryUiState()
    data class Error   (val message: String)      : SummaryUiState()
}

//ViewModel
private const val TAG = "SessionDetailVM"

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val repository     : SessionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    // Live session from Room
    val session: StateFlow<RecordingSessionEntity?> = repository
        .observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _transcriptState = MutableStateFlow<TranscriptUiState>(TranscriptUiState.Loading)
    val transcriptState: StateFlow<TranscriptUiState> = _transcriptState.asStateFlow()

    private val _summaryState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val summaryState: StateFlow<SummaryUiState> = _summaryState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey = "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"

    init {
        // Watch session and load transcript state whenever it changes
        viewModelScope.launch {
            session.collect { entity ->
                entity ?: return@collect
                loadTranscriptState(entity)
                // If summary is already in DB, restore it
                if (_summaryState.value is SummaryUiState.Idle) {
                    restoreSummaryFromDb(entity)
                }
            }
        }
    }

    //Transcript

    private fun loadTranscriptState(entity: RecordingSessionEntity) {
        when {
            entity.transcriptError != null -> {
                _transcriptState.value = TranscriptUiState.Error(entity.transcriptError)
            }
            entity.transcriptPath != null -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val text = try {
                        File(entity.transcriptPath).readText().trim()
                    } catch (e: Exception) {
                        null
                    }
                    _transcriptState.value = if (text.isNullOrEmpty())
                        TranscriptUiState.Error("Transcript file is empty or unreadable")
                    else
                        TranscriptUiState.Ready(text)
                }
            }
            else -> {
                _transcriptState.value = TranscriptUiState.Empty
            }
        }
    }

    // Re-transcribe all chunks for this session. Called from Retry button.
    fun retryTranscription() {
        val entity = session.value ?: return
        _transcriptState.value = TranscriptUiState.Loading

        val sessionFolder = File(entity.sessionFolderPath)
        val chunkFiles = sessionFolder
            .listFiles { f -> f.extension == "wav" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (chunkFiles.isEmpty()) {
            _transcriptState.value = TranscriptUiState.Error("No audio chunks found")
            return
        }

        // Clear old transcript and error from DB so loadTranscriptState
        // doesn't re-show stale data while new transcription runs
        viewModelScope.launch {
            repository.setTranscriptError(sessionId, "")   // clear error
        }

        // Delete old transcript file
        File(sessionFolder, TranscriptionManager.TRANSCRIPT_FILENAME).delete()

        val manager = TranscriptionManager(
            sessionFolder     = sessionFolder,
            apiKey            = apiKey,
            onTranscriptReady = { file ->
                //Must launch a coroutine — onTranscriptReady is a plain lambda,
                // not a suspend context. Without this the DB never updates and
                // the screen stays on Loading forever.
                viewModelScope.launch {
                    repository.setTranscriptReady(sessionId, file)
                }
            }
        )
        chunkFiles.forEachIndexed { index, file ->
            manager.enqueueChunk(file, index)
        }
    }

    // Summary
    private fun restoreSummaryFromDb(entity: RecordingSessionEntity) {
        when {
            entity.summaryJson != null -> {
                val data = parseSummaryJson(entity.summaryJson)
                if (data != null) _summaryState.value = SummaryUiState.Ready(data)
                else _summaryState.value = SummaryUiState.Error("Stored summary is malformed")
            }
            entity.summaryError != null -> {
                _summaryState.value = SummaryUiState.Error(entity.summaryError)
            }
        }
    }

    // Called when user taps the Summary tab for the first time, or hits Retry.
    fun generateSummary(forceRegenerate: Boolean = false) {
        val currentState = _summaryState.value
        // Don't re-generate if already done — unless forced (Retry)
        if (!forceRegenerate && currentState is SummaryUiState.Ready) return
        if (currentState is SummaryUiState.Generating) return

        val transcript = (transcriptState.value as? TranscriptUiState.Ready)?.text
        if (transcript.isNullOrBlank()) {
            _summaryState.value = SummaryUiState.Error("Transcript not ready yet")
            return
        }

        _summaryState.value = SummaryUiState.Generating

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summaryData = callOpenAiForSummary(transcript)
                val json = summaryData.toJson()
                repository.setSummary(sessionId, json)
                _summaryState.value = SummaryUiState.Ready(summaryData)
            } catch (e: Exception) {
                Log.e(TAG, "Summary generation failed", e)
                val msg = e.message ?: "Unknown error"
                repository.setSummaryError(sessionId, msg)
                _summaryState.value = SummaryUiState.Error(msg)
            }
        }
    }

    //OpenAI summary API

    private fun callOpenAiForSummary(transcript: String): SummaryData {
        val systemPrompt = """
            You are a meeting summarizer. Given a transcript, return ONLY a JSON object with:
            {
              "title": "short descriptive title",
              "summary": "2-3 sentence overview",
              "actionItems": ["item1", "item2"],
              "keyPoints": ["point1", "point2", "point3"]
            }
            Return ONLY the JSON. No markdown, no explanation.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", transcript)
                })
            })
            put("max_tokens", 1000)
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("API error ${response.code}: $errorBody")
            }
            val responseJson = JSONObject(response.body!!.string())
            val content = responseJson
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            return parseSummaryJson(content)
                ?: throw IOException("Could not parse summary response: $content")
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun parseSummaryJson(json: String): SummaryData? {
        return try {
            // Strip markdown code fences if model wraps response
            val clean = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = JSONObject(clean)
            SummaryData(
                title       = obj.optString("title", "Untitled"),
                summary     = obj.optString("summary", ""),
                actionItems = obj.optJSONArray("actionItems")?.toStringList() ?: emptyList(),
                keyPoints   = obj.optJSONArray("keyPoints")?.toStringList()   ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse summary JSON", e)
            null
        }
    }

    private fun SummaryData.toJson(): String = JSONObject().apply {
        put("title", title)
        put("summary", summary)
        put("actionItems", JSONArray(actionItems))
        put("keyPoints", JSONArray(keyPoints))
    }.toString()

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}