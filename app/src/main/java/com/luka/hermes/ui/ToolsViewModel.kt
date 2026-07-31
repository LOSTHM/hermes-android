package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val SECTION_TIMEOUT_MS = 15_000L

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

            // Load every section in parallel with a short per-RPC timeout so a
            // slow call cannot block the whole page. Each section is guarded
            // independently: one failing section shows "Failed to load" while
            // the others still render.
            val results = supervisorScope {
                awaitAll(
                    async { runSection(emptyList<JsonElement>()) { repository.listCronJobs(SECTION_TIMEOUT_MS).toListItems() } },
                    async { runSection(emptyList<JsonElement>()) { repository.listSkills(SECTION_TIMEOUT_MS).toListItems() } },
                    async { runSection(emptyList<JsonElement>()) { repository.listPlugins(SECTION_TIMEOUT_MS).toListItems() } },
                    async { runSection(emptyList<JsonElement>()) { repository.listAgents(SECTION_TIMEOUT_MS).toListItems() } },
                    async { runSection(emptyList<JsonElement>()) { repository.listTools(SECTION_TIMEOUT_MS).toListItems() } },
                    async { runSection(emptyList<JsonElement>()) { repository.listToolsets(SECTION_TIMEOUT_MS).toListItems() } },
                )
            }

            _uiState.value = _uiState.value.copy(
                cronJobs = results[0].value,
                cronJobsError = results[0].error != null,
                skills = results[1].value,
                skillsError = results[1].error != null,
                plugins = results[2].value,
                pluginsError = results[2].error != null,
                agents = results[3].value,
                agentsError = results[3].error != null,
                tools = results[4].value,
                toolsError = results[4].error != null,
                toolsets = results[5].value,
                toolsetsError = results[5].error != null,
                loading = false,
                error = results.mapNotNull { it.error }.joinToString(" · ").ifEmpty { null },
            )
        }
    }
}

/**
 * Run one guarded section: on failure return [default] plus the error message
 * so callers can flag the section as unavailable without losing the others.
 * True coroutine cancellation is rethrown untouched.
 */
internal suspend fun <T> runSection(default: T, block: suspend () -> T): SectionResult<T> =
    try {
        SectionResult(block(), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SectionResult(default, e.message ?: "section failed")
    }

internal data class SectionResult<T>(val value: T, val error: String?)

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
