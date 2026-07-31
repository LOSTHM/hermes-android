package com.luka.hermes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCleanupScreen(
    viewModel: SessionCleanupViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var pruneDialog by remember { mutableStateOf(false) }
    var emptyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Cleanup", style = MaterialTheme.typography.headlineSmall) },
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { viewModel.loadStats() }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!uiState.loading && uiState.error == null) {
                StatsGrid(uiState.stats)

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { pruneDialog = true },
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Prune old sessions")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { emptyDialog = true },
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Empty all empty sessions", color = MaterialTheme.colorScheme.error)
                }

                uiState.lastAction?.let { action ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (pruneDialog) {
        AlertDialog(
            onDismissRequest = { pruneDialog = false },
            title = { Text("Prune sessions", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Delete ended sessions older than 90 days? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pruneDialog = false
                    viewModel.prune()
                }) { Text("Prune") }
            },
            dismissButton = {
                TextButton(onClick = { pruneDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (emptyDialog) {
        AlertDialog(
            onDismissRequest = { emptyDialog = false },
            title = { Text("Empty sessions", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Delete every empty session (0 messages, ended, not archived)? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    emptyDialog = false
                    viewModel.empty()
                }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { emptyDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatsGrid(stats: SessionStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(label = "Total", value = stats.total.toString(), modifier = Modifier.weight(1f))
        StatCard(label = "Active", value = stats.activeStore.toString(), modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(label = "Archived", value = stats.archived.toString(), modifier = Modifier.weight(1f))
        StatCard(label = "Messages", value = stats.messages.toString(), modifier = Modifier.weight(1f))
    }
    if (stats.bySource.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "By source",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                stats.bySource.entries.sortedByDescending { it.value }.forEach { (source, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = source,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
