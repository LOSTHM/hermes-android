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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorktreesScreen(
    viewModel: WorktreesViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Worktrees", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) { Text("Add") }
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
                        TextButton(onClick = { viewModel.loadWorktrees() }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            uiState.removeError?.let { error ->
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

            if (!uiState.loading && uiState.error == null) {
                if (uiState.worktrees.isEmpty()) {
                    Text(
                        text = "No worktrees",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.worktrees.forEach { worktree ->
                        WorktreeCard(
                            worktree = worktree,
                            removing = uiState.removing == worktree.path,
                            onRemove = { pendingRemove = worktree.path },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddWorktreeDialog(
            adding = uiState.adding,
            error = uiState.addError,
            onDismiss = { showAddDialog = false },
            onConfirm = { path ->
                viewModel.addWorktree(path) { ok -> if (ok) showAddDialog = false }
            },
        )
    }

    pendingRemove?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove Worktree") },
            text = { Text("Remove worktree at \"$path\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeWorktree(path)
                    pendingRemove = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorktreeCard(
    worktree: Worktree,
    removing: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = worktree.branch ?: "(detached)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (worktree.isMain) {
                        Badge("main")
                    }
                    if (worktree.locked) {
                        Badge("locked")
                    }
                    if (worktree.detached) {
                        Badge("detached")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = worktree.path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!worktree.isMain) {
                TextButton(
                    onClick = onRemove,
                    enabled = !removing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    if (removing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AddWorktreeDialog(
    adding: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Worktree") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Path / name") },
                    placeholder = { Text("feature/foo") },
                    singleLine = true,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path) },
                enabled = path.isNotBlank() && !adding,
            ) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        },
    )
}
