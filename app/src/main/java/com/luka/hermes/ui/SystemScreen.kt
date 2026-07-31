package com.luka.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(
    viewModel: SystemViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var pendingKill by remember { mutableStateOf<JsonElement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System", style = MaterialTheme.typography.headlineSmall) },
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

            // ── Processes ─────────────────────────────────────────────
            SectionCard("Processes") {
                if (uiState.processesError) {
                    Text(
                        text = "Process list unavailable on serve",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (uiState.processes.isEmpty()) {
                    NoData()
                } else {
                    uiState.processes.forEach { proc ->
                        val name = proc.processName() ?: "Unknown"
                        val pid = proc.processPid()
                        val subtitle = listOfNotNull(
                            pid?.let { "PID $it" },
                            proc.processCpu()?.let { "CPU $it" },
                            proc.processMem()?.let { "Mem $it" },
                        ).joinToString(" · ")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = pid != null) { pendingKill = proc }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (pid != null) {
                                Text(
                                    text = "kill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            // ── System / Battery ───────────────────────────────────────
            SectionCard("System") {
                val battery = uiState.system
                if (battery == null) {
                    NoData()
                } else if (battery.isAvailableFalse()) {
                    Text(
                        text = "Battery info unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val level = battery.optString("level", "battery", "percent", "percentage")
                    val charging = battery.optString("charging", "is_charging", "status", "state")
                    val summary = listOfNotNull(
                        level?.let { "Battery $it" },
                        charging?.let { "Charging $it" },
                    ).joinToString(" · ")
                    if (summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        JsonSnippet(battery)
                    }
                }
            }

            // ── Config ─────────────────────────────────────────────────
            SectionCard("Config") {
                when {
                    uiState.configError -> Text(
                        text = "Failed to load",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    uiState.config.isEmpty() -> NoData()
                    else -> uiState.config.forEach { section ->
                        val title = section.optString("title", "name", "label") ?: "Unknown"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Models ─────────────────────────────────────────────────
            SectionCard("Models") {
                when {
                    uiState.modelsError -> Text(
                        text = "Failed to load",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    uiState.models.isEmpty() -> NoData()
                    else -> uiState.models.forEach { provider ->
                        val name = provider.optString("name", "id", "label") ?: "Unknown"
                        val count = provider.modelCount()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text("•", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (count != null) "$name · $count models" else name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Usage ──────────────────────────────────────────────────
            SectionCard("Usage") {
                val usage = uiState.usage
                when {
                    usage != null && usage.isAvailableFalse() -> Text(
                        text = "Usage bars unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        val bars = usage?.toUsageBars().orEmpty()
                        if (bars.isEmpty()) {
                            NoData()
                        } else {
                            bars.forEach { (label, value, fraction) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(120.dp),
                                    )
                                    LinearProgressIndicator(
                                        progress = { fraction.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Kill confirmation dialog ─────────────────────────────────────────
    pendingKill?.let { proc ->
        val name = proc.processName() ?: "Unknown"
        val pid = proc.processPid()
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text("Kill Process") },
            text = { Text("Kill \"$name\"${pid?.let { " (PID $it)" } ?: ""}?") },
            confirmButton = {
                TextButton(onClick = {
                    pid?.toIntOrNull()?.let { viewModel.killProcess(it) }
                    pendingKill = null
                }) {
                    Text("Kill", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingKill = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
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
            content()
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NoData() {
    Text(
        text = "No data",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JsonSnippet(element: JsonElement) {
    val raw = element.toString()
    val text = if (raw.length > 600) raw.take(600) + "…" else raw
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Parsing helpers ─────────────────────────────────────────────────────────

private fun JsonElement.processName(): String? =
    optString("name", "command", "cmd", "title", "id")

private fun JsonElement.processPid(): String? =
    optString("pid")

private fun JsonElement.processCpu(): String? =
    optString("cpu", "cpu_percent", "cpu_pct", "cpu%")

private fun JsonElement.processMem(): String? =
    optString("mem", "memory", "memory_percent", "rss", "mem%", "vms")

/** True when a system/usage RPC reports the feature as unsupported. */
private fun JsonElement.isAvailableFalse(): Boolean {
    val obj = this as? JsonObject ?: return false
    return obj["available"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == false
}

/** Number of models a `model.options` provider entry carries. */
private fun JsonElement.modelCount(): Int? {
    val obj = this as? JsonObject ?: return null
    return (obj["models"] as? JsonArray)?.size
}

private data class UsageBar(
    val label: String,
    val value: String,
    val fraction: Float,
)

private fun JsonElement.toUsageBars(): List<UsageBar> {
    val items = when (this) {
        is JsonArray -> toList()
        is JsonObject -> {
            listOf("bars", "items", "results", "data", "usage").firstNotNullOfOrNull { key ->
                (this[key] as? JsonArray)?.takeIf { it.isNotEmpty() }
            }?.toList() ?: listOf(this)
        }
        else -> emptyList()
    }

    return items.mapNotNull { item ->
        val label = item.optString("name", "label", "key", "title", "bar") ?: return@mapNotNull null
        val rawValue = item.optString("value", "percent", "current", "used", "progress", "fraction")
            ?: return@mapNotNull null
        val numeric = rawValue.trimEnd('%').toFloatOrNull()
        val max = item.optString("max", "total")?.trimEnd('%')?.toFloatOrNull()?.takeIf { it > 0f }
        val fraction = when {
            numeric == null -> 0f
            max != null -> numeric / max
            rawValue.trimEnd('%').endsWith("%") || numeric > 1f -> numeric / 100f
            else -> numeric
        }
        UsageBar(label, rawValue, fraction.coerceIn(0f, 1f))
    }
}
