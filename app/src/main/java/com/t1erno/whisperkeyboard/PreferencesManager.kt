package com.t1erno.whisperkeyboard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.t1erno.whisperkeyboard.nativeengine.ModelManager
import org.json.JSONArray

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

    const val KEY_HISTORY_SERVER_URL = "history_server_url"
    const val KEY_HISTORY_REMOTE_MODEL = "history_remote_model"
    const val KEY_HISTORY_OFFLINE_MODEL = "history_offline_model"

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
        getPreferences(context).edit { putString(KEY_SERVER_URL, cleanUrl) }
    }

    fun isHapticEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_HAPTIC_ENABLED, enabled) }
    }

    fun isAutoSendOnSilenceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_SEND_SILENCE, true)
    }

    fun setAutoSendOnSilenceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_AUTO_SEND_SILENCE, enabled) }
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
        getPreferences(context).edit { putString(KEY_ENGINE_MODE, mode.name) }
    }

    fun getRemoteModel(context: Context): String {
        if (isCustomRemoteModel(context)) {
            val custom = getCustomRemoteModel(context)
            if (custom.isNotBlank()) return custom.trim()
        }
        return getPreferences(context).getString(KEY_REMOTE_MODEL, DEFAULT_REMOTE_MODEL) ?: DEFAULT_REMOTE_MODEL
    }

    fun setRemoteModel(context: Context, model: String) {
        getPreferences(context).edit { putString(KEY_REMOTE_MODEL, model.trim()) }
    }

    fun getCustomRemoteModel(context: Context): String {
        return getPreferences(context).getString(KEY_CUSTOM_REMOTE_MODEL, "") ?: ""
    }

    fun setCustomRemoteModel(context: Context, customModel: String) {
        getPreferences(context).edit { putString(KEY_CUSTOM_REMOTE_MODEL, customModel.trim()) }
    }

    fun isCustomRemoteModel(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_CUSTOM_REMOTE_MODEL, false)
    }

    fun setIsCustomRemoteModel(context: Context, isCustom: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_IS_CUSTOM_REMOTE_MODEL, isCustom) }
    }

    fun getCustomOfflineModel(context: Context): String {
        return getPreferences(context).getString(KEY_CUSTOM_OFFLINE_MODEL, "") ?: ""
    }

    fun setCustomOfflineModel(context: Context, customModel: String) {
        getPreferences(context).edit { putString(KEY_CUSTOM_OFFLINE_MODEL, customModel.trim()) }
    }

    fun isCustomOfflineModel(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_CUSTOM_OFFLINE_MODEL, false)
    }

    fun setIsCustomOfflineModel(context: Context, isCustom: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_IS_CUSTOM_OFFLINE_MODEL, isCustom) }
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
        getPreferences(context).edit { putString(KEY_SELECTED_MODEL, fileName) }
    }

    fun getHistory(context: Context, key: String): List<String> {
        val jsonString = getPreferences(context).getString(key, null)
        if (jsonString.isNullOrBlank()) {
            val defaults = getDefaultHistory(context, key)
            if (defaults.isNotEmpty()) {
                saveHistory(context, key, defaults)
            }
            return defaults
        }
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i)
                if (!item.isNullOrBlank()) {
                    list.add(item.trim())
                }
            }
            if (list.isEmpty()) {
                val defaults = getDefaultHistory(context, key)
                if (defaults.isNotEmpty()) {
                    saveHistory(context, key, defaults)
                }
                defaults
            } else {
                list
            }
        } catch (_: Exception) {
            getDefaultHistory(context, key)
        }
    }

    private fun getDefaultHistory(context: Context, key: String): List<String> {
        return when (key) {
            KEY_HISTORY_SERVER_URL -> {
                val current = getServerUrl(context)
                listOfNotNull(current, if (current != DEFAULT_URL) DEFAULT_URL else null).distinct()
            }
            KEY_HISTORY_REMOTE_MODEL -> {
                val current = getCustomRemoteModel(context)
                val list = mutableListOf<String>()
                if (current.isNotBlank()) list.add(current)
                list.add("Systran/faster-whisper-small")
                list.add("deepdml/faster-whisper-large-v3-turbo-ct2")
                list.distinct()
            }
            KEY_HISTORY_OFFLINE_MODEL -> {
                val current = getCustomOfflineModel(context)
                val list = mutableListOf<String>()
                if (current.isNotBlank()) list.add(current)
                list.add("ggerganov/whisper.cpp/ggml-medium.en.bin")
                list.add("ggerganov/whisper.cpp/ggml-large-v3-turbo-q5_0.bin")
                list.distinct()
            }
            else -> emptyList()
        }
    }

    fun saveHistory(context: Context, key: String, list: List<String>) {
        val array = JSONArray()
        for (item in list) {
            val trimmed = item.trim()
            if (trimmed.isNotBlank()) {
                array.put(trimmed)
            }
        }
        getPreferences(context).edit { putString(key, array.toString()) }
    }

    fun addToHistory(context: Context, key: String, item: String) {
        val trimmed = item.trim()
        if (trimmed.isBlank()) return
        val current = getHistory(context, key).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 50) {
            current.subList(50, current.size).clear()
        }
        saveHistory(context, key, current)
    }

    fun updateHistoryItem(context: Context, key: String, oldItem: String, newItem: String) {
        val trimmedNew = newItem.trim()
        if (trimmedNew.isBlank()) return
        val current = getHistory(context, key).toMutableList()
        val index = current.indexOf(oldItem)
        if (index != -1) {
            current[index] = trimmedNew
            saveHistory(context, key, current)
        }
    }

    fun removeFromHistory(context: Context, key: String, item: String) {
        val current = getHistory(context, key).toMutableList()
        if (current.remove(item)) {
            saveHistory(context, key, current)
        }
    }
}
