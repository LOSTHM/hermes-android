package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

data class SystemUiState(
    val processes: List<JsonElement> = emptyList(),
    val system: JsonElement? = null,
    val config: JsonElement? = null,
    val models: List<JsonElement> = emptyList(),
    val usage: JsonElement? = null,
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
            var system: JsonElement? = null
            var config: JsonElement? = null
            var models: List<JsonElement> = emptyList()
            var usage: JsonElement? = null

            try { processes = repository.listProcesses().toListItems() }
            catch (e: Exception) { errors += e.message ?: "process.list failed" }

            try { system = repository.getSystemBattery() }
            catch (e: Exception) { errors += e.message ?: "system.battery failed" }

            try { config = repository.getConfigShow() }
            catch (e: Exception) { errors += e.message ?: "config.show failed" }

            try { models = repository.getModelOptions().toListItems() }
            catch (e: Exception) { errors += e.message ?: "model.options failed" }

            try { usage = repository.getUsageBars() }
            catch (e: Exception) { errors += e.message ?: "usage.bars failed" }

            _uiState.value = _uiState.value.copy(
                processes = processes,
                system = system,
                config = config,
                models = models,
                usage = usage,
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
