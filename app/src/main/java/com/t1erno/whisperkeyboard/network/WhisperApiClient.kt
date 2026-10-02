package com.t1erno.whisperkeyboard.network

import android.content.Context
import com.t1erno.whisperkeyboard.PreferencesManager
import com.t1erno.whisperkeyboard.nativeengine.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

data class ServerModelsResponse(
    val availableModels: List<String>,
    val loadedModels: List<String>,
    val defaultModel: String?
)

object WhisperApiClient {

    private var currentBaseUrl: String? = null
    private var cachedApiService: WhisperApiService? = null
    var lastServerModelsResponse: ServerModelsResponse? = null
        private set

    fun isModelAvailableOnServer(serverKey: String): Boolean? {
        val resp = lastServerModelsResponse ?: return null
        return resp.availableModels.any { it.equals(serverKey.trim(), ignoreCase = true) }
    }

    fun isModelLoadedOnServer(serverKey: String): Boolean {
        return lastServerModelsResponse?.loadedModels?.any { it.equals(serverKey.trim(), ignoreCase = true) } == true
    }

    suspend fun fetchServerModels(context: Context): Result<ServerModelsResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val apiService = getApiService(context)
            val response = apiService.getModels()
            if (response.isSuccessful && response.body() != null) {
                val rawJson = response.body()!!.string().trim()
                val available = mutableListOf<String>()
                val loaded = mutableListOf<String>()
                var defaultModel: String? = null

                if (rawJson.startsWith("{")) {
                    val jsonObj = org.json.JSONObject(rawJson)
                    if (jsonObj.has("available_models")) {
                        val availArray = jsonObj.optJSONArray("available_models")
                        if (availArray != null) {
                            for (i in 0 until availArray.length()) {
                                val item = availArray.optString(i, "").trim()
                                if (item.isNotEmpty() && !available.contains(item)) available.add(item)
                            }
                        }
                        val loadedArray = jsonObj.optJSONArray("loaded_models")
                        if (loadedArray != null) {
                            for (i in 0 until loadedArray.length()) {
                                val item = loadedArray.optString(i, "").trim()
                                if (item.isNotEmpty() && !loaded.contains(item)) loaded.add(item)
                            }
                        }
                        defaultModel = jsonObj.optString("default_model", "").takeIf { it.isNotEmpty() }
                    } else {
                        // Fallback: Map of modelKey -> boolean (true if loaded/ready)
                        val keys = jsonObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next().trim()
                            if (key.isNotEmpty()) {
                                val isReady = jsonObj.optBoolean(key, false)
                                if (!available.contains(key)) available.add(key)
                                if (isReady && !loaded.contains(key)) loaded.add(key)
                            }
                        }
                    }
                } else if (rawJson.startsWith("[")) {
                    val array = org.json.JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val item = array.optString(i, "").trim()
                        if (item.isNotEmpty() && !available.contains(item)) available.add(item)
                    }
                }

                val modelsResponse = ServerModelsResponse(
                    availableModels = available,
                    loadedModels = loaded,
                    defaultModel = defaultModel
                )
                lastServerModelsResponse = modelsResponse
                Result.success(modelsResponse)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // 5 minutes read timeout for long audio transcriptions
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    private fun getApiService(context: Context): WhisperApiService {
        val baseUrl = PreferencesManager.getServerUrl(context)
        if (cachedApiService == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(getOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            cachedApiService = retrofit.create(WhisperApiService::class.java)
        }
        return cachedApiService!!
    }

    /**
     * Uploads recorded audio file via multipart HTTP POST request to /transcribe?model=<serverKey>&language=<lang>.
     * Returns Result<String> containing transcribed text or exception.
     */
    suspend fun uploadAudio(
        context: Context,
        audioFile: File,
        language: String = "es"
    ): Result<String> {
        return try {
            val apiService = getApiService(context)
            val requestFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)

            val remoteModelKey = PreferencesManager.getRemoteModel(context)

            val response = apiService.transcribeAudio(
                file = body,
                model = remoteModelKey.ifEmpty { null },
                language = language
            )

            if (response.isSuccessful) {
                val transcribedText = response.body()?.text
                if (!transcribedText.isNullOrEmpty()) {
                    Result.success(transcribedText)
                } else {
                    Result.failure(Exception("Empty transcription"))
                }
            } else {
                val errorMsg = parseErrorMessage(response)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(response: retrofit2.Response<*>): String {
        val code = response.code()
        val rawMessage = response.message()
        val rawBody = try {
            response.errorBody()?.string()?.trim()
        } catch (_: Exception) {
            null
        }

        // 1. Try parsing JSON error body if present (e.g. {"detail": "...", "error": "...", "message": "..."})
        if (!rawBody.isNullOrEmpty() && (rawBody.startsWith("{") || rawBody.startsWith("["))) {
            try {
                val json = org.json.JSONObject(rawBody)
                val detail = json.optString("detail", json.optString("error", json.optString("message", "")))
                if (detail.isNotEmpty()) {
                    return "HTTP $code: $detail"
                }
            } catch (_: Exception) {
                // Ignore JSON parse failure
            }
        }

        // 2. Check if body is HTML (e.g. Nginx, OpenResty, Cloudflare error pages)
        if (!rawBody.isNullOrEmpty() && (rawBody.contains("<html", ignoreCase = true) || rawBody.contains("<!DOCTYPE", ignoreCase = true) || rawBody.startsWith("<"))) {
            val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(rawBody)
            val extractedTitle = titleMatch?.groupValues?.get(1)?.trim()

            if (!extractedTitle.isNullOrEmpty()) {
                val cleanTitle = extractedTitle.replace(Regex("<[^>]*>"), "").trim()
                return if (cleanTitle.startsWith("HTTP", ignoreCase = true) || cleanTitle.contains(code.toString())) {
                    cleanTitle
                } else {
                    "HTTP $code ($cleanTitle)"
                }
            }
            val reason = getStandardHttpReason(code, rawMessage)
            return "HTTP $code $reason".trim()
        }

        // 3. If raw body is plain text and reasonably concise (< 150 chars)
        if (!rawBody.isNullOrEmpty() && rawBody.length < 150 && !rawBody.contains("<")) {
            return "HTTP $code: $rawBody"
        }

        // 4. Fallback to HTTP code + status message or reason
        val reason = getStandardHttpReason(code, rawMessage)
        return "HTTP $code $reason".trim()
    }

    private fun getStandardHttpReason(code: Int, defaultMessage: String?): String {
        if (!defaultMessage.isNullOrEmpty() && defaultMessage != "Response.error()") {
            return defaultMessage
        }
        return when (code) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Server Error"
        }
    }
}
