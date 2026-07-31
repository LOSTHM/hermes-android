package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Worktree(
    val path: String,
    val branch: String? = null,
    val isMain: Boolean = false,
    val detached: Boolean = false,
    val locked: Boolean = false,
)

data class WorktreesUiState(
    val worktrees: List<Worktree> = emptyList(),
    val repoPath: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val adding: Boolean = false,
    val addError: String? = null,
    val removing: String? = null,
    val removeError: String? = null,
)

class WorktreesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WorktreesUiState())
    val uiState: StateFlow<WorktreesUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadWorktrees()
    }

    fun loadWorktrees() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null, removeError = null)
            var repoPath = _uiState.value.repoPath
            if (repoPath.isBlank()) {
                try {
                    val cwd = HermesRestClient.getJson("fs/default-cwd")
                    repoPath = (cwd as? JsonObject)?.optString("cwd") ?: ""
                } catch (_: Exception) {
                }
            }
            if (repoPath.isBlank()) {
                _uiState.value = _uiState.value.copy(loading = false, error = "No repository path available")
                return@launch
            }
            try {
                val element = HermesRestClient.getJson(
                    "git/worktrees",
                    queryParams = mapOf("path" to repoPath),
                )
                _uiState.value = _uiState.value.copy(
                    worktrees = element.worktreeList(),
                    repoPath = repoPath,
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    repoPath = repoPath,
                    loading = false,
                    error = e.message ?: "Failed to load worktrees",
                )
            }
        }
    }

    fun addWorktree(name: String, onDone: (Boolean) -> Unit = {}) {
        val trimmed = name.trim()
        val repoPath = _uiState.value.repoPath
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(addError = "Enter a path")
            return
        }
        if (repoPath.isBlank()) {
            _uiState.value = _uiState.value.copy(addError = "No repository path available")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(adding = true, addError = null)
            try {
                val body = buildJsonObject {
                    put("path", repoPath)
                    put("name", trimmed)
                }.toString()
                HermesRestClient.post("git/worktree/add", body)
                _uiState.value = _uiState.value.copy(adding = false)
                loadWorktrees()
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    adding = false,
                    addError = e.message ?: "Failed to add worktree",
                )
                onDone(false)
            }
        }
    }

    fun removeWorktree(worktreePath: String) {
        val repoPath = _uiState.value.repoPath
        if (repoPath.isBlank() || worktreePath.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(removing = worktreePath, removeError = null)
            try {
                val body = buildJsonObject {
                    put("path", repoPath)
                    put("worktreePath", worktreePath)
                    put("force", JsonPrimitive(false))
                }.toString()
                HermesRestClient.post("git/worktree/remove", body)
                _uiState.value = _uiState.value.copy(removing = null)
                loadWorktrees()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    removing = null,
                    removeError = e.message ?: "Failed to remove worktree",
                )
            }
        }
    }
}

private fun JsonElement.worktreeList(): List<Worktree> {
    val entries = when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf("worktrees", "items", "results", "data", "list")
            .firstNotNullOfOrNull { key -> (this[key] as? JsonArray) }?.toList() ?: emptyList()
        else -> emptyList()
    }
    return entries.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val path = obj.optString("path", "worktreePath", "worktree", "dir") ?: return@mapNotNull null
        Worktree(
            path = path,
            branch = obj.optString("branch", "head", "ref"),
            isMain = obj.optString("isMain", "is_main", "main", "primary")
                ?.let { it == "true" || it == "1" } == true,
            detached = obj.optString("detached", "is_detached", "isDetached")
                ?.let { it == "true" || it == "1" } == true,
            locked = obj.optString("locked", "is_locked", "isLocked")
                ?.let { it == "true" || it == "1" } == true,
        )
    }
}
