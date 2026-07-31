package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ProjectRepo(
    val root: String,
    val label: String = "",
    val sessions: Long = 0,
    val lastActive: Double = 0.0,
)

data class ProjectsUiState(
    val repos: List<ProjectRepo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = repository.discoverRepos()
                _uiState.value = _uiState.value.copy(
                    repos = element.projectRepos(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load projects",
                )
            }
        }
    }
}

private fun JsonElement.projectRepos(): List<ProjectRepo> {
    val obj = this as? JsonObject ?: return emptyList()
    val list = (obj["repos"] as? JsonArray)?.toList() ?: emptyList()
    return list.mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val root = item.optString("root", "path", "repo", "dir") ?: return@mapNotNull null
        ProjectRepo(
            root = root,
            label = item.optString("label", "name", "title") ?: "",
            sessions = item.optLong("sessions", "session_count", "count"),
            lastActive = item.optDouble("last_active", "last_seen", "lastActive"),
        )
    }
}
