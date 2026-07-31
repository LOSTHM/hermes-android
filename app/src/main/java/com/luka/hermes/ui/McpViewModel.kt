package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class McpServer(
    val name: String,
    val enabled: Boolean,
    val transport: String = "",
    val url: String? = null,
    val command: String? = null,
    val description: String? = null,
)

data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class McpViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("mcp/servers")
                _uiState.value = _uiState.value.copy(
                    servers = element.mcpServers(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load MCP servers",
                )
            }
        }
    }
}

private fun JsonElement.mcpServers(): List<McpServer> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["servers"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name", "id", "key", "label") ?: return@mapNotNull null
        val enabled = obj.optString("enabled", "is_enabled", "active", "isActive")
            ?.let { it == "true" || it == "1" } != false
        McpServer(
            name = name,
            enabled = enabled,
            transport = obj.optString("transport", "type") ?: "",
            url = obj.optString("url", "endpoint", "baseUrl"),
            command = obj.optString("command", "cmd", "binary"),
            description = obj.optString("description", "desc", "detail", "summary"),
        )
    }
}
