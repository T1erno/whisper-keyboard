package com.t1erno.whisperkeyboard.network

import android.content.Context
import com.t1erno.whisperkeyboard.PreferencesManager
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
     * Uploads recorded audio file via multipart HTTP POST request.
     * Returns Result<String> containing transcribed text or exception.
     */
    suspend fun uploadAudio(context: Context, audioFile: File): Result<String> {
        return try {
            val apiService = getApiService(context)
            val requestFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)

            val response = apiService.transcribeAudio(body)
            if (response.isSuccessful) {
                val transcribedText = response.body()?.text
                if (!transcribedText.isNullOrEmpty()) {
                    Result.success(transcribedText)
                } else {
                    Result.failure(Exception("Empty transcription"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Server error: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
