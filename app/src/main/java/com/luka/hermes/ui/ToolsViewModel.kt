package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
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
import kotlinx.serialization.json.jsonArray

data class ToolsUiState(
    val cronJobs: List<JsonElement> = emptyList(),
    val skills: List<JsonElement> = emptyList(),
    val plugins: List<JsonElement> = emptyList(),
    val agents: List<JsonElement> = emptyList(),
    val tools: List<JsonElement> = emptyList(),
    val toolsets: List<JsonElement> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class ToolsViewModel : ViewModel() {

    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        loadTools()
    }

    fun loadTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val cronJobs = repository.listCronJobs().toListItems()
                val skills = repository.listSkills().toListItems()
                val plugins = repository.listPlugins().toListItems()
                val agents = repository.listAgents().toListItems()
                val tools = repository.listTools().toListItems()
                val toolsets = repository.listToolsets().toListItems()
                _uiState.value = _uiState.value.copy(
                    cronJobs = cronJobs,
                    skills = skills,
                    plugins = plugins,
                    agents = agents,
                    tools = tools,
                    toolsets = toolsets,
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load tools",
                )
            }
        }
    }
}

internal fun JsonElement.toListItems(): List<JsonElement> = when (this) {
    is JsonArray -> toList()
    is JsonObject -> {
        val arrayKey = listOf(
            "jobs", "skills", "plugins", "agents", "tools", "toolsets",
            "items", "results", "data", "cron_jobs",
        ).firstOrNull { key ->
            val el = this[key]
            el != null && el is JsonArray && el.isNotEmpty()
        }
        if (arrayKey != null) this[arrayKey]!!.jsonArray.toList() else listOf(this)
    }
    else -> emptyList()
}

internal fun JsonElement.optString(vararg keys: String): String? {
    val obj = this as? JsonObject ?: return null
    for (key in keys) {
        val el = obj[key] ?: continue
        return when (el) {
            is JsonPrimitive -> el.contentOrNull
            else -> el.toString()
        }
    }
    return null
}
