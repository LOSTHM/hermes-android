package com.luka.hermes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    onBack: () -> Unit,
    onOpenDiff: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var diffFile by remember { mutableStateOf<String?>(null) }
    val openFile = diffFile
    if (openFile != null) {
        GitDiffScreen(
            repoPath = uiState.repoPath,
            filePath = openFile,
            onBack = { diffFile = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git", style = MaterialTheme.typography.headlineSmall) },
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

            // ── Repository selector ────────────────────────────────────
            if (uiState.repos.isNotEmpty()) {
                GitSectionCard("Repository") {
                    uiState.repos.forEach { repo ->
                        val selected = repo.root == uiState.repoPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                )
                                .clickable { viewModel.selectRepo(repo.root) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (selected) "●" else "○",
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = repo.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (repo.root != repo.label) {
                                    Text(
                                        text = repo.root,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (selected) {
                                Text(
                                    text = "active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

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
                        TextButton(onClick = { viewModel.loadGit() }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Working tree status ───────────────────────────────────
            GitSectionCard("Status") {
                val element = uiState.status
                if (element.isEmptyData()) {
                    GitNoData()
                } else {
                    val files = element.statusFileRows()
                    if (files.isEmpty()) {
                        GitJsonSnippet(element)
                    } else {
                        files.forEachIndexed { index, file ->
                            StatusFileRow(
                                file = file,
                                busy = uiState.stageBusy,
                                onOpenDiff = { name ->
                                    onOpenDiff(name)
                                    diffFile = name
                                },
                                onStage = viewModel::stageFile,
                                onUnstage = viewModel::unstageFile,
                            )
                            if (index < files.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                        uiState.stageError?.let { stageError ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stageError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // ── Branches ──────────────────────────────────────────────
            GitSectionCard("Branches") {
                val element = uiState.branches
                if (element.isEmptyData()) {
                    GitNoData()
                } else {
                    val branches = element.branchList()
                    if (branches.isEmpty()) {
                        GitJsonSnippet(element)
                    } else {
                        branches.forEach { (name, current) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (current) "★" else "·",
                                    color = if (current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                if (current) {
                                    Text(
                                        text = "current",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Base branches ─────────────────────────────────────────
            GitSectionCard("Base Branches") {
                val element = uiState.baseBranches
                if (element.isEmptyData()) {
                    GitNoData()
                } else {
                    val baseBranches = element.baseBranchList()
                    if (baseBranches.isEmpty()) {
                        GitJsonSnippet(element)
                    } else {
                        baseBranches.forEach { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── UI building blocks ─────────────────────────────────────────────────────

@Composable
private fun GitSectionCard(
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
private fun GitNoData() {
    Text(
        text = "No data",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GitJsonSnippet(element: JsonElement) {
    val raw = element.toString()
    val text = if (raw.length > 600) raw.take(600) + "…" else raw
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusFileRow(
    file: GitFileRow,
    busy: Boolean,
    onOpenDiff: (String) -> Unit,
    onStage: (String) -> Unit,
    onUnstage: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusLabels[file.marker] ?: file.marker,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusMarkerColor(file.marker),
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(statusMarkerColor(file.marker).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable { onOpenDiff(file.name) },
        )
        if (!file.staged) {
            SmallActionButton(label = "Stage", enabled = !busy) { onStage(file.name) }
        }
        if (file.staged) {
            SmallActionButton(label = "Unstage", enabled = !busy) { onUnstage(file.name) }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun statusMarkerColor(marker: String): Color = when (marker) {
    "??" -> MaterialTheme.colorScheme.tertiary
    "A" -> MaterialTheme.colorScheme.primary
    "D" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.secondary
}

// ── Parsing helpers (best-effort over varying response shapes) ─────────────

data class GitFileRow(
    val marker: String,
    val name: String,
    val staged: Boolean = false,
    val unstaged: Boolean = false,
    val untracked: Boolean = false,
)

private val statusLabels = mapOf(
    "??" to "untracked",
    "A" to "added",
    "M" to "modified",
    "D" to "deleted",
    "R" to "renamed",
    "C" to "copied",
    "U" to "unmerged",
)

private fun JsonElement?.isEmptyData(): Boolean {
    if (this == null || this is JsonNull) return true
    return when (this) {
        is JsonArray -> isEmpty()
        is JsonObject -> isEmpty()
        is JsonPrimitive -> (contentOrNull ?: "").isBlank()
    }
}

private fun JsonElement.statusFileRows(): List<GitFileRow> {
    val entries = when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf("files", "status", "changes", "entries", "items", "results", "data")
            .firstNotNullOfOrNull { key -> (this[key] as? JsonArray) }?.toList() ?: emptyList()
        else -> emptyList()
    }
    return entries.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> parseStatusLine(entry.contentOrNull ?: return@mapNotNull null)
                ?.let { (marker, name) -> GitFileRow(marker = marker, name = name) }
            is JsonObject -> {
                val name = entry.optString("path", "file", "name", "filename", "path_from_root", "value")
                    ?: return@mapNotNull null
                val staged = entry.boolValue("staged", "is_staged", "isStaged") == true
                val unstaged = entry.boolValue("unstaged", "is_unstaged", "isUnstaged") == true
                val untracked = entry.boolValue("untracked", "is_untracked", "isUntracked") == true
                val conflicted = entry.boolValue("conflicted", "conflict", "is_conflicted") == true
                val code = entry.optString("status", "state", "code", "raw")
                val marker = when {
                    code != null -> code
                    untracked -> "??"
                    conflicted -> "U"
                    else -> "M"
                }
                GitFileRow(
                    marker = marker,
                    name = name,
                    staged = staged,
                    unstaged = unstaged,
                    untracked = untracked,
                )
            }
            else -> null
        }
    }
}

private fun JsonObject.boolValue(vararg keys: String): Boolean? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el !is JsonPrimitive) return null
        return when (val content = el.contentOrNull) {
            "true", "1", "yes" -> true
            "false", "0", "no", null -> false
            else -> content.toBooleanStrictOrNull()
        }
    }
    return null
}

private fun parseStatusLine(line: String): Pair<String, String>? {
    val trimmed = line.trimStart()
    val code = trimmed.take(2).trim()
    val path = trimmed.drop(2).trim()
    if (path.isEmpty()) return null
    return (code.ifEmpty { "M" }) to path
}

private fun JsonElement.branchList(): List<Pair<String, Boolean>> {
    val entries = when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf("branches", "items", "results", "data", "list")
            .firstNotNullOfOrNull { key -> (this[key] as? JsonArray) }?.toList() ?: emptyList()
        else -> emptyList()
    }
    return entries.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> (entry.contentOrNull ?: return@mapNotNull null) to false
            is JsonObject -> {
                val name = entry.optString("name", "branch", "ref", "label", "value") ?: return@mapNotNull null
                val current = entry.optString("current", "is_current", "isCurrent", "active", "selected")
                    ?.let { it == "true" || it == "1" } == true
                name to current
            }
            else -> null
        }
    }
}

private fun JsonElement.baseBranchList(): List<String> {
    val entries = when (this) {
        is JsonArray -> toList()
        is JsonObject -> listOf("base_branches", "branches", "items", "results", "data")
            .firstNotNullOfOrNull { key -> (this[key] as? JsonArray) }?.toList() ?: emptyList()
        else -> emptyList()
    }
    return entries.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> entry.contentOrNull
            is JsonObject -> entry.optString("name", "branch", "ref", "label")
            else -> null
        }
    }
}
