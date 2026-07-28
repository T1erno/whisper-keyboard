package com.t1erno.whisperkeyboard.nativeengine

import android.content.Context
import android.util.Log
import com.t1erno.whisperkeyboard.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object OnDeviceTranscriber {

    private const val TAG = "OnDeviceTranscriber"

    private var loadedModelPath: String? = null
    private var nativeContextPtr: Long = 0L

    @Synchronized
    private fun getOrInitContext(context: Context): Long {
        val modelFileName = PreferencesManager.getSelectedModelFileName(context)
        val modelFile = ModelManager.getModelFile(context, modelFileName)

        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            return 0L
        }

        if (nativeContextPtr != 0L && loadedModelPath == modelFile.absolutePath) {
            return nativeContextPtr
        }

        if (nativeContextPtr != 0L) {
            WhisperNative.freeContext(nativeContextPtr)
            nativeContextPtr = 0L
            loadedModelPath = null
        }

        val ptr = WhisperNative.initContext(modelFile.absolutePath)
        if (ptr != 0L) {
            nativeContextPtr = ptr
            loadedModelPath = modelFile.absolutePath
            Log.i(TAG, "Loaded on-device whisper model: ${modelFile.name}")
        }
        return nativeContextPtr
    }

    suspend fun transcribeAudioFile(context: Context, audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ctxPtr = getOrInitContext(context)
            if (ctxPtr == 0L) {
                val modelName = PreferencesManager.getSelectedModelFileName(context)
                return@withContext Result.failure(Exception("Model file not downloaded ($modelName)"))
            }

            val samples = AudioDecoder.decodeToPCM16kHzFloat(audioFile)
            if (samples.isEmpty()) {
                return@withContext Result.failure(Exception("Could not decode audio samples"))
            }

            val cores = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val text = WhisperNative.transcribeData(ctxPtr, cores, samples, "auto")

            if (text.isNotBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(Exception("Empty transcription"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Synchronized
    fun releaseContext() {
        if (nativeContextPtr != 0L) {
            WhisperNative.freeContext(nativeContextPtr)
            nativeContextPtr = 0L
            loadedModelPath = null
        }
    }
}
