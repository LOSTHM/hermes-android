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
import kotlinx.serialization.json.JsonNull

data class SystemStatsUiState(
    val stats: JsonElement = JsonNull,
    val loading: Boolean = false,
    val error: String? = null,
)

class SystemStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SystemStatsUiState())
    val uiState: StateFlow<SystemStatsUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("system/stats")
                _uiState.value = _uiState.value.copy(stats = element, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load system stats",
                )
            }
        }
    }
}
