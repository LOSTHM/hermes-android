package com.luka.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    LaunchedEffect(uiState.filePath) {
        editing = false
        editText = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files", style = MaterialTheme.typography.headlineSmall) },
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
                .padding(padding),
        ) {
            // ── Breadcrumb ─────────────────────────────────────────────
            FilesBreadcrumb(
                parts = viewModel.breadcrumbParts(),
                onNavigate = viewModel::loadDirectory,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { viewModel.loadDirectory(uiState.path) }) { Text("Retry") }
                    }
                }
            }

            if (!uiState.loading && uiState.error == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New Folder")
                    }
                }

                if (uiState.entries.isEmpty()) {
                    Text(
                        text = "Empty directory",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.entries.size) { index ->
                            val entry = uiState.entries[index]
                            FilesEntryRow(
                                entry = entry,
                                onClick = {
                                    if (entry.isDirectory) {
                                        viewModel.loadDirectory(entry.path)
                                    } else {
                                        viewModel.openFile(entry.path)
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }

    // ── File preview / edit dialog ─────────────────────────────────────
    val filePath = uiState.filePath
    if (filePath != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeFile,
            title = {
                Text(
                    text = filePath.substringAfterLast("/").ifEmpty { filePath },
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                if (editing) {
                    Column {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 8,
                            maxLines = 12,
                        )
                        uiState.fileSaveError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    when {
                        uiState.fileLoading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        uiState.fileError != null -> {
                            Text(
                                text = uiState.fileError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        uiState.fileContent != null -> {
                            val content = uiState.fileContent
                            if (content != null && content is JsonObject && content.optString("binary") == "true") {
                                Text(
                                    text = "Binary file — preview not available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (content != null) {
                                val text = content.filePreviewText()
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (editing) {
                    TextButton(
                        onClick = {
                            editing = false
                            viewModel.resetFileSave()
                        },
                        enabled = !uiState.fileSaving,
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            viewModel.writeFile(filePath, editText) { ok ->
                                if (ok) editing = false
                            }
                        },
                        enabled = editText.isNotEmpty() && !uiState.fileSaving,
                    ) {
                        if (uiState.fileSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            editText = uiState.fileContent?.fileEditText() ?: ""
                            editing = true
                        },
                        enabled = !uiState.fileLoading && uiState.fileError == null,
                    ) { Text("Edit") }
                    TextButton(onClick = viewModel::closeFile) { Text("Close") }
                }
            },
        )
    }

    // ── New folder dialog ──────────────────────────────────────────────
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder name") },
                        singleLine = true,
                    )
                    uiState.mkdirError?.let {
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
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.mkdir(joinPath(uiState.path, name)) { ok ->
                                if (ok) {
                                    showNewFolderDialog = false
                                    newFolderName = ""
                                }
                            }
                        }
                    },
                    enabled = newFolderName.isNotBlank() && !uiState.mkdirSaving,
                ) {
                    if (uiState.mkdirSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    newFolderName = ""
                }) { Text("Cancel") }
            },
        )
    }
}

private fun joinPath(parent: String, name: String): String {
    val base = parent.trimEnd('/').ifEmpty { "/" }
    return if (base == "/") "/$name" else "$base/$name"
}

private fun JsonElement.fileEditText(): String = when (this) {
    is JsonObject -> optString("text", "content", "data") ?: ""
    is JsonPrimitive -> contentOrNull ?: ""
    else -> ""
}

@Composable
private fun FilesBreadcrumb(
    parts: List<String>,
    onNavigate: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parts.isEmpty()) {
            Text(
                text = "~",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            var acc = "/"
            parts.forEachIndexed { index, part ->
                val path = when {
                    index == 0 && part == "/" -> "/"
                    index == 0 -> part
                    else -> {
                        acc = if (acc == "/") "/$part" else "$acc/$part"
                        acc
                    }
                }
                if (index > 0) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onNavigate(path) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FilesEntryRow(
    entry: FsEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = if (entry.isDirectory) "Folder" else "File",
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun JsonElement.filePreviewText(): String {
    val obj = this as? JsonObject ?: return toString()
    val text = obj.optString("text", "content", "data") ?: return toString()
    if (text.length <= 4000) return text
    return text.take(4000) + "\n… (truncated)"
}
