package com.t1erno.whisperkeyboard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * Performs a network health check to /health relative to rawUrl.
     * Verifies DNS resolution, TCP connection, SSL/TLS certificates, and SNI hostname validity.
     * Returns Result<Long> with RTT in milliseconds on success.
     */
    suspend fun ping(rawUrl: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            var cleanUrl = rawUrl.trim()
            if (cleanUrl.isEmpty()) {
                return@withContext Result.failure(Exception("URL is empty"))
            }
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            if (!cleanUrl.endsWith("/")) {
                cleanUrl = "$cleanUrl/"
            }
            val healthUrl = "${cleanUrl}health"

            val request = Request.Builder()
                .url(healthUrl)
                .head()
                .build()

            val startTime = System.currentTimeMillis()
            val response = pingClient.newCall(request).execute()
            val rtt = System.currentTimeMillis() - startTime
            response.close()

            Result.success(rtt)
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
            msg.contains("CLEARTEXT", ignoreCase = true) || msg.contains("cleartext", ignoreCase = true) -> "HTTP Cleartext blocked"
            this is java.net.UnknownHostException -> "Unknown host (DNS failed)"
            this is java.net.SocketTimeoutException -> "Connection timed out"
            this is java.net.ConnectException -> "Connection refused (Port closed)"
            msg.contains("UNRECOGNIZED_NAME", ignoreCase = true) ||
            msg.contains("unrecognized name", ignoreCase = true) -> "Invalid domain (SSL unrecognized)"
            msg.contains("SSL", ignoreCase = true) || msg.contains("TLS", ignoreCase = true) -> "SSL / TLS Handshake Failed"
            else -> msg.ifEmpty { "Connection failed" }
        }
    }
}
