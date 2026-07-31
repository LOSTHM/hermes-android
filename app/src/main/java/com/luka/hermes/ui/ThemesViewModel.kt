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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

data class ThemeItem(
    val name: String,
    val label: String? = null,
    val description: String? = null,
)

data class ThemesUiState(
    val themes: List<ThemeItem> = emptyList(),
    val active: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Lists dashboard themes via GET /api/dashboard/themes, which returns
 * `{"themes": [{name, label, description, ...}], "active": "default"}`.
 */
class ThemesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ThemesUiState())
    val uiState: StateFlow<ThemesUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
    }

    fun loadThemes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("dashboard/themes")
                val obj = element as? JsonObject
                val themes = obj?.get("themes")?.jsonArray
                    ?: (element as? JsonArray)
                    ?: emptyList()
                val active = obj?.get("active")?.primitiveString()

                _uiState.value = _uiState.value.copy(
                    themes = themes.mapNotNull { it.toThemeItem() },
                    active = active,
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load themes",
                )
            }
        }
    }

    private fun JsonElement?.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement.toThemeItem(): ThemeItem? {
        return when (this) {
            is JsonObject -> {
                val name = this.optString("name", "id") ?: return null
                ThemeItem(
                    name = name,
                    label = this.optString("label", "title"),
                    description = this.optString("description", "desc", "summary"),
                )
            }
            is JsonPrimitive -> {
                val name = contentOrNull ?: return null
                ThemeItem(name = name)
            }
            else -> null
        }
    }
}

private fun JsonObject.optString(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonPrimitive) {
            (el.contentOrNull ?: continue).let { if (it.isNotBlank()) return it }
        } else {
            return el.toString()
        }
    }
    return null
}
