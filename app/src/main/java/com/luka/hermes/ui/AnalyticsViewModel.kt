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

data class ModelUsage(
    val name: String,
    val provider: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val sessions: Long = 0,
    val apiCalls: Long = 0,
    val cost: Double = 0.0,
)

data class UsageTotals(
    val totalInput: Long = 0,
    val totalOutput: Long = 0,
    val totalCacheRead: Long = 0,
    val totalSessions: Long = 0,
    val totalApiCalls: Long = 0,
    val distinctModels: Long = 0,
    val estimatedCost: Double = 0.0,
) {
    val totalTokens: Long get() = totalInput + totalOutput
}

data class AnalyticsUiState(
    val models: List<ModelUsage> = emptyList(),
    val totals: UsageTotals = UsageTotals(),
    val loading: Boolean = false,
    val error: String? = null,
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val modelsEl = HermesRestClient.getJson("analytics/models")
                val usageEl = HermesRestClient.getJson("analytics/usage")
                _uiState.value = _uiState.value.copy(
                    models = modelsEl.analyticsModels(),
                    totals = usageEl.usageTotals() ?: modelsEl.usageTotals() ?: UsageTotals(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load analytics",
                )
            }
        }
    }
}

/** Extract the per-model usage list, tolerant of `{models: [...]}` or a bare array. */
internal fun JsonElement.analyticsModels(): List<ModelUsage> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["models"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("model", "name", "id", "label") ?: return@mapNotNull null
        ModelUsage(
            name = name,
            provider = obj.optString("provider", "billing_provider") ?: "",
            inputTokens = obj.optLong("input_tokens", "inputTokens", "input"),
            outputTokens = obj.optLong("output_tokens", "outputTokens", "output"),
            cacheReadTokens = obj.optLong("cache_read_tokens", "cache_read", "cached_tokens"),
            sessions = obj.optLong("sessions", "session_count"),
            apiCalls = obj.optLong("api_calls", "api_call_count", "requests"),
            cost = obj.optDouble("estimated_cost", "cost", "estimated_cost_usd"),
        )
    }
}

/** Extract totals, tolerant of `{totals: {...}}` or the totals object directly. */
internal fun JsonElement.usageTotals(): UsageTotals? {
    val obj = this as? JsonObject ?: return null
    val totals = (obj["totals"] as? JsonObject) ?: obj
    val hasAny = totals.keys.any { it.startsWith("total_") || it.startsWith("total") }
    if (!hasAny && totals != obj) return null
    return UsageTotals(
        totalInput = totals.optLong("total_input", "total_input_tokens", "input_tokens"),
        totalOutput = totals.optLong("total_output", "total_output_tokens", "output_tokens"),
        totalCacheRead = totals.optLong("total_cache_read", "total_cache_read_tokens", "cache_read_tokens"),
        totalSessions = totals.optLong("total_sessions", "sessions"),
        totalApiCalls = totals.optLong("total_api_calls", "api_calls"),
        distinctModels = totals.optLong("distinct_models", "total_models"),
        estimatedCost = totals.optDouble("total_estimated_cost", "estimated_cost", "total_cost"),
    )
}

/** Read the first present primitive key as a [Long], tolerating numeric or string encodings. */
internal fun JsonElement.optLong(vararg keys: String): Long {
    val obj = this as? JsonObject ?: return 0L
    for (key in keys) {
        val el = obj[key] ?: continue
        val s = (el as? JsonPrimitive)?.contentOrNull ?: continue
        val v = s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong() ?: continue
        return v
    }
    return 0L
}

/** Read the first present primitive key as a [Double], tolerating numeric or string encodings. */
internal fun JsonElement.optDouble(vararg keys: String): Double {
    val obj = this as? JsonObject ?: return 0.0
    for (key in keys) {
        val el = obj[key] ?: continue
        val s = (el as? JsonPrimitive)?.contentOrNull ?: continue
        val v = s.toDoubleOrNull() ?: continue
        return v
    }
    return 0.0
}
