package com.example.audiosummeryapp.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class AudioChunkManager(
    private val context: Context,
    private val onChunkReady: (File, Int) -> Unit,
    private val onAmplitudeUpdate: (Float) -> Unit,
    private val onSilenceDetected: () -> Unit,
    private val onStorageLow: () -> Unit
) {
    companion object {
        private const val TAG               = "AudioChunkManager"
        const val SAMPLE_RATE               = 16_000
        const val CHANNEL_CONFIG            = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT              = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE          = 2
        const val CHUNK_DURATION_SECONDS    = 30
        const val OVERLAP_DURATION_SECONDS  = 2
        const val SILENCE_THRESHOLD_SECONDS = 10
        const val LOW_STORAGE_BYTES         = 50L * 1024 * 1024
        private const val SILENCE_RMS_THRESHOLD = 150f
    }

    // Audio config
    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ).let { if (it == AudioRecord.ERROR_BAD_VALUE) 4096 else it }

    // 30 seconds of PCM audio
    private val chunkBytes   = SAMPLE_RATE * BYTES_PER_SAMPLE * CHUNK_DURATION_SECONDS
    // 2 seconds of PCM (overlap)
    private val overlapBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * OVERLAP_DURATION_SECONDS

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job?        = null
    private var chunkIndex                = 0

    // Silence tracking
    private var silentFrameCount  = 0
    private val silentFrameLimit  = (SAMPLE_RATE * SILENCE_THRESHOLD_SECONDS)   // samples

    // Overlap buffer: tail of previous chunk prepended to next
    private var overlapBuffer: ByteArray? = null

    //Public API
    fun startRecording(scope: CoroutineScope) {
        if (audioRecord != null) return
        chunkIndex   = 0
        overlapBuffer = null
        silentFrameCount = 0
        initAudioRecord()
        audioRecord?.startRecording()

        recordingJob = scope.launch(Dispatchers.IO) {
            captureLoop()
        }
        Log.d(TAG, "Recording started")
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Recording stopped")
    }

    // Call when service pauses (phone call, focus loss)
    fun pauseCapture() {
        audioRecord?.stop()
        Log.d(TAG, "Capture paused")
    }

    // Call when service resumes
    fun resumeCapture() {
        audioRecord?.startRecording()
        silentFrameCount = 0
        Log.d(TAG, "Capture resumed")
    }

    //Core capture loop

    private suspend fun captureLoop() {
        val readBuffer = ByteArray(minBufferSize)

        val chunkAccumulator = ArrayList<Byte>(chunkBytes + overlapBytes)

        // Prepend overlap from previous chunk if any
        overlapBuffer?.let { chunkAccumulator.addAll(it.toList()) }

        while (recordingJob?.isActive == true) {
            val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: break
            if (bytesRead <= 0) continue

            // Check storage before writing
            if (!hasEnoughStorage()) {
                onStorageLow()
                break
            }

            // Amplitude / silence detection
            val rms = calculateRms(readBuffer, bytesRead)
            val normalizedAmplitude = (rms / 10_000f).coerceIn(0f, 1f)
            onAmplitudeUpdate(normalizedAmplitude)

            if (rms < SILENCE_RMS_THRESHOLD) {
                silentFrameCount += bytesRead / BYTES_PER_SAMPLE
                if (silentFrameCount >= silentFrameLimit) {
                    onSilenceDetected()
                    silentFrameCount = 0   // reset so we don't spam
                }
            } else {
                silentFrameCount = 0
            }

            // Accumulate bytes
            chunkAccumulator.addAll(readBuffer.take(bytesRead))

            //  Emit chunk when we have 30 seconds of audio
            if (chunkAccumulator.size >= chunkBytes) {
                val chunkData = chunkAccumulator.take(chunkBytes).toByteArray()

                // Save last 2 seconds as overlap for the next chunk
                overlapBuffer = chunkData.takeLast(overlapBytes).toByteArray()

                // Write WAV file
                val chunkFile = writeWavFile(chunkData, chunkIndex)
                onChunkReady(chunkFile, chunkIndex)
                chunkIndex++

                // Reset accumulator, seed it with the overlap
                chunkAccumulator.clear()
                overlapBuffer?.let { chunkAccumulator.addAll(it.toList()) }
            }
        }

        // Flush any remaining audio as the final partial chunk
        if (chunkAccumulator.isNotEmpty()) {
            val remaining = chunkAccumulator.toByteArray()
            val chunkFile = writeWavFile(remaining, chunkIndex)
            onChunkReady(chunkFile, chunkIndex)
            chunkIndex++
        }
    }

    // WAV file writing

    private fun writeWavFile(pcmData: ByteArray, index: Int): File {
        val chunksDir = File(context.filesDir, "audio_chunks").apply { mkdirs() }
        val file = File(chunksDir, "chunk_${index.toString().padStart(4, '0')}.wav")

        FileOutputStream(file).use { fos ->
            fos.write(buildWavHeader(pcmData.size))
            fos.write(pcmData)
        }
        Log.d(TAG, "Wrote chunk $index → ${file.name} (${pcmData.size} bytes)")
        return file
    }

    //Standard PCM WAV header (44 bytes).
    private fun buildWavHeader(pcmDataSize: Int): ByteArray {
        val totalDataLen  = pcmDataSize + 36
        val byteRate      = SAMPLE_RATE * BYTES_PER_SAMPLE
        val buf           = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                  // PCM subchunk size
        buf.putShort(1)                 // PCM format
        buf.putShort(1)                 // Mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(byteRate)
        buf.putShort(BYTES_PER_SAMPLE.toShort())
        buf.putShort(16)                // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(pcmDataSize)

        return buf.array()
    }

    //Helpers

    private fun initAudioRecord() {
        if (ActivityCompat.checkSelfPermission(context.applicationContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context.applicationContext, "Audio Record Permission Not Granted", Toast.LENGTH_SHORT).show()
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 4
        )
    }

    private fun calculateRms(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        var i   = 0
        while (i < bytesRead - 1) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val mean = sum / (bytesRead / 2)
        return sqrt(mean).toFloat()
    }

    private fun hasEnoughStorage(): Boolean {
        val available = context.filesDir.freeSpace
        return available > LOW_STORAGE_BYTES
    }
}
