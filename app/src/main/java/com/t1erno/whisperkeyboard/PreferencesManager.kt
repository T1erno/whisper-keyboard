package com.t1erno.whisperkeyboard

import android.content.Context
import android.content.SharedPreferences
import com.t1erno.whisperkeyboard.nativeengine.ModelManager

object PreferencesManager {

    enum class EngineMode {
        REMOTE_SERVER,
        EDGE_ON_DEVICE
    }

    private const val PREF_NAME = "whisper_keyboard_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    private const val KEY_AUTO_SEND_SILENCE = "auto_send_silence"
    private const val KEY_ENGINE_MODE = "engine_mode"
    private const val KEY_SELECTED_MODEL = "selected_model_file"

    private const val DEFAULT_URL = "https://whisper.t1erno.com/"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        var url = getPreferences(context).getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    fun saveServerUrl(context: Context, url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isNotEmpty() && !cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        getPreferences(context).edit().putString(KEY_SERVER_URL, cleanUrl).apply()
    }

    fun isHapticEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    fun isAutoSendOnSilenceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_SEND_SILENCE, true)
    }

    fun setAutoSendOnSilenceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_AUTO_SEND_SILENCE, enabled).apply()
    }

    fun getEngineMode(context: Context): EngineMode {
        val modeStr = getPreferences(context).getString(KEY_ENGINE_MODE, EngineMode.REMOTE_SERVER.name)
        return try {
            EngineMode.valueOf(modeStr ?: EngineMode.REMOTE_SERVER.name)
        } catch (_: Exception) {
            EngineMode.REMOTE_SERVER
        }
    }

    fun setEngineMode(context: Context, mode: EngineMode) {
        getPreferences(context).edit().putString(KEY_ENGINE_MODE, mode.name).apply()
    }

    fun getSelectedModelFileName(context: Context): String {
        return getPreferences(context).getString(
            KEY_SELECTED_MODEL,
            ModelManager.MODEL_LARGE_V3_TURBO.fileName
        ) ?: ModelManager.MODEL_LARGE_V3_TURBO.fileName
    }

    fun setSelectedModelFileName(context: Context, fileName: String) {
        getPreferences(context).edit().putString(KEY_SELECTED_MODEL, fileName).apply()
    }
}
