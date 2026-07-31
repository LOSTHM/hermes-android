package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class HubSkill(
    val name: String = "",
    val description: String = "",
    val identifier: String = "",
    val source: String = "",
    val trustLevel: String = "",
)

data class SkillsHubUiState(
    val query: String = "",
    val skills: List<HubSkill> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selectedName: String? = null,
    val skillContent: String? = null,
    val skillLoading: Boolean = false,
    val skillError: String? = null,
)

class SkillsHubViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SkillsHubUiState())
    val uiState: StateFlow<SkillsHubUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        HermesRestClient.init(application)
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadSkills(query)
        }
    }

    fun loadSkills(query: String = "") {
        val trimmed = query.trim()
        viewModelScope.launch {
            if (trimmed.isEmpty()) {
                _uiState.value = _uiState.value.copy(skills = emptyList(), loading = false, error = null)
                return@launch
            }
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("skills/hub/search", mapOf("q" to trimmed))
                _uiState.value = _uiState.value.copy(
                    skills = element.hubSkills(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Hub search failed",
                )
            }
        }
    }

    fun showSkill(name: String) {
        _uiState.value = _uiState.value.copy(
            selectedName = name,
            skillContent = null,
            skillLoading = true,
            skillError = null,
        )
        viewModelScope.launch {
            try {
                val element = HermesRestClient.getJson("skills/content", mapOf("name" to name))
                _uiState.value = _uiState.value.copy(
                    skillContent = element.skillContentText(),
                    skillLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    skillLoading = false,
                    skillError = e.message ?: "Failed to load skill content",
                )
            }
        }
    }

    fun closeSkill() {
        _uiState.value = _uiState.value.copy(
            selectedName = null,
            skillContent = null,
            skillLoading = false,
            skillError = null,
        )
    }
}

private fun JsonElement.hubSkills(): List<HubSkill> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["results"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name", "title", "identifier") ?: return@mapNotNull null
        HubSkill(
            name = name,
            description = obj.optString("description", "summary", "detail") ?: "",
            identifier = obj.optString("identifier", "id") ?: "",
            source = obj.optString("source") ?: "",
            trustLevel = obj.optString("trust_level", "trust") ?: "",
        )
    }
}

private fun JsonElement.skillContentText(): String? = when (this) {
    is JsonObject -> optString("content", "text")
    is JsonPrimitive -> contentOrNull
    else -> null
}
