package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class MemoryEntry(
    val name: String,
    val size: Long,
)

data class MemoryProvider(
    val name: String,
    val description: String = "",
    val available: Boolean = false,
    val configured: Boolean = false,
    val status: String = "",
)

data class MemoryUiState(
    val entries: List<MemoryEntry> = emptyList(),
    val providers: List<MemoryProvider> = emptyList(),
    val activeProvider: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadMemory()
    }

    fun loadMemory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("memory")
                _uiState.value = _uiState.value.copy(
                    entries = element.memoryEntries(),
                    providers = element.memoryProviders(),
                    activeProvider = element.memoryActiveProvider(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load memory",
                )
            }
        }
    }

    /** Tolerant load of the provider endpoint; falls back to the /api/memory payload. */
    fun loadProvider() {
        viewModelScope.launch {
            try {
                val element = HermesRestClient.getJson("memory/provider")
                val providers = element.memoryProviders()
                if (providers.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        providers = providers,
                        activeProvider = element.memoryActiveProvider()
                            .ifEmpty { _uiState.value.activeProvider },
                    )
                }
            } catch (_: Exception) {
                // /api/memory/provider is a PUT-only endpoint on this server build;
                // provider status already comes from /api/memory.
            }
        }
    }
}

/** Parse memory entries from `builtin_files` (e.g. {memory: 1234, user: 5678}). */
private fun JsonElement.memoryEntries(): List<MemoryEntry> {
    val obj = this as? JsonObject ?: return emptyList()
    val files = obj["builtin_files"] as? JsonObject ?: return emptyList()
    val names = mapOf(
        "memory" to "MEMORY.md",
        "user" to "USER.md",
        "persistent" to "PERSISTENT.md",
    )
    return files.entries.mapNotNull { (key, value) ->
        val size = (value as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
        MemoryEntry(name = names[key] ?: key, size = size)
    }.sortedBy { it.name }
}

/** Parse the provider status list, tolerant of `{providers: [...]}` or a bare array. */
private fun JsonElement.memoryProviders(): List<MemoryProvider> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["providers"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name", "id") ?: return@mapNotNull null
        MemoryProvider(
            name = name,
            description = obj.optString("description", "desc") ?: "",
            available = obj.optString("available", "is_available", "installed")
                ?.let { it == "true" || it == "1" } == true,
            configured = obj.optString("configured", "is_configured")
                ?.let { it == "true" || it == "1" } == true,
            status = obj.optString("status") ?: "",
        )
    }
}

private fun JsonElement.memoryActiveProvider(): String {
    val obj = this as? JsonObject ?: return ""
    return obj.optString("active", "active_provider", "provider") ?: ""
}
