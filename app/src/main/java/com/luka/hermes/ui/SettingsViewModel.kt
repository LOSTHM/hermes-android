package com.luka.hermes.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.ConnectionState
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val hermesToken: String = "",
    val apiKey: String = "",
    val apiBaseUrl: String = "",
    val apiModel: String = "qwen3.6",
    val apiModels: List<String> = emptyList(),
    val chatMode: ChatMode = ChatMode.HERMES,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val saved: Boolean = false,
    val testResult: String? = null,
    val testing: Boolean = false,
    val exporting: Boolean = false,
    val exportError: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.settingsDataStore
    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    init {
        HermesRestClient.init(application)
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _uiState.value = _uiState.value.copy(
                hermesToken = prefs[SettingsKeys.TOKEN] ?: "",
                apiKey = prefs[SettingsKeys.API_KEY] ?: "",
                apiBaseUrl = prefs[SettingsKeys.API_BASE_URL] ?: "",
                apiModel = prefs[SettingsKeys.API_MODEL] ?: "qwen3.6",
                temperature = prefs[SettingsKeys.TEMPERATURE] ?: 0.7f,
                maxTokens = (prefs[SettingsKeys.MAX_TOKENS] ?: 4096),
                systemPrompt = prefs[SettingsKeys.SYSTEM_PROMPT] ?: "",
                themeMode = try { ThemeMode.valueOf(prefs[SettingsKeys.THEME_MODE] ?: "SYSTEM") } catch (_: Exception) { ThemeMode.SYSTEM },
                chatMode = when (prefs[SettingsKeys.CHAT_MODE]) {
                    "direct" -> ChatMode.DIRECT
                    else -> ChatMode.HERMES
                },
            )
        }
    }

    fun updateHermesToken(t: String) { _uiState.value = _uiState.value.copy(hermesToken = t) }
    fun updateApiKey(k: String) { _uiState.value = _uiState.value.copy(apiKey = k) }
    fun updateApiBaseUrl(u: String) { _uiState.value = _uiState.value.copy(apiBaseUrl = u) }
    fun updateApiModel(m: String) { _uiState.value = _uiState.value.copy(apiModel = m) }
    fun updateTemperature(t: Float) { _uiState.value = _uiState.value.copy(temperature = t) }
    fun updateMaxTokens(m: Int) { _uiState.value = _uiState.value.copy(maxTokens = m) }
    fun updateSystemPrompt(s: String) { _uiState.value = _uiState.value.copy(systemPrompt = s) }
    fun updateThemeMode(m: ThemeMode) { _uiState.value = _uiState.value.copy(themeMode = m) }
    fun updateChatMode(m: ChatMode) { _uiState.value = _uiState.value.copy(chatMode = m) }

    fun saveAndConnect() {
        val state = _uiState.value
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.TOKEN] = state.hermesToken
                prefs[SettingsKeys.API_KEY] = state.apiKey
                prefs[SettingsKeys.API_BASE_URL] = state.apiBaseUrl
                prefs[SettingsKeys.API_MODEL] = state.apiModel
                prefs[SettingsKeys.TEMPERATURE] = state.temperature
                prefs[SettingsKeys.MAX_TOKENS] = state.maxTokens
                prefs[SettingsKeys.SYSTEM_PROMPT] = state.systemPrompt
                prefs[SettingsKeys.THEME_MODE] = state.themeMode.name
                prefs[SettingsKeys.CHAT_MODE] = when (state.chatMode) {
                    ChatMode.HERMES -> "hermes"
                    ChatMode.DIRECT -> "direct"
                }
            }

            if (state.chatMode == ChatMode.HERMES && state.hermesToken.isNotBlank()) {
                try {
                    repository.connect(state.hermesToken)
                } catch (e: Exception) {
                    // Connection error visible via connectionState
                }
            }

            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    fun testDirectConnection() {
        val state = _uiState.value
        if (state.apiBaseUrl.isBlank() || state.apiKey.isBlank()) return

        _uiState.value = _uiState.value.copy(testing = true, testResult = null)

        viewModelScope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("${state.apiBaseUrl.trimEnd('/')}/models")
                    .header("Authorization", "Bearer ${state.apiKey}")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val models = JSONObject(body).optJSONArray("data")
                    val modelNames = if (models != null) {
                        (0 until models.length()).map { i ->
                            models.getJSONObject(i).optString("id", "?")
                        }
                    } else emptyList()

                    val result = buildString {
                        appendLine("✅ Connected!")
                        if (modelNames.isNotEmpty()) {
                            appendLine("Models:")
                            modelNames.forEach { appendLine("  • $it") }
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        testResult = result,
                        testing = false,
                        apiModels = modelNames,
                        apiModel = if (state.apiModel.isBlank() && modelNames.isNotEmpty()) modelNames.first() else state.apiModel,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        testResult = "❌ HTTP ${response.code}: $body",
                        testing = false,
                    )
                }
                response.close()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    testResult = "❌ ${e.message ?: "Connection failed"}",
                    testing = false,
                )
            }
        }
    }

    /**
     * Export the most recent Hermes session as JSON via
     * `GET /api/sessions/{id}/export` and publish it to [exportJson] so the
     * screen can share it. Falls back to any available session when none is
     * "current" (the daemon keeps no client-side current-session notion).
     */
    fun exportCurrentSession() {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exporting = true, exportError = null)
            try {
                val sessions = repository.listSessions()
                val session = sessions.firstOrNull()
                    ?: throw IllegalStateException("No sessions available to export")
                val data = HermesRestClient.getJson("sessions/${session.id}/export")
                _exportJson.value = data.toString()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    exportError = e.message ?: "Export failed",
                )
            } finally {
                _uiState.value = _uiState.value.copy(exporting = false)
            }
        }
    }

    fun clearExportJson() { _exportJson.value = null }

    fun clearSavedFlag() { _uiState.value = _uiState.value.copy(saved = false) }
    fun disconnect() { repository.disconnect() }
}
