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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class LearningNode(
    val id: String,
    val label: String,
    val kind: String = "",
    val category: String = "",
    val timestampMillis: Long? = null,
)

data class LearningUiState(
    val nodes: List<LearningNode> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class LearningViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadLearning()
    }

    fun loadLearning() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("learning/graph")
                _uiState.value = _uiState.value.copy(
                    nodes = element.learningNodes(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    nodes = emptyList(),
                    loading = false,
                    error = e.message ?: "Failed to load learning graph",
                )
            }
        }
    }
}

private fun JsonElement.learningNodes(): List<LearningNode> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["nodes"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val label = obj.optString("label", "title", "name") ?: return@mapNotNull null
        LearningNode(
            id = obj.optString("id", "nodeId", "key") ?: label,
            label = label,
            kind = obj.optString("kind", "type") ?: "",
            category = obj.optString("category") ?: "",
            timestampMillis = obj.epochMillis("timestamp", "ts", "last_activity_at", "created_at"),
        )
    }
}

private fun JsonObject.epochMillis(vararg keys: String): Long? {
    for (key in keys) {
        val el = this[key] ?: continue
        val raw = (el as? JsonPrimitive)?.contentOrNull ?: continue
        val value = raw.toLongOrNull()
            ?: runCatching { (raw.toDouble() * 1000).toLong() }.getOrNull()
            ?: continue
        return if (value in 1 until 10_000_000_000L) value * 1000 else value
    }
    return null
}
