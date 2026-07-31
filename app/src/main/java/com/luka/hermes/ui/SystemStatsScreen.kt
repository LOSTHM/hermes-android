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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatsScreen(
    viewModel: SystemStatsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Stats", style = MaterialTheme.typography.headlineSmall) },
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

            if (!uiState.loading && uiState.error == null && uiState.stats is JsonObject) {
                val stats = uiState.stats as JsonObject

                // ── System info ──────────────────────────────────────────
                StatSection("System") {
                    stats.let { obj ->
                        listOfNotNull(
                            obj.pair("OS", "os"),
                            obj.pair("OS release", "os_release"),
                            obj.pair("OS version", "os_version"),
                            obj.pair("Platform", "platform"),
                            obj.pair("Arch", "arch"),
                            obj.pair("Hostname", "hostname"),
                            obj.pair("Python", "python_version"),
                            obj.pair("Python impl", "python_impl"),
                            obj.pair("Hermes version", "hermes_version"),
                            obj.pair("CPU count", "cpu_count"),
                            obj.pair("psutil", "psutil"),
                        ).forEach { (label, value) ->
                            StatRow(label, value)
                        }
                    }
                }

                // ── Memory ───────────────────────────────────────────────
                val memory = stats.nestedObject("memory", "mem")
                if (memory != null) {
                    StatSection("Memory") {
                        val percent = memory.percentOf("percent", "usage", "used_percent")
                        val total = memory.bytesOf("total", "size")
                        val used = memory.bytesOf("used")
                        val available = memory.bytesOf("available", "free", "avail")
                        val summary = listOfNotNull(
                            used?.let { formatBytes(it) },
                            total?.let { "of ${formatBytes(it)}" },
                            percent?.let { "${(it * 100).toInt()}%" },
                        ).joinToString(" ")
                        StatBar(label = if (summary.isBlank()) "Memory" else summary, percent = percent)
                        available?.let { StatRow("Available", formatBytes(it)) }
                    }
                }

                // ── Disk ─────────────────────────────────────────────────
                val disk = stats.nestedObject("disk", "storage")
                if (disk != null) {
                    StatSection("Disk") {
                        val percent = disk.percentOf("percent", "used_percent")
                        val total = disk.bytesOf("total", "size")
                        val used = disk.bytesOf("used")
                        val free = disk.bytesOf("free", "available", "avail")
                        val summary = listOfNotNull(
                            used?.let { formatBytes(it) },
                            total?.let { "of ${formatBytes(it)}" },
                            percent?.let { "${(it * 100).toInt()}%" },
                        ).joinToString(" ")
                        StatBar(label = if (summary.isBlank()) "Disk" else summary, percent = percent)
                        free?.let { StatRow("Free", formatBytes(it)) }
                    }
                }

                // ── CPU ──────────────────────────────────────────────────
                val cpuPercent = stats.percentOf("cpu_percent", "cpu", "cpu_pct")
                val loadAvg = stats.loadAvgString()
                StatSection("CPU") {
                    StatBar(
                        label = if (cpuPercent != null) "CPU ${(cpuPercent * 100).toInt()}%" else "CPU",
                        percent = cpuPercent,
                    )
                    if (loadAvg != null) {
                        StatRow("Load average", loadAvg)
                    }
                }

                // ── Process ──────────────────────────────────────────────
                val process = stats.nestedObject("process", "proc")
                if (process != null) {
                    StatSection("Process") {
                        process.let { obj ->
                            listOfNotNull(
                                obj.pair("PID", "pid"),
                                obj.pair("RSS", "rss").let { (label, raw) ->
                                    raw?.toDoubleOrNull()?.toLong()?.let { label to formatBytes(it) }
                                        ?: (label to raw)
                                },
                                obj.pair("Threads", "num_threads", "threads", "numThreads"),
                            ).forEach { (label, value) ->
                                StatRow(label, value)
                            }
                        }
                        val createTime = process.createTime()
                        if (createTime != null) {
                            StatRow("Start", createTime)
                        }
                    }
                }

                // ── Uptime ───────────────────────────────────────────────
                val uptime = stats.uptimeSeconds()
                if (uptime != null) {
                    StatSection("Uptime") {
                        StatRow("Uptime", formatUptime(uptime))
                    }
                }

                Spacer(Modifier.height(24.dp))
            } else if (!uiState.loading && uiState.error == null) {
                Text(
                    text = "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatSection(
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
private fun StatRow(label: String, value: String?) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBar(label: String, percent: Float?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(160.dp),
        )
        LinearProgressIndicator(
            progress = { (percent ?: 0f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(MaterialTheme.shapes.medium),
        )
    }
}

// ── Parsing helpers (best-effort over varying response shapes) ─────────────

private fun JsonObject.pair(label: String, vararg keys: String): Pair<String, String?> =
    label to optString(*keys)

private fun JsonObject.percentOf(vararg keys: String): Float? {
    val raw = optString(*keys) ?: return null
    val value = raw.trimEnd('%').toFloatOrNull() ?: return null
    if (value < 0f) return null
    return if (value > 1f) value / 100f else value
}

private fun JsonObject.bytesOf(vararg keys: String): Long? {
    val raw = optString(*keys) ?: return null
    return raw.trimEnd('%').toDoubleOrNull()?.toLong()
}

private fun JsonObject.loadAvgString(): String? {
    val el = this["load_avg"] ?: return null
    return when (el) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
        is JsonPrimitive -> el.contentOrNull
        else -> null
    }
}

private fun JsonObject.uptimeSeconds(): Long? {
    val raw = optString("uptime_seconds", "uptime") ?: return null
    val seconds = raw.toDoubleOrNull()?.toLong() ?: return null
    if (seconds <= 0) return null
    return seconds
}

private fun JsonObject.createTime(): String? {
    val raw = optString("create_time", "created", "started_at") ?: return null
    val millis = raw.toDoubleOrNull()?.times(1000.0)?.toLong() ?: return null
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        fmt.format(java.util.Date(millis))
    } catch (_: Exception) {
        raw
    }
}

private fun JsonElement.nestedObject(vararg keys: String): JsonObject? {
    val obj = this as? JsonObject ?: return null
    for (key in keys) {
        val el = obj[key] as? JsonObject ?: continue
        return el
    }
    return null
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0 || days > 0) add("${hours}h")
        add("${minutes}m")
    }.joinToString(" ")
}
