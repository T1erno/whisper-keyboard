package com.t1erno.whisperkeyboard.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface WhisperApiService {
    /**
     * Uploads the recorded audio file to the transcription backend server.
     * Posts to "transcribe" relative to configured base URL.
     */
    @Multipart
    @POST("transcribe")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part
    ): Response<TranscriptionResponse>
}
