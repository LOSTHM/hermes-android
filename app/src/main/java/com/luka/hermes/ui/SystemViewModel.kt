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

            val errors = mutableListOf<String>()

            var processes: List<JsonElement> = emptyList()
            var processesError = false
            var system: JsonElement? = null
            var systemError = false
            var config: JsonElement? = null
            var configError = false
            var models: List<JsonElement> = emptyList()
            var modelsError = false
            var usage: JsonElement? = null
            var usageError = false

            // Each call is guarded independently so one failing section
            // (e.g. process.list → 4001 "session not found" on serve) can
            // never block the rest of the page from rendering.
            try { processes = repository.listProcesses().toListItems() }
            catch (e: Exception) { processesError = true; errors += e.message ?: "process.list failed" }

            try { system = repository.getSystemBattery() }
            catch (e: Exception) { systemError = true; errors += e.message ?: "system.battery failed" }

            try { config = repository.getConfigShow().configSections() }
            catch (e: Exception) { configError = true; errors += e.message ?: "config.show failed" }

            try { models = repository.getModelOptions().modelProviders() }
            catch (e: Exception) { modelsError = true; errors += e.message ?: "model.options failed" }

            try { usage = repository.getUsageBars() }
            catch (e: Exception) { usageError = true; errors += e.message ?: "usage.bars failed" }

            _uiState.value = _uiState.value.copy(
                processes = processes,
                processesError = processesError,
                system = system,
                systemError = systemError,
                config = config,
                configError = configError,
                models = models,
                modelsError = modelsError,
                usage = usage,
                usageError = usageError,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
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
