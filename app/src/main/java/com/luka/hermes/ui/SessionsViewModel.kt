package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.GatewayException
import com.luka.hermes.gateway.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SessionsViewModel : ViewModel() {

    private val repository = HermesClient.repository

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    val connectionState = repository.connectionState

    init {
        loadSessions()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val sessions = repository.listSessions()
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    loading = false,
                )
            } catch (e: GatewayException) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load sessions",
                )
            }
        }
    }

    fun createSession(title: String = "New Chat", onCreated: (Session) -> Unit) {
        viewModelScope.launch {
            try {
                val session = repository.createSession(title)
                loadSessions()
                onCreated(session)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to create session",
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteSession(sessionId)
                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to delete session",
                )
            }
        }
    }
}
