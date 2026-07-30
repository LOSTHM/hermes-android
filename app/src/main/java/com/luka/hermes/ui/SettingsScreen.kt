package com.luka.hermes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onConfigured: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.clearSavedFlag()
            onConfigured()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // ── Theme ──
        Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = uiState.themeMode == ThemeMode.SYSTEM, onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) }) { Text("System") }
            SegmentedButton(selected = uiState.themeMode == ThemeMode.LIGHT, onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) }) { Text("Light") }
            SegmentedButton(selected = uiState.themeMode == ThemeMode.DARK, onClick = { viewModel.updateThemeMode(ThemeMode.DARK) }) { Text("Dark") }
        }

        Spacer(Modifier.height(24.dp))

        // ── Chat Mode ──
        Text(text = "Chat Mode", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = uiState.chatMode == ChatMode.HERMES, onClick = { viewModel.updateChatMode(ChatMode.HERMES) }) { Text("Hermes") }
            SegmentedButton(selected = uiState.chatMode == ChatMode.DIRECT, onClick = { viewModel.updateChatMode(ChatMode.DIRECT) }) { Text("Direct API") }
        }

        Spacer(Modifier.height(24.dp))

        // ── Hermes mode ──
        if (uiState.chatMode == ChatMode.HERMES) {
            Text(text = "Hermes API Token", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.hermesToken,
                onValueChange = viewModel::updateHermesToken,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your token here") },
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            ConnectionStatusCard(connectionState)
        }

        // ── Direct API mode ──
        if (uiState.chatMode == ChatMode.DIRECT) {
            // Base URL
            Text(text = "API Base URL", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.apiBaseUrl,
                onValueChange = viewModel::updateApiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://llmapi.tripln.top:5000/v1") },
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // API Key
            Text(text = "API Key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // Model dropdown
            Text(text = "Model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ModelDropdown(
                models = uiState.apiModels,
                selected = uiState.apiModel,
                onSelected = viewModel::updateApiModel,
            )
            Spacer(Modifier.height(16.dp))

            // Test connection
            OutlinedButton(
                onClick = viewModel::testDirectConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.testing && uiState.apiBaseUrl.isNotBlank() && uiState.apiKey.isNotBlank(),
            ) {
                if (uiState.testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.testing) "Testing…" else "Test Connection")
            }

            uiState.testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(text = result, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Parameters", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))

                    // Temperature
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperature: %.1f".format(uiState.temperature), style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = uiState.temperature,
                        onValueChange = viewModel::updateTemperature,
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    // Max tokens
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Max Tokens: ${uiState.maxTokens}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = uiState.maxTokens.toFloat(),
                        onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                        valueRange = 256f..8192f,
                        steps = 30,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // System prompt
            Text(text = "System Prompt", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.systemPrompt,
                onValueChange = viewModel::updateSystemPrompt,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("You are a helpful assistant...") },
                maxLines = 6,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::saveAndConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save & Continue")
        }

        Spacer(Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(models: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = if (models.isNotEmpty()) models else listOf(selected.ifBlank { "qwen3.6" })

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected.ifBlank { items.first() },
            onValueChange = onSelected,
            readOnly = models.isNotEmpty(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val (statusText, statusColor) = when (connectionState) {
                ConnectionState.Idle -> "Idle" to Color.Gray
                ConnectionState.Connecting -> "Connecting…" to Color(0xFFFFA000)
                ConnectionState.Open -> "Connected" to Color(0xFF4CAF50)
                ConnectionState.Closed -> "Disconnected" to Color.Gray
                ConnectionState.Error -> "Error" to Color(0xFFE53935)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u25CF", color = statusColor)
                Spacer(Modifier.width(8.dp))
                Text(text = statusText)
                if (connectionState == ConnectionState.Connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(start = 8.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
