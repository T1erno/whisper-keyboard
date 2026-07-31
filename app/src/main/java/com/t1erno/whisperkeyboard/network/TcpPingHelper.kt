package com.t1erno.whisperkeyboard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object TcpPingHelper {

    private val pingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Normalizes and validates rawUrl.
     * Ensures scheme is http or https and host is valid.
     */
    fun normalizeAndValidateUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("URL is empty"))
        }

        // Validate scheme if "://" is present
        if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") {
                return Result.failure(IllegalArgumentException("Invalid URL scheme ('$scheme://' is not allowed, must be http:// or https://)"))
            }
        }

        val urlWithScheme = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val parsedUrl = urlWithScheme.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid URL format"))

        val cleanUrl = if (!parsedUrl.toString().endsWith("/")) {
            "${parsedUrl}/"
        } else {
            parsedUrl.toString()
        }

        return Result.success("${cleanUrl}health")
    }

    /**
     * Performs a network health check to /health relative to rawUrl.
     * Verifies DNS resolution, TCP connection, SSL/TLS certificates, and HTTP 2xx/3xx response codes.
     * Returns Result<Long> with RTT in milliseconds on success.
     */
    suspend fun ping(rawUrl: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val validationResult = normalizeAndValidateUrl(rawUrl)
            if (validationResult.isFailure) {
                return@withContext Result.failure(validationResult.exceptionOrNull() ?: Exception("Invalid URL"))
            }

            val healthUrl = validationResult.getOrThrow()
            val request = Request.Builder()
                .url(healthUrl)
                .head()
                .build()

            val startTime = System.currentTimeMillis()
            val response = pingClient.newCall(request).execute()
            val rtt = System.currentTimeMillis() - startTime
            val isSuccess = response.isSuccessful || response.code in 200..399
            val statusCode = response.code
            response.close()

            if (isSuccess) {
                Result.success(rtt)
            } else {
                Result.failure(Exception("HTTP Error $statusCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Formats network connection exceptions into concise human-readable error messages.
     */
    fun Throwable.toHumanReadablePingError(): String {
        val msg = message ?: ""
        return when {
            this is IllegalArgumentException || msg.contains("Invalid URL", ignoreCase = true) -> msg
            msg.contains("CLEARTEXT", ignoreCase = true) || msg.contains("cleartext", ignoreCase = true) -> "HTTP Cleartext blocked"
            this is java.net.UnknownHostException -> "Unknown host (DNS failed)"
            this is java.net.SocketTimeoutException -> "Connection timed out"
            this is java.net.ConnectException -> "Connection refused (Port closed)"
            msg.contains("HTTP Error", ignoreCase = true) -> msg
            msg.contains("UNRECOGNIZED_NAME", ignoreCase = true) ||
            msg.contains("unrecognized name", ignoreCase = true) -> "Invalid domain (SSL unrecognized)"
            msg.contains("SSL", ignoreCase = true) || msg.contains("TLS", ignoreCase = true) -> "SSL / TLS Handshake Failed"
            else -> msg.ifEmpty { "Connection failed" }
        }
    }
}
