package com.luka.hermes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val exportJson by viewModel.exportJson.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.clearSavedFlag()
            onConfigured()
        }
    }

    // When an export finishes, hand the JSON to the system share sheet.
    LaunchedEffect(exportJson) {
        if (exportJson != null) {
            ShareHelper.shareText(context, "Hermes session export", exportJson!!)
            viewModel.clearExportJson()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Theme Section ──
            SettingsSectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.persistThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.name) }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Chat Mode Section ──
            SettingsSectionHeader("Connection")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.chatMode == ChatMode.HERMES,
                    onClick = { viewModel.updateChatMode(ChatMode.HERMES) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Daemon") }
                SegmentedButton(
                    selected = uiState.chatMode == ChatMode.DIRECT,
                    onClick = { viewModel.updateChatMode(ChatMode.DIRECT) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Direct API") }
            }

            Spacer(Modifier.height(16.dp))

            // ── Hermes Mode ──
            if (uiState.chatMode == ChatMode.HERMES) {
                SettingsSectionHeader("Hermes Daemon")
                OutlinedTextField(
                    value = uiState.hermesToken,
                    onValueChange = viewModel::updateHermesToken,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token") },
                    placeholder = { Text("Paste your token") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                ConnectionStatusCard(connectionState)

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::exportCurrentSession,
                    enabled = !uiState.exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.exporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (uiState.exporting) "Exporting…" else "Export Current Session")
                }
                uiState.exportError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── Direct API Mode ──
            if (uiState.chatMode == ChatMode.DIRECT) {
                SettingsSectionHeader("API Configuration")
                OutlinedTextField(
                    value = uiState.apiBaseUrl,
                    onValueChange = viewModel::updateApiBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.apiModel,
                    onValueChange = viewModel::updateApiModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    placeholder = { Text("qwen3.6") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = viewModel::testDirectConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.testing && uiState.apiBaseUrl.isNotBlank() && uiState.apiKey.isNotBlank(),
                ) {
                    if (uiState.testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (uiState.testing) "Testing…" else "Test Connection")
                }

                uiState.testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(text = result, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Parameters
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Parameters", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Temperature: %.1f".format(uiState.temperature), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.temperature,
                            onValueChange = viewModel::updateTemperature,
                            valueRange = 0f..2f,
                            steps = 19,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Max Tokens: ${uiState.maxTokens}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.maxTokens.toFloat(),
                            onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                            valueRange = 256f..8192f,
                            steps = 30,
                        )
                    }
                }

                // System Prompt
                Spacer(Modifier.height(16.dp))
                Text("System Prompt", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.systemPrompt,
                    onValueChange = viewModel::updateSystemPrompt,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
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
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (text, color) = when (state) {
                ConnectionState.Idle -> "Idle" to MaterialTheme.colorScheme.outline
                ConnectionState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.tertiary
                ConnectionState.Open -> "Connected" to MaterialTheme.colorScheme.primary
                ConnectionState.Closed -> "Disconnected" to MaterialTheme.colorScheme.outline
                ConnectionState.Error -> "Error" to MaterialTheme.colorScheme.error
            }
            Text("\u25CF", color = color)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
