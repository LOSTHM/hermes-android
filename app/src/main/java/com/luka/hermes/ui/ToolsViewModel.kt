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

data class ToolsUiState(
    val cronJobs: List<JsonElement> = emptyList(),
    val cronJobsError: Boolean = false,
    val skills: List<JsonElement> = emptyList(),
    val skillsError: Boolean = false,
    val plugins: List<JsonElement> = emptyList(),
    val pluginsError: Boolean = false,
    val agents: List<JsonElement> = emptyList(),
    val agentsError: Boolean = false,
    val tools: List<JsonElement> = emptyList(),
    val toolsError: Boolean = false,
    val toolsets: List<JsonElement> = emptyList(),
    val toolsetsError: Boolean = false,
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

            val errors = mutableListOf<String>()

            var cronJobs: List<JsonElement> = emptyList()
            var cronJobsError = false
            var skills: List<JsonElement> = emptyList()
            var skillsError = false
            var plugins: List<JsonElement> = emptyList()
            var pluginsError = false
            var agents: List<JsonElement> = emptyList()
            var agentsError = false
            var tools: List<JsonElement> = emptyList()
            var toolsError = false
            var toolsets: List<JsonElement> = emptyList()
            var toolsetsError = false

            // Each call is guarded independently: one failing section shows
            // "Failed to load" but the other sections still render.
            try { cronJobs = repository.listCronJobs().toListItems() }
            catch (e: Exception) { cronJobsError = true; errors += e.message ?: "cron.manage failed" }

            try { skills = repository.listSkills().toListItems() }
            catch (e: Exception) { skillsError = true; errors += e.message ?: "skills.manage failed" }

            try { plugins = repository.listPlugins().toListItems() }
            catch (e: Exception) { pluginsError = true; errors += e.message ?: "plugins.list failed" }

            try { agents = repository.listAgents().toListItems() }
            catch (e: Exception) { agentsError = true; errors += e.message ?: "agents.list failed" }

            try { tools = repository.listTools().toListItems() }
            catch (e: Exception) { toolsError = true; errors += e.message ?: "tools.list failed" }

            try { toolsets = repository.listToolsets().toListItems() }
            catch (e: Exception) { toolsetsError = true; errors += e.message ?: "toolsets.list failed" }

            _uiState.value = _uiState.value.copy(
                cronJobs = cronJobs,
                cronJobsError = cronJobsError,
                skills = skills,
                skillsError = skillsError,
                plugins = plugins,
                pluginsError = pluginsError,
                agents = agents,
                agentsError = agentsError,
                tools = tools,
                toolsError = toolsError,
                toolsets = toolsets,
                toolsetsError = toolsetsError,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }
}

/**
 * Normalise a tools/agents/system response into a list of items.
 *
 * Handles bare arrays, `{ "<listKey>": [...] }` objects, and wraps stray
 * objects so they are still visible. An array under a known key is used even
 * when empty — e.g. `agents.list` returns `{"processes": []}`, which must
 * resolve to an empty list rather than being treated as a single item.
 *
 * A known key whose value is a nested object is flattened too — e.g.
 * `skills.manage list` returns `{"skills": {"general": [...], "android": [...]}}`,
 * which merges every array inside that object into a single list.
 */
internal fun JsonElement.toListItems(): List<JsonElement> = when (this) {
    is JsonArray -> toList()
    is JsonObject -> {
        val arrayKey = listOf(
            "jobs", "skills", "plugins", "agents", "processes", "tools", "toolsets",
            "items", "results", "data", "cron_jobs",
        ).firstOrNull { key -> this[key] is JsonArray || this[key] is JsonObject }
        if (arrayKey != null) {
            when (val value = this[arrayKey]) {
                is JsonArray -> value.toList()
                is JsonObject -> value.values.filterIsInstance<JsonArray>().flatMap { it.toList() }
                else -> emptyList()
            }
        } else {
            listOf(this)
        }
    }
    else -> emptyList()
}

/**
 * Read a display string from a list item. Object items look up the given
 * keys; bare primitives (e.g. a skill name returned as a plain string) are
 * returned directly so nested-object lists render their values.
 */
internal fun JsonElement.optString(vararg keys: String): String? {
    if (this is JsonPrimitive) return this.contentOrNull
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
