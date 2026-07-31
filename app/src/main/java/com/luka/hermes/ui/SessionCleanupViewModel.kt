package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SessionStats(
    val total: Long = 0,
    val activeStore: Long = 0,
    val archived: Long = 0,
    val messages: Long = 0,
    val bySource: Map<String, Long> = emptyMap(),
)

data class SessionCleanupUiState(
    val stats: SessionStats = SessionStats(),
    val loading: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
    val lastAction: String? = null,
)

class SessionCleanupViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionCleanupUiState())
    val uiState: StateFlow<SessionCleanupUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("sessions/stats")
                _uiState.value = _uiState.value.copy(
                    stats = element.sessionStats(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load session stats",
                )
            }
        }
    }

    fun prune() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = null, lastAction = null)
            try {
                HermesRestClient.post("sessions/prune", "{}")
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    lastAction = "Prune finished",
                )
                loadStats()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = e.message ?: "Failed to prune sessions",
                )
            }
        }
    }

    fun empty() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, error = null, lastAction = null)
            try {
                HermesRestClient.delete("sessions/empty")
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    lastAction = "Empty finished",
                )
                loadStats()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    error = e.message ?: "Failed to empty sessions",
                )
            }
        }
    }
}

private fun JsonElement.sessionStats(): SessionStats {
    val obj = this as? JsonObject ?: return SessionStats()
    val bySource = mutableMapOf<String, Long>()
    val sourceObj = (obj["by_source"] as? JsonObject) ?: (obj["bySource"] as? JsonObject)
    if (sourceObj != null) {
        for ((key, value) in sourceObj) {
            val n = (value as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: continue
            bySource[key] = n
        }
    }
    return SessionStats(
        total = obj.optLong("total", "total_sessions"),
        activeStore = obj.optLong("active_store", "active", "active_sessions"),
        archived = obj.optLong("archived", "archived_sessions"),
        messages = obj.optLong("messages", "message_count", "total_messages"),
        bySource = bySource,
    )
}
