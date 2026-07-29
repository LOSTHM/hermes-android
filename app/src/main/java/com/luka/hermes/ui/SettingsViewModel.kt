package com.luka.hermes.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.settingsDataStore
    private val repository = HermesClient.repository

    /** The saved token (empty if none). */
    val token: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.TOKEN] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun saveToken(newToken: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.TOKEN] = newToken
            }
            _saved.value = true
            // Connect after saving token
            try {
                repository.connect(newToken)
            } catch (_: Exception) {
                // connection error is visible via connectionState
            }
        }
    }

    fun clearSavedFlag() {
        _saved.value = false
    }

    fun disconnect() {
        repository.disconnect()
    }
}
