package com.t1erno.whisperkeyboard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object TcpPingHelper {

    private val pingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Performs a network health check to rawUrl.
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

            val request = Request.Builder()
                .url(cleanUrl)
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
            this is java.net.UnknownHostException -> "Unknown host"
            this is java.net.SocketTimeoutException -> "Connection timed out"
            this is java.net.ConnectException -> "Connection refused"
            msg.contains("UNRECOGNIZED_NAME", ignoreCase = true) ||
            msg.contains("unrecognized name", ignoreCase = true) -> "Invalid domain (SSL unrecognized)"
            msg.contains("SSL", ignoreCase = true) || msg.contains("TLS", ignoreCase = true) -> "SSL / TLS Handshake Failed"
            else -> "Connection failed"
        }
    }
}
