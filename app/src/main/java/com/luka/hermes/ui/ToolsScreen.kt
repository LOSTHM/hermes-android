package com.luka.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onOpenGit: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenMcp: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenLearning: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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

            if (uiState.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            ToolsSectionCard("Cron Jobs", uiState.cronJobs, { it.optString("title", "name", "job", "id") }) { it.cronSubtitle() }
            ToolsSectionCard("Skills", uiState.skills, { it.optString("name", "title", "id") }) { it.optString("description") }
            ToolsSectionCard("Plugins", uiState.plugins, { it.optString("name", "title", "id") }) { it.optString("version") }
            ToolsSectionCard("Agents", uiState.agents, { it.optString("name", "id") })
            ToolsSectionCard("Tools", uiState.tools, { it.optString("name", "title", "id") })
            ToolsSectionCard("Toolsets", uiState.toolsets, { it.optString("name", "title", "id") })

            AdvancedPanelsCard(
                onOpenGit = onOpenGit,
                onOpenFiles = onOpenFiles,
                onOpenMcp = onOpenMcp,
                onOpenProfiles = onOpenProfiles,
                onOpenLearning = onOpenLearning,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToolsSectionCard(
    title: String,
    items: List<JsonElement>,
    titleOf: (JsonElement) -> String?,
    subtitleOf: (JsonElement) -> String? = { null },
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEach { item ->
                    val name = titleOf(item) ?: "Unknown"
                    val subtitle = subtitleOf(item)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("•", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun AdvancedPanelsCard(
    onOpenGit: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenLearning: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "Advanced Panels",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            AdvancedPanelItem("Git", onOpenGit)
            AdvancedPanelItem("Files", onOpenFiles)
            AdvancedPanelItem("MCP Servers", onOpenMcp)
            AdvancedPanelItem("Profiles", onOpenProfiles)
            AdvancedPanelItem("Learning", onOpenLearning)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun AdvancedPanelItem(
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun JsonElement.cronSubtitle(): String? {
    val status = optString("status", "enabled")
    val schedule = optString("schedule", "cron", "expression", "interval")
    return listOfNotNull(status, schedule).joinToString(" · ").ifBlank { null }
}
