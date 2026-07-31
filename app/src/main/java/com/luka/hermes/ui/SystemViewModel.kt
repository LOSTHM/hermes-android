package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class SystemUiState(
    val processes: List<JsonElement> = emptyList(),
    val processesError: Boolean = false,
    val system: JsonElement? = null,
    val systemError: Boolean = false,
    val config: List<JsonElement> = emptyList(),
    val configError: Boolean = false,
    val models: List<JsonElement> = emptyList(),
    val modelsError: Boolean = false,
    val usage: JsonElement? = null,
    val usageError: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class SystemViewModel : ViewModel() {

    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(SystemUiState())
    val uiState: StateFlow<SystemUiState> = _uiState.asStateFlow()

    init {
        loadSystem()
    }

    fun loadSystem() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)

            // Load every section in parallel with a short per-RPC timeout so a
            // slow call cannot block the whole page. Each section is guarded
            // independently: one failing section (e.g. process.list → 4001
            // "session not found" on serve) never blocks the rest.
            val results = supervisorScope {
                val processes = async { runSection(emptyList<JsonElement>()) { repository.listProcesses(SECTION_TIMEOUT_MS).toListItems() } }
                val system = async { runSection<JsonElement?>(null) { repository.getSystemBattery(SECTION_TIMEOUT_MS) } }
                val config = async { runSection(emptyList<JsonElement>()) { repository.getConfigShow(SECTION_TIMEOUT_MS).configSections() } }
                val models = async { runSection(emptyList<JsonElement>()) { repository.getModelOptions(SECTION_TIMEOUT_MS).modelProviders() } }
                val usage = async { runSection<JsonElement?>(null) { repository.getUsageBars(SECTION_TIMEOUT_MS) } }
                SystemSections(
                    processes = processes.await(),
                    system = system.await(),
                    config = config.await(),
                    models = models.await(),
                    usage = usage.await(),
                )
            }

            _uiState.value = _uiState.value.copy(
                processes = results.processes.value,
                processesError = results.processes.error != null,
                system = results.system.value,
                systemError = results.system.error != null,
                config = results.config.value,
                configError = results.config.error != null,
                models = results.models.value,
                modelsError = results.models.error != null,
                usage = results.usage.value,
                usageError = results.usage.error != null,
                loading = false,
                error = listOfNotNull(
                    results.processes.error,
                    results.system.error,
                    results.config.error,
                    results.models.error,
                    results.usage.error,
                ).joinToString(" · ").ifEmpty { null },
            )
        }
    }

    fun killProcess(pid: Int) {
        viewModelScope.launch {
            try {
                repository.killProcess(pid)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "process.kill failed",
                )
            }
            loadSystem()
        }
    }
}

private data class SystemSections(
    val processes: SectionResult<List<JsonElement>>,
    val system: SectionResult<JsonElement?>,
    val config: SectionResult<List<JsonElement>>,
    val models: SectionResult<List<JsonElement>>,
    val usage: SectionResult<JsonElement?>,
)

/** Extract the `sections` array from a `config.show` response. */
private fun JsonElement.configSections(): List<JsonElement> {
    val obj = this as? JsonObject ?: return emptyList()
    return (obj["sections"] as? JsonArray)?.toList() ?: toListItems()
}

/** Extract the `providers` array from a `model.options` response. */
private fun JsonElement.modelProviders(): List<JsonElement> {
    val obj = this as? JsonObject ?: return emptyList()
    return (obj["providers"] as? JsonArray)?.toList() ?: toListItems()
}
