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

object WhisperApiClient {

    private var currentBaseUrl: String? = null
    private var cachedApiService: WhisperApiService? = null
    private var serverModelsCache: Map<String, Boolean>? = null

    fun isModelAvailableOnServer(serverKey: String): Boolean? {
        return serverModelsCache?.get(serverKey.lowercase())
    }

    suspend fun fetchServerModels(context: Context): Result<Map<String, Boolean>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val apiService = getApiService(context)
            val response = apiService.getModels()
            if (response.isSuccessful && response.body() != null) {
                val rawJson = response.body()!!.string().trim()
                val resultMap = mutableMapOf<String, Boolean>()

                if (rawJson.startsWith("{")) {
                    val jsonObj = org.json.JSONObject(rawJson)
                    val array = jsonObj.optJSONArray("models") ?: jsonObj.optJSONArray("data")
                    if (array != null) {
                        parseJsonArrayToMap(array, resultMap)
                    } else {
                        val keys = jsonObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val value = jsonObj.optBoolean(k, true)
                            resultMap[k.lowercase()] = value
                        }
                    }
                } else if (rawJson.startsWith("[")) {
                    val array = org.json.JSONArray(rawJson)
                    parseJsonArrayToMap(array, resultMap)
                }

                serverModelsCache = resultMap
                Result.success(resultMap)
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseJsonArrayToMap(array: org.json.JSONArray, resultMap: MutableMap<String, Boolean>) {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is org.json.JSONObject) {
                val key = item.optString("key", item.optString("id", item.optString("name", "")))
                val available = item.optBoolean("available", item.optBoolean("downloaded", item.optBoolean("ready", true)))
                if (key.isNotEmpty()) {
                    resultMap[key.lowercase()] = available
                }
            } else if (item is String) {
                resultMap[item.lowercase()] = true
            }
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

            val selectedFileName = PreferencesManager.getSelectedModelFileName(context)
            val modelInfo = ModelManager.getModelInfoByFileName(selectedFileName)
            val serverModelKey = modelInfo.serverKey

            val response = apiService.transcribeAudio(
                file = body,
                model = serverModelKey,
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
