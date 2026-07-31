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
import kotlinx.serialization.json.JsonObject

data class Profile(
    val name: String,
    val isDefault: Boolean = false,
    val description: String? = null,
    val model: String? = null,
    val provider: String? = null,
)

data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)

            var profiles: List<Profile> = emptyList()
            var active: String? = null
            val errors = mutableListOf<String>()

            try { profiles = HermesRestClient.getJson("profiles").profilesList() }
            catch (e: Exception) { errors += "profiles: ${e.message ?: "failed"}" }

            try { active = HermesRestClient.getJson("profiles/active").activeProfileName() }
            catch (e: Exception) { errors += "active: ${e.message ?: "failed"}" }

            _uiState.value = _uiState.value.copy(
                profiles = profiles,
                activeProfile = active,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }
}

private fun JsonElement.profilesList(): List<Profile> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["profiles"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name", "id", "key") ?: return@mapNotNull null
        Profile(
            name = name,
            isDefault = obj.optString("is_default", "isDefault")?.let { it == "true" || it == "1" } == true,
            description = obj.optString("description", "desc", "summary"),
            model = obj.optString("model", "model_id"),
            provider = obj.optString("provider"),
        )
    }
}

private fun JsonElement.activeProfileName(): String? {
    val obj = this as? JsonObject ?: return null
    return obj.optString("active")?.takeIf { it.isNotBlank() }
}
