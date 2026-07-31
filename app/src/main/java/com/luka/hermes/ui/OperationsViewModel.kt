package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

data class OperationsUiState(
    val doctorResult: JsonElement = JsonNull,
    val backupResult: JsonElement = JsonNull,
    /** `"doctor"` or `"backup"` while an operation is running, else null. */
    val busy: String? = null,
    val error: String? = null,
)

/**
 * Drives the daemon's maintenance operations (both spawn an async `hermes`
 * subprocess, so the REST replies are quick `{ok, pid, name}` acks rather
 * than the operation output itself).
 */
class OperationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        HermesRestClient.init(application)
    }

    /** Trigger `hermes doctor` via POST /api/ops/doctor. */
    fun runDoctor() {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = "doctor", error = null)
            try {
                val result = HermesRestClient.post("ops/doctor").toJsonElement()
                _uiState.value = _uiState.value.copy(
                    doctorResult = result,
                    busy = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    busy = null,
                    error = "Doctor failed: ${e.message ?: "unknown error"}",
                )
            }
        }
    }

    /** Create a daemon backup via POST /api/ops/backup (timestamped zip). */
    fun backup() {
        if (_uiState.value.busy != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = "backup", error = null)
            try {
                val result = HermesRestClient.post("ops/backup").toJsonElement()
                _uiState.value = _uiState.value.copy(
                    backupResult = result,
                    busy = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    busy = null,
                    error = "Backup failed: ${e.message ?: "unknown error"}",
                )
            }
        }
    }

    /** Best-effort body parse; non-JSON falls back to a string node. */
    private fun String.toJsonElement(): JsonElement {
        if (isBlank()) return JsonNull
        return try {
            json.parseToJsonElement(this)
        } catch (_: Exception) {
            JsonPrimitive(this)
        }
    }
}
