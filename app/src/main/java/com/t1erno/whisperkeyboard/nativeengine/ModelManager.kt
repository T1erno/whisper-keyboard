package com.t1erno.whisperkeyboard.nativeengine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ModelManager {

    data class ModelInfo(
        val name: String,
        val fileName: String,
        val url: String,
        val description: String
    )

    val MODEL_LARGE_V3 = ModelInfo(
        name = "Large v3 (q5_0)",
        fileName = "ggml-large-v3-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin",
        description = "Maximum precision (1.08 GB)"
    )

    val MODEL_LARGE_V3_TURBO = ModelInfo(
        name = "Large v3 Turbo (q5_0)",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        description = "Recommended • Fast & High Precision (547 MB)"
    )

    val MODEL_MEDIUM = ModelInfo(
        name = "Medium (q5_0)",
        fileName = "ggml-medium-q5_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        description = "High accuracy (539 MB)"
    )

    val MODEL_SMALL = ModelInfo(
        name = "Small",
        fileName = "ggml-small.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        description = "Balanced speed & memory (487 MB)"
    )

    val MODEL_BASE = ModelInfo(
        name = "Base",
        fileName = "ggml-base.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        description = "Lightweight (147 MB)"
    )

    val MODEL_TINY = ModelInfo(
        name = "Tiny",
        fileName = "ggml-tiny.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        description = "Fastest, low memory (77 MB)"
    )

    val AVAILABLE_MODELS = listOf(
        MODEL_LARGE_V3,
        MODEL_LARGE_V3_TURBO,
        MODEL_MEDIUM,
        MODEL_SMALL,
        MODEL_BASE,
        MODEL_TINY
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getModelFile(context: Context, fileName: String): File {
        val modelsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, fileName)
    }

    fun isModelDownloaded(context: Context, fileName: String): Boolean {
        val file = getModelFile(context, fileName)
        return file.exists() && file.length() > 5000000L // Ensure file is not empty or truncated
    }

    fun getModelInfoByFileName(fileName: String): ModelInfo {
        return AVAILABLE_MODELS.find { it.fileName == fileName } ?: MODEL_LARGE_V3_TURBO
    }

    suspend fun downloadModel(
        context: Context,
        modelInfo: ModelInfo,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context, modelInfo.fileName)
        val tempFile = File(targetFile.parentFile, "${modelInfo.fileName}.tmp")

        try {
            val request = Request.Builder()
                .url(modelInfo.url)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: Download failed"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty body response"))
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Failed to write model file"))
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }
}
