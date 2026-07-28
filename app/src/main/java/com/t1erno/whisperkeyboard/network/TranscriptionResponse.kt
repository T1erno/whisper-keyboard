package com.t1erno.whisperkeyboard.network

import com.google.gson.annotations.SerializedName

/**
 * Data class representing the backend server transcription JSON response.
 * Expected format: {"text": "transcribed text"}
 */
data class TranscriptionResponse(
    @SerializedName("text")
    val text: String? = null
)
