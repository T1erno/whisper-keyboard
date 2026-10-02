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
    private const val KEY_REMOTE_MODEL = "remote_model"
    private const val KEY_CUSTOM_REMOTE_MODEL = "custom_remote_model"
    private const val KEY_IS_CUSTOM_REMOTE_MODEL = "is_custom_remote_model"
    private const val KEY_CUSTOM_OFFLINE_MODEL = "custom_offline_model"
    private const val KEY_IS_CUSTOM_OFFLINE_MODEL = "is_custom_offline_model"

    private const val DEFAULT_URL = "https://whisper.t1erno.com/"
    private const val DEFAULT_REMOTE_MODEL = "large-v3-turbo"

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

    fun getRemoteModel(context: Context): String {
        if (isCustomRemoteModel(context)) {
            val custom = getCustomRemoteModel(context)
            if (custom.isNotBlank()) return custom.trim()
        }
        return getPreferences(context).getString(KEY_REMOTE_MODEL, DEFAULT_REMOTE_MODEL) ?: DEFAULT_REMOTE_MODEL
    }

    fun setRemoteModel(context: Context, model: String) {
        getPreferences(context).edit().putString(KEY_REMOTE_MODEL, model.trim()).apply()
    }

    fun getCustomRemoteModel(context: Context): String {
        return getPreferences(context).getString(KEY_CUSTOM_REMOTE_MODEL, "") ?: ""
    }

    fun setCustomRemoteModel(context: Context, customModel: String) {
        getPreferences(context).edit().putString(KEY_CUSTOM_REMOTE_MODEL, customModel.trim()).apply()
    }

    fun isCustomRemoteModel(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_CUSTOM_REMOTE_MODEL, false)
    }

    fun setIsCustomRemoteModel(context: Context, isCustom: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_CUSTOM_REMOTE_MODEL, isCustom).apply()
    }

    fun getCustomOfflineModel(context: Context): String {
        return getPreferences(context).getString(KEY_CUSTOM_OFFLINE_MODEL, "") ?: ""
    }

    fun setCustomOfflineModel(context: Context, customModel: String) {
        getPreferences(context).edit().putString(KEY_CUSTOM_OFFLINE_MODEL, customModel.trim()).apply()
    }

    fun isCustomOfflineModel(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_CUSTOM_OFFLINE_MODEL, false)
    }

    fun setIsCustomOfflineModel(context: Context, isCustom: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_CUSTOM_OFFLINE_MODEL, isCustom).apply()
    }

    fun getSelectedOfflineModel(context: Context): String {
        if (isCustomOfflineModel(context)) {
            val custom = getCustomOfflineModel(context)
            if (custom.isNotBlank()) {
                return ModelManager.extractFileName(custom)
            }
        }
        return getSelectedModelFileName(context)
    }

    fun setSelectedOfflineModel(context: Context, fileName: String) {
        setSelectedModelFileName(context, fileName)
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
