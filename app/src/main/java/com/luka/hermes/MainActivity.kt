package com.luka.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luka.hermes.ui.HermesClient
import com.luka.hermes.ui.HermesNavHost
import com.luka.hermes.ui.HermesTheme
import com.luka.hermes.ui.SettingsKeys
import com.luka.hermes.ui.ThemeMode
import com.luka.hermes.ui.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var ready by remember { mutableStateOf(false) }
            var initialRoute by remember { mutableStateOf("settings") }

            // Reactively observe theme mode from DataStore so settings changes apply immediately.
            val themeMode by applicationContext.settingsDataStore.data
                .map { prefs -> prefs[SettingsKeys.THEME_MODE] ?: "SYSTEM" }
                .map { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM) }
                .collectAsState(initial = ThemeMode.SYSTEM)

            androidx.compose.runtime.LaunchedEffect(Unit) {
                val prefs = applicationContext.settingsDataStore.data.first()
                val token = prefs[SettingsKeys.TOKEN] ?: ""
                val apiKey = prefs[SettingsKeys.API_KEY] ?: ""
                val mode = prefs[SettingsKeys.CHAT_MODE] ?: "hermes"

                initialRoute = when {
                    mode == "direct" && apiKey.isNotBlank() -> "sessions"
                    token.isNotBlank() -> "sessions"
                    else -> "settings"
                }
                ready = true

                // Auto-connect on startup so Tools/System RPCs work without the
                // user having to tap Save again. Idempotent: GatewayClient.connect
                // is a no-op when already Open/Connecting.
                if (token.isNotBlank()) {
                    try {
                        HermesClient.repository.connect(token)
                    } catch (_: Exception) {
                        // Connection failures surface via connectionState.
                    }
                }
            }

            if (!ready) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                HermesTheme(themeMode = themeMode) {
                    HermesNavHost(initialRoute = initialRoute)
                }
            }
        }
    }
}
