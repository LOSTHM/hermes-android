package com.luka.hermes.ui

import com.luka.hermes.gateway.HermesRestClient
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class FsEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
)

data class FilesUiState(
    val path: String = "~",
    val entries: List<FsEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val filePath: String? = null,
    val fileContent: JsonElement? = null,
    val fileLoading: Boolean = false,
    val fileError: String? = null,
    val fileSaving: Boolean = false,
    val fileSaveError: String? = null,
    val mkdirSaving: Boolean = false,
    val mkdirError: String? = null,
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadDefault()
    }

    fun loadDefault() {
        viewModelScope.launch {
            var start = "~"
            try {
                val element = HermesRestClient.getJson("fs/default-cwd")
                (element as? JsonObject)?.optString("cwd")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { start = it }
            } catch (_: Exception) {
            }
            loadDirectory(start)
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                path = path,
                loading = true,
                error = null,
                fileContent = null,
                filePath = null,
                fileError = null,
                fileSaving = false,
                fileSaveError = null,
                mkdirSaving = false,
                mkdirError = null,
            )
            try {
                val element = HermesRestClient.getJson("fs/list", mapOf("path" to path))
                _uiState.value = _uiState.value.copy(
                    entries = element.fsEntries(),
                    loading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    entries = emptyList(),
                    loading = false,
                    error = e.message ?: "Failed to list directory",
                )
            }
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                filePath = path,
                fileContent = null,
                fileLoading = true,
                fileError = null,
                fileSaving = false,
                fileSaveError = null,
            )
            try {
                val element = HermesRestClient.getJson("fs/read-text", mapOf("path" to path))
                _uiState.value = _uiState.value.copy(
                    fileContent = element,
                    fileLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    fileLoading = false,
                    fileError = e.message ?: "Failed to read file",
                )
            }
        }
    }

    fun closeFile() {
        _uiState.value = _uiState.value.copy(
            filePath = null,
            fileContent = null,
            fileLoading = false,
            fileError = null,
            fileSaving = false,
            fileSaveError = null,
        )
    }

    /** Overwrite (or create) a UTF-8 text file via `/api/fs/write-text`. */
    fun writeFile(path: String, content: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(fileSaving = true, fileSaveError = null)
            try {
                val body = buildJsonObject {
                    put("path", path)
                    put("content", content)
                }.toString()
                HermesRestClient.post("fs/write-text", body)
                _uiState.value = _uiState.value.copy(fileSaving = false)
                openFile(path)
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    fileSaving = false,
                    fileSaveError = e.message ?: "Failed to save file",
                )
                onDone(false)
            }
        }
    }

    /** Create a directory via `/api/files/mkdir`, then refresh the listing. */
    fun mkdir(path: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mkdirSaving = true, mkdirError = null)
            try {
                val body = buildJsonObject { put("path", path) }.toString()
                HermesRestClient.post("files/mkdir", body)
                _uiState.value = _uiState.value.copy(mkdirSaving = false)
                loadDirectory(_uiState.value.path)
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mkdirSaving = false,
                    mkdirError = e.message ?: "Failed to create directory",
                )
                onDone(false)
            }
        }
    }

    /** Clear any previous file-save error when re-entering edit mode. */
    fun resetFileSave() {
        _uiState.value = _uiState.value.copy(fileSaving = false, fileSaveError = null)
    }

    fun breadcrumbParts(): List<String> {
        val raw = _uiState.value.path
        return when {
            raw.isBlank() -> emptyList()
            raw == "~" -> listOf("~")
            raw == "/" -> listOf("/")
            else -> raw.trimEnd('/').split("/")
                .filter { it.isNotBlank() }
                .let { parts -> listOf("/") + parts }
        }
    }
}

private fun JsonElement.fsEntries(): List<FsEntry> {
    val list = when (this) {
        is JsonArray -> toList()
        is JsonObject -> (this["entries"] as? JsonArray)?.toList() ?: emptyList()
        else -> emptyList()
    }
    return list.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val name = obj.optString("name") ?: return@mapNotNull null
        val path = obj.optString("path", "fullPath", "absPath", "file")
            ?: return@mapNotNull null
        val isDir = obj.optString("isDirectory", "is_dir", "isDir", "type")
            ?.let { it == "true" || it == "1" || it.equals("dir", ignoreCase = true) } == true
        FsEntry(name = name, path = path, isDirectory = isDir)
    }
}
