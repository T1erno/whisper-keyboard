package com.t1erno.whisperkeyboard

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {

    private const val PREF_NAME = "whisper_keyboard_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
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
}
