package com.luka.hermes.ui

import com.luka.hermes.gateway.HermesRestClient
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class GitUiState(
    val status: JsonElement = JsonNull,
    val branches: JsonElement = JsonNull,
    val baseBranches: JsonElement = JsonNull,
    val repoPath: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val stageBusy: Boolean = false,
    val stageError: String? = null,
)

class GitViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadGit()
    }

    fun loadGit() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, stageBusy = false)

            var status: JsonElement = JsonNull
            var branches: JsonElement = JsonNull
            var baseBranches: JsonElement = JsonNull
            var repoPath = _uiState.value.repoPath
            val errors = mutableListOf<String>()

            try { status = HermesRestClient.getJson("git/status") }
            catch (e: Exception) { errors += "status: ${e.message ?: "failed"}" }

            try { branches = HermesRestClient.getJson("git/branches") }
            catch (e: Exception) { errors += "branches: ${e.message ?: "failed"}" }

            try { baseBranches = HermesRestClient.getJson("git/base-branches") }
            catch (e: Exception) { errors += "base-branches: ${e.message ?: "failed"}" }

            if (repoPath.isBlank()) {
                try {
                    val cwd = HermesRestClient.getJson("fs/default-cwd")
                    repoPath = (cwd as? JsonObject)?.optString("cwd") ?: ""
                } catch (_: Exception) {
                }
            }

            _uiState.value = _uiState.value.copy(
                status = status,
                branches = branches,
                baseBranches = baseBranches,
                repoPath = repoPath,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }

    fun stageFile(filePath: String) {
        toggleStage(filePath, stage = true)
    }

    fun unstageFile(filePath: String) {
        toggleStage(filePath, stage = false)
    }

    private fun toggleStage(filePath: String, stage: Boolean) {
        val repoPath = _uiState.value.repoPath
        if (repoPath.isBlank()) {
            _uiState.value = _uiState.value.copy(
                stageError = "No repository path available",
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stageBusy = true, stageError = null)
            try {
                val body = JsonObject(
                    mapOf(
                        "path" to JsonPrimitive(repoPath),
                        "file" to JsonPrimitive(filePath),
                    ),
                ).toString()
                HermesRestClient.post(
                    path = if (stage) "git/review/stage" else "git/review/unstage",
                    body = body,
                )
                loadGit()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    stageBusy = false,
                    stageError = e.message ?: "Stage/unstage failed",
                )
            }
        }
    }
}
