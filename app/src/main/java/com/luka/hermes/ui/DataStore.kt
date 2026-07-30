package com.luka.hermes.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_settings")

object SettingsKeys {
    val TOKEN = stringPreferencesKey("hermes_token")
    val API_KEY = stringPreferencesKey("api_key")
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val API_MODEL = stringPreferencesKey("api_model")
    val CHAT_MODE = stringPreferencesKey("chat_mode") // "hermes" or "direct"
    val TEMPERATURE = floatPreferencesKey("temperature")
    val MAX_TOKENS = intPreferencesKey("max_tokens")
    val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    val THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
}
