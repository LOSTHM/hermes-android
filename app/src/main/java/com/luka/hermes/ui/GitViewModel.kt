package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

data class GitUiState(
    val status: JsonElement? = null,
    val branches: JsonElement? = null,
    val baseBranches: JsonElement? = null,
    val loading: Boolean = false,
    val error: String? = null,
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
            _uiState.value = _uiState.value.copy(loading = true, error = null)

            var status: JsonElement? = null
            var branches: JsonElement? = null
            var baseBranches: JsonElement? = null
            val errors = mutableListOf<String>()

            try { status = HermesRestClient.getJson("git/status") }
            catch (e: Exception) { errors += "status: ${e.message ?: "failed"}" }

            try { branches = HermesRestClient.getJson("git/branches") }
            catch (e: Exception) { errors += "branches: ${e.message ?: "failed"}" }

            try { baseBranches = HermesRestClient.getJson("git/base-branches") }
            catch (e: Exception) { errors += "base-branches: ${e.message ?: "failed"}" }

            _uiState.value = _uiState.value.copy(
                status = status,
                branches = branches,
                baseBranches = baseBranches,
                loading = false,
                error = errors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }
}
