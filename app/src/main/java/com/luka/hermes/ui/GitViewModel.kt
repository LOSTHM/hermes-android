package com.luka.hermes.ui

import com.luka.hermes.gateway.HermesRestClient
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class GitRepo(
    val root: String,
    val label: String,
)

data class GitUiState(
    val status: JsonElement = JsonNull,
    val branches: JsonElement = JsonNull,
    val baseBranches: JsonElement = JsonNull,
    val repos: List<GitRepo> = emptyList(),
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

            var repoPath = _uiState.value.repoPath
            var repos = _uiState.value.repos
            var status: JsonElement = JsonNull
            var branches: JsonElement = JsonNull
            var baseBranches: JsonElement = JsonNull
            val errors = mutableListOf<String>()

            if (repoPath.isBlank()) {
                // 1. Try the daemon's default working directory first.
                val cwdPath = try {
                    val cwd = HermesRestClient.getJson("fs/default-cwd")
                    (cwd as? JsonObject)?.optString("cwd") ?: ""
                } catch (_: Exception) {
                    ""
                }

                // 2. If it isn't a git repo, fall back to repository discovery.
                if (cwdPath.isNotBlank() && isGitRepo(cwdPath)) {
                    repoPath = cwdPath
                } else {
                    repos = discoverRepos()
                    repoPath = repos.firstOrNull()?.root ?: ""
                }
            }

            if (repoPath.isBlank()) {
                errors += "No git repository available"
            } else {
                val params = mapOf("path" to repoPath)
                try { status = HermesRestClient.getJson("git/status", params) }
                catch (e: Exception) { errors += "status: ${e.message ?: "failed"}" }

                try { branches = HermesRestClient.getJson("git/branches", params) }
                catch (e: Exception) { errors += "branches: ${e.message ?: "failed"}" }

                try { baseBranches = HermesRestClient.getJson("git/base-branches", params) }
                catch (e: Exception) { errors += "base-branches: ${e.message ?: "failed"}" }
            }

            _uiState.value = _uiState.value.copy(
                status = status,
                branches = branches,
                baseBranches = baseBranches,
                repos = repos,
                repoPath = repoPath,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }

    /** Switch the active repository and reload status/branches for it. */
    fun selectRepo(path: String) {
        if (path.isBlank() || path == _uiState.value.repoPath) return
        _uiState.value = _uiState.value.copy(repoPath = path)
        loadGit()
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

/** True when git/status succeeds for [path] (i.e. it is a real git repo). */
private suspend fun isGitRepo(path: String): Boolean {
    if (path.isBlank()) return false
    return try {
        HermesRestClient.getJson("git/status", mapOf("path" to path))
        true
    } catch (_: Exception) {
        false
    }
}

/** List discoverable git repos via `projects.discover_repos`. */
private suspend fun discoverRepos(): List<GitRepo> {
    return try {
        val obj = HermesClient.repository.discoverRepos() as? JsonObject ?: return emptyList()
        val list = obj["repos"] as? JsonArray ?: return emptyList()
        list.mapNotNull { entry ->
            val repo = entry as? JsonObject ?: return@mapNotNull null
            val root = repo.optString("root")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = repo.optString("label", "name")?.takeIf { it.isNotBlank() } ?: root
            GitRepo(root = root, label = label)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
