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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubagentsScreen(
    viewModel: SubagentsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subagents", style = MaterialTheme.typography.headlineSmall) },
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
                        TextButton(onClick = { viewModel.loadAgents() }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!uiState.loading && uiState.error == null) {
                if (uiState.trees.isEmpty()) {
                    Text(
                        text = "No subagent trees",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.trees.forEach { tree ->
                        SubagentTreeCard(tree)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubagentTreeCard(tree: SubagentTree) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (tree.running) "running" else "completed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tree.running) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = tree.sessionId.ifBlank { "session" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${tree.count} agent${if (tree.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tree.label.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tree.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (tree.nodes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                tree.nodes.forEach { node ->
                    Spacer(Modifier.height(8.dp))
                    SubagentNodeRow(node)
                }
            }
        }
    }
}

@Composable
private fun SubagentNodeRow(node: SubagentNode) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (node.depth != null && node.depth > 0) "·".repeat(node.depth.coerceAtMost(4)) + " " else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = node.status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = nodeStatusColor(node.status),
        )
    }
    if (node.model.isNotBlank()) {
        Text(
            text = node.model,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun nodeStatusColor(status: String): androidx.compose.ui.graphics.Color {
    val lower = status.lowercase()
    return when {
        lower.contains("complete") || lower.contains("done") || lower.contains("success") ->
            MaterialTheme.colorScheme.primary
        lower.contains("fail") || lower.contains("error") || lower.contains("dead") ->
            MaterialTheme.colorScheme.error
        lower.contains("run") || lower.contains("work") || lower.contains("queued") ->
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
