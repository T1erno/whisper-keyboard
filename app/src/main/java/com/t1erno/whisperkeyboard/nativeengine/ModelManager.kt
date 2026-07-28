package com.t1erno.whisperkeyboard.nativeengine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

    private val activeDownloads = ConcurrentHashMap<String, Int>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
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
        return file.exists() && file.length() > 5000000L
    }

    fun getDownloadProgress(fileName: String): Int? {
        return activeDownloads[fileName]
    }

    fun isModelDownloading(fileName: String): Boolean {
        return activeDownloads.containsKey(fileName)
    }

    fun getModelInfoByFileName(fileName: String): ModelInfo {
        return AVAILABLE_MODELS.find { it.fileName == fileName } ?: MODEL_LARGE_V3_TURBO
    }

    suspend fun downloadModelParallel(
        context: Context,
        modelInfo: ModelInfo,
        numThreads: Int = 4,
        onProgress: (percent: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context, modelInfo.fileName)
        val tempFile = File(targetFile.parentFile, "${modelInfo.fileName}.tmp")

        try {
            activeDownloads[modelInfo.fileName] = 0
            onProgress(0)

            val headRequest = Request.Builder().url(modelInfo.url).head().build()
            val headResponse = httpClient.newCall(headRequest).execute()
            val totalBytes = headResponse.body?.contentLength() ?: -1L
            val acceptsRanges = headResponse.header("Accept-Ranges")?.equals("bytes", ignoreCase = true) == true || totalBytes > 10_000_000L
            headResponse.close()

            if (totalBytes <= 0L || !acceptsRanges || numThreads <= 1) {
                return@withContext downloadSingleStream(context, modelInfo, onProgress)
            }

            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }

            val chunkSize = totalBytes / numThreads
            val totalDownloadedBytes = AtomicLong(0L)

            val deferreds = (0 until numThreads).map { threadIdx ->
                async(Dispatchers.IO) {
                    val startByte = threadIdx * chunkSize
                    val endByte = if (threadIdx == numThreads - 1) totalBytes - 1 else (startByte + chunkSize - 1)

                    val rangeRequest = Request.Builder()
                        .url(modelInfo.url)
                        .addHeader("Range", "bytes=$startByte-$endByte")
                        .build()

                    val response = httpClient.newCall(rangeRequest).execute()
                    if (!response.isSuccessful && response.code != 206) {
                        response.close()
                        throw Exception("Chunk $threadIdx failed with HTTP ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Chunk $threadIdx empty body")
                    val buffer = ByteArray(16384)

                    RandomAccessFile(tempFile, "rw").use { raf ->
                        raf.seek(startByte)
                        body.byteStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                raf.write(buffer, 0, read)
                                val currentTotal = totalDownloadedBytes.addAndGet(read.toLong())
                                val percent = ((currentTotal * 100) / totalBytes).toInt().coerceIn(0, 100)

                                activeDownloads[modelInfo.fileName] = percent
                                onProgress(percent)
                            }
                        }
                    }
                    response.close()
                }
            }

            deferreds.awaitAll()

            if (tempFile.exists() && tempFile.length() == totalBytes) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
                activeDownloads.remove(modelInfo.fileName)
                onProgress(100)
                Result.success(targetFile)
            } else {
                activeDownloads.remove(modelInfo.fileName)
                Result.failure(Exception("Incomplete download verification"))
            }
        } catch (e: Exception) {
            activeDownloads.remove(modelInfo.fileName)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    private fun downloadSingleStream(
        context: Context,
        modelInfo: ModelInfo,
        onProgress: (percent: Int) -> Unit
    ): Result<File> {
        val targetFile = getModelFile(context, modelInfo.fileName)
        val tempFile = File(targetFile.parentFile, "${modelInfo.fileName}.tmp")

        return try {
            val request = Request.Builder().url(modelInfo.url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                activeDownloads.remove(modelInfo.fileName)
                return Result.failure(Exception("HTTP ${response.code}: Download failed"))
            }

            val body = response.body ?: run {
                activeDownloads.remove(modelInfo.fileName)
                return Result.failure(Exception("Empty body response"))
            }
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
                                activeDownloads[modelInfo.fileName] = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            activeDownloads.remove(modelInfo.fileName)

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
                onProgress(100)
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Failed to write model file"))
            }
        } catch (e: Exception) {
            activeDownloads.remove(modelInfo.fileName)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }
}
