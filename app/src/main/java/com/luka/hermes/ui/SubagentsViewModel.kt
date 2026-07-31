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

data class SubagentNode(
    val name: String,
    val status: String,
    val model: String = "",
    val depth: Int? = null,
)

data class SubagentTree(
    val label: String,
    val sessionId: String,
    val count: Int = 0,
    val running: Boolean = false,
    val nodes: List<SubagentNode> = emptyList(),
)

data class SubagentsUiState(
    val trees: List<SubagentTree> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SubagentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(SubagentsUiState())
    val uiState: StateFlow<SubagentsUiState> = _uiState.asStateFlow()

    init {
        loadAgents()
    }

    fun loadAgents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = repository.listSpawnTree()
                val entries = result.treeEntries()
                if (entries.isEmpty()) {
                    _uiState.value = _uiState.value.copy(trees = emptyList(), loading = false)
                    return@launch
                }
                val trees = mutableListOf<SubagentTree>()
                for (entry in entries.take(MAX_TREES)) {
                    val path = entry.optString("path") ?: continue
                    val finished = entry.optString("finished_at")?.toDoubleOrNull()
                    val running = finished == null || finished <= 0.0
                    var nodes: List<SubagentNode> = emptyList()
                    try {
                        val snapshot = repository.loadSpawnTree(path)
                        nodes = snapshot.subagentNodes()
                    } catch (_: Exception) {
                        // Tolerate a failed load — still show the tree header.
                    }
                    trees += SubagentTree(
                        label = entry.optString("label") ?: entry.optString("session_id") ?: path,
                        sessionId = entry.optString("session_id") ?: "",
                        count = entry.optString("count")?.toIntOrNull() ?: nodes.size,
                        running = running,
                        nodes = nodes,
                    )
                }
                _uiState.value = _uiState.value.copy(trees = trees, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load subagents",
                )
            }
        }
    }

    private companion object {
        const val MAX_TREES = 15
    }
}

private fun JsonElement.treeEntries(): List<JsonElement> = when (this) {
    is JsonArray -> toList()
    is JsonObject -> listOf("entries", "trees", "items", "results", "data", "list")
        .firstNotNullOfOrNull { key -> (this[key] as? JsonArray) }?.toList() ?: emptyList()
    else -> emptyList()
}

private fun JsonElement.subagentNodes(): List<SubagentNode> {
    val obj = this as? JsonObject ?: return emptyList()
    val list = listOf("subagents", "agents", "nodes", "items")
        .firstNotNullOfOrNull { key -> (obj[key] as? JsonArray) }?.toList() ?: return emptyList()
    return list.mapNotNull { entry ->
        val node = entry as? JsonObject ?: return@mapNotNull null
        val name = node.optString("goal", "name", "title", "id") ?: return@mapNotNull null
        SubagentNode(
            name = name,
            status = node.optString("status", "state") ?: "unknown",
            model = node.optString("model", "modelName", "provider") ?: "",
            depth = node.optString("depth", "level")?.toIntOrNull(),
        )
    }
}
