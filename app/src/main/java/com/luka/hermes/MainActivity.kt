package com.luka.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luka.hermes.ui.HermesNavHost
import com.luka.hermes.ui.HermesTheme
import com.luka.hermes.ui.SettingsKeys
import com.luka.hermes.ui.ThemeMode
import com.luka.hermes.ui.settingsDataStore
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var ready by remember { mutableStateOf(false) }
            var initialRoute by remember { mutableStateOf("settings") }
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                val prefs = applicationContext.settingsDataStore.data.first()
                val token = prefs[SettingsKeys.TOKEN] ?: ""
                val apiKey = prefs[SettingsKeys.API_KEY] ?: ""
                val mode = prefs[SettingsKeys.CHAT_MODE] ?: "hermes"
                val theme = prefs[SettingsKeys.THEME_MODE] ?: "SYSTEM"

                themeMode = try { ThemeMode.valueOf(theme) } catch (_: Exception) { ThemeMode.SYSTEM }
                initialRoute = when {
                    mode == "direct" && apiKey.isNotBlank() -> "sessions"
                    token.isNotBlank() -> "sessions"
                    else -> "settings"
                }
                ready = true
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
