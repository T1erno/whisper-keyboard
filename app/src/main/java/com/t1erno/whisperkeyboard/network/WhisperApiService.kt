package com.t1erno.whisperkeyboard.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface WhisperApiService {
    /**
     * Uploads the recorded audio file to the transcription backend server with optional model & language queries.
     * Posts to "transcribe" relative to configured base URL.
     */
    @Multipart
    @POST("transcribe")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Query("model") model: String? = null,
        @Query("language") language: String? = "es"
    ): Response<TranscriptionResponse>

    /**
     * Queries the backend server for available/downloaded models.
     */
    @GET("models")
    suspend fun getModels(): Response<ResponseBody>
}
