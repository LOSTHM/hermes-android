package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class SessionSearchResult(
    val sessionId: String = "",
    val snippet: String = "",
    val role: String? = null,
    val source: String? = null,
    val model: String? = null,
    val startedAt: String? = null,
)

data class SessionSearchUiState(
    val query: String = "",
    val results: List<SessionSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SessionSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionSearchUiState())
    val uiState: StateFlow<SessionSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        HermesRestClient.init(application)
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            search(query)
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        viewModelScope.launch {
            if (trimmed.isEmpty()) {
                _uiState.value = _uiState.value.copy(results = emptyList(), loading = false, error = null)
                return@launch
            }
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("sessions/search", mapOf("q" to trimmed))
                _uiState.value = _uiState.value.copy(
                    results = element.sessionSearchResults(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Search failed",
                )
            }
        }
    }
}

private fun JsonElement.sessionSearchResults(): List<SessionSearchResult> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["results"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val id = obj.optString("session_id", "id", "sessionId") ?: return@mapNotNull null
        SessionSearchResult(
            sessionId = id,
            snippet = obj.optString("snippet", "preview", "text", "summary") ?: "",
            role = obj.optString("role"),
            source = obj.optString("source"),
            model = obj.optString("model"),
            startedAt = obj.optString("session_started", "started_at", "startedAt"),
        )
    }
}
