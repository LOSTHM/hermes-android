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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class Webhook(
    val name: String,
    val url: String = "",
    val events: List<String> = emptyList(),
    val description: String = "",
    val deliver: String = "",
    val enabled: Boolean = true,
)

data class WebhooksUiState(
    val webhooks: List<Webhook> = emptyList(),
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val creating: Boolean = false,
    val createError: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

class WebhooksViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WebhooksUiState())
    val uiState: StateFlow<WebhooksUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadWebhooks()
    }

    fun loadWebhooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("webhooks")
                _uiState.value = _uiState.value.copy(
                    webhooks = element.webhooks(),
                    enabled = element.enabledFlag(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load webhooks",
                )
            }
        }
    }

    /** Create a subscription with the given name and comma/space-separated event types. */
    fun createWebhook(name: String, event: String, onDone: (Boolean) -> Unit = {}) {
        val trimmedName = name.trim()
        val events = event.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedName.isEmpty() || events.isEmpty()) {
            _uiState.value = _uiState.value.copy(createError = "Enter a name and at least one event")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(creating = true, createError = null)
            try {
                val body = buildJsonObject {
                    put("name", trimmedName)
                    put("events", buildJsonArray { events.forEach { add(it) } })
                }.toString()
                HermesRestClient.post("webhooks", body)
                _uiState.value = _uiState.value.copy(creating = false)
                loadWebhooks()
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    creating = false,
                    createError = e.message ?: "Failed to create webhook",
                )
                onDone(false)
            }
        }
    }

    fun deleteWebhook(name: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, deleteError = null)
            try {
                HermesRestClient.delete("webhooks/${name.trim()}")
                _uiState.value = _uiState.value.copy(deleting = false)
                loadWebhooks()
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    deleteError = e.message ?: "Failed to delete webhook",
                )
                onDone(false)
            }
        }
    }
}

private fun JsonElement.webhooks(): List<Webhook> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["subscriptions"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name", "id") ?: return@mapNotNull null
        Webhook(
            name = name,
            url = obj.optString("url", "webhook_url", "endpoint") ?: "",
            events = obj.events(),
            description = obj.optString("description", "desc") ?: "",
            deliver = obj.optString("deliver") ?: "",
            enabled = obj.optString("enabled", "is_enabled", "active")
                ?.let { it == "true" || it == "1" } != false,
        )
    }
}

private fun JsonObject.events(): List<String> {
    val el = this["events"] ?: return emptyList()
    return when (el) {
        is JsonArray -> el.mapNotNull { it.scalarString() }
        else -> listOfNotNull(el.scalarString())
    }
}

private fun JsonElement.scalarString(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    else -> optString()
}

private fun JsonElement.enabledFlag(): Boolean {
    val obj = this as? JsonObject ?: return true
    return obj.optString("enabled", "is_enabled", "active")?.let { it == "true" || it == "1" } != false
}
