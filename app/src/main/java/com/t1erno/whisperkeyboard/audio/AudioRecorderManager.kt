package com.t1erno.whisperkeyboard.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    var isRecording: Boolean = false
        private set

    @Synchronized
    fun startRecording(): File? {
        // Ensure any existing recorder session is cleanly released
        releaseRecorder()

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        startTimeMs = System.currentTimeMillis()

        return try {
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d(TAG, "Recording started: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            releaseRecorder()
            file.delete()
            null
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (isRecording) mediaRecorder?.maxAmplitude ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        if (!isRecording) {
            releaseRecorder()
            return null
        }

        val elapsed = System.currentTimeMillis() - startTimeMs
        // If recording duration was under 600ms, wait remaining time so MediaRecorder.stop() doesn't fail natively
        if (elapsed < 600) {
            try {
                Thread.sleep(600 - elapsed)
            } catch (_: InterruptedException) {}
        }

        val file = outputFile
        try {
            mediaRecorder?.stop()
            Log.d(TAG, "Recording stopped. File size: ${file?.length() ?: 0} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder.stop() failed (clip too short)", e)
        } finally {
            releaseRecorder()
        }

        // Return file only if valid and contains actual audio (> 1000 bytes)
        return if (file != null && file.exists() && file.length() > 1000) {
            file
        } else {
            file?.delete()
            null
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    @Synchronized
    fun releaseRecorder() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}

        mediaRecorder = null
        isRecording = false
    }

    companion object {
        private const val TAG = "AudioRecorderManager"
    }
}
