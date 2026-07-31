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

data class PairedDevice(
    val platform: String,
    val userId: String,
    val userName: String = "",
    val approvedAt: Double = 0.0,
)

data class PendingPairing(
    val platform: String,
    val code: String = "",
    val userId: String = "",
    val userName: String = "",
    val ageMinutes: Long = 0,
)

data class PairingUiState(
    val approved: List<PairedDevice> = emptyList(),
    val pending: List<PendingPairing> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadPairing()
    }

    fun loadPairing() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val element = HermesRestClient.getJson("pairing")
                _uiState.value = _uiState.value.copy(
                    approved = element.pairingApproved(),
                    pending = element.pairingPending(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load pairing",
                )
            }
        }
    }

    fun revokePairing(device: PairedDevice) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                val body = buildJsonObject {
                    put("platform", JsonPrimitive(device.platform))
                    put("user_id", JsonPrimitive(device.userId))
                }.toString()
                HermesRestClient.post("pairing/revoke", body)
                loadPairing()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to revoke pairing",
                )
            }
        }
    }
}

private fun JsonElement.pairingApproved(): List<PairedDevice> {
    val obj = this as? JsonObject ?: return emptyList()
    val list = (obj["approved"] as? JsonArray)?.toList() ?: emptyList()
    return list.mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val platform = item.optString("platform", "source") ?: return@mapNotNull null
        val userId = item.optString("user_id", "userId", "id") ?: return@mapNotNull null
        PairedDevice(
            platform = platform,
            userId = userId,
            userName = item.optString("user_name", "name", "display_name") ?: "",
            approvedAt = item.optDouble("approved_at", "paired_at"),
        )
    }
}

private fun JsonElement.pairingPending(): List<PendingPairing> {
    val obj = this as? JsonObject ?: return emptyList()
    val list = (obj["pending"] as? JsonArray)?.toList() ?: emptyList()
    return list.mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val platform = item.optString("platform", "source") ?: return@mapNotNull null
        PendingPairing(
            platform = platform,
            code = item.optString("code") ?: "",
            userId = item.optString("user_id", "userId", "id") ?: "",
            userName = item.optString("user_name", "name", "display_name") ?: "",
            ageMinutes = item.optLong("age_minutes", "age"),
        )
    }
}
