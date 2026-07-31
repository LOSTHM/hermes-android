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
fun PairingScreen(
    viewModel: PairingViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var revokeTarget by remember { mutableStateOf<PairedDevice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pairing", style = MaterialTheme.typography.headlineSmall) },
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
                        TextButton(onClick = { viewModel.loadPairing() }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!uiState.loading) {
                SectionHeader("Paired devices")
                if (uiState.approved.isEmpty()) {
                    EmptyHint("No paired devices")
                } else {
                    uiState.approved.forEach { device ->
                        ApprovedDeviceRow(device = device, onRevoke = { revokeTarget = device })
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader("Pending codes")
                if (uiState.pending.isEmpty()) {
                    EmptyHint("No pending pairing codes")
                } else {
                    uiState.pending.forEach { pending ->
                        PendingPairingRow(pending)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    revokeTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke pairing", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Revoke ${device.userName.ifBlank { device.userId }} " +
                        "(${device.userId}) on ${device.platform}?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    revokeTarget = null
                    viewModel.revokePairing(device)
                }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ApprovedDeviceRow(device: PairedDevice, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.userName.ifBlank { device.userId },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.platform,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRevoke) {
                Text("Revoke", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PendingPairingRow(pending: PendingPairing) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pending.userName.ifBlank { pending.userId.ifBlank { "Unknown user" } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pending.platform,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = "Code ${pending.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${pending.ageMinutes}m old",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
