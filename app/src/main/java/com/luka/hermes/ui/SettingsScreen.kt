package com.luka.hermes.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luka.hermes.gateway.ConnectionState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onTokenConfigured: () -> Unit,
) {
    val token by viewModel.token.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    var localToken by remember(token) { mutableStateOf(token) }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.clearSavedFlag()
            onTokenConfigured()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Hermes API Token",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = localToken,
            onValueChange = { localToken = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste your token here") },
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveToken(localToken.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = localToken.isNotBlank(),
        ) {
            Text("Save & Connect")
        }

        Spacer(Modifier.height(24.dp))

        // Connection status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    ConnectionState.Open -> Color(0xFF1B5E20).let { MaterialTheme.colorScheme.surfaceVariant }
                    ConnectionState.Error -> Color(0xFFB71C1C).let { MaterialTheme.colorScheme.surfaceVariant }
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                val (statusText, statusColor) = when (connectionState) {
                    ConnectionState.Idle -> "Idle" to Color.Gray
                    ConnectionState.Connecting -> "Connecting…" to Color(0xFFFFA000)
                    ConnectionState.Open -> "Connected" to Color(0xFF4CAF50)
                    ConnectionState.Closed -> "Disconnected" to Color.Gray
                    ConnectionState.Error -> "Error" to Color(0xFFE53935)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\u25CF",
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                    if (connectionState == ConnectionState.Connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}
