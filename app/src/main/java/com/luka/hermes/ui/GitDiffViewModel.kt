package com.luka.hermes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.HermesRestClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class GitDiffLineKind { ADD, REMOVE, HUNK, CONTEXT }

data class GitDiffLine(
    val text: String,
    val kind: GitDiffLineKind,
)

data class GitDiffUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val lines: List<GitDiffLine> = emptyList(),
)

class GitDiffViewModel(
    application: Application,
    private val repoPath: String,
    private val filePath: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GitDiffUiState())
    val uiState: StateFlow<GitDiffUiState> = _uiState.asStateFlow()

    init {
        HermesRestClient.init(application)
        loadDiff()
    }

    fun loadDiff() {
        viewModelScope.launch {
            _uiState.value = GitDiffUiState(loading = true)
            try {
                val element = HermesRestClient.getJson(
                    "git/file-diff",
                    mapOf("path" to repoPath, "file" to filePath),
                )
                _uiState.value = GitDiffUiState(lines = element.diffText().toDiffLines())
            } catch (e: Exception) {
                _uiState.value = GitDiffUiState(error = e.message ?: "Failed to load diff")
            }
        }
    }
}

// ── Diff parsing (best-effort over varying response shapes) ────────────────

private fun JsonElement.diffText(): String {
    val obj = this as? JsonObject
    if (obj != null) {
        obj.optString("diff", "text", "content", "data")?.let { return it }
        obj.optStringArray("lines", "diff", "content", "data")?.let { return it }
        return ""
    }
    return when (this) {
        is JsonArray -> buildString {
            for (entry in this@diffText) {
                append(entry.optScalar())
                append("\n")
            }
        }
        is JsonPrimitive -> contentOrNull ?: ""
        else -> ""
    }
}

/** Read a string array field and join it into a single diff text. */
private fun JsonObject.optStringArray(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        val arr = el as? JsonArray ?: continue
        if (arr.isEmpty()) return ""
        return buildString {
            for (entry in arr) {
                append(entry.optScalar())
                append("\n")
            }
        }
    }
    return null
}

private fun JsonElement.optScalar(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: ""
    is JsonObject -> optString("line", "text", "content", "value") ?: ""
    else -> ""
}

private fun String.toDiffLines(): List<GitDiffLine> {
    if (isBlank()) return emptyList()
    return trimEnd('\n', '\r').split("\n").map { raw ->
        val line = raw.removeSuffix("\r")
        val kind = when {
            line.startsWith("+") && !line.startsWith("+++") -> GitDiffLineKind.ADD
            line.startsWith("-") && !line.startsWith("---") -> GitDiffLineKind.REMOVE
            line.startsWith("@@") -> GitDiffLineKind.HUNK
            else -> GitDiffLineKind.CONTEXT
        }
        GitDiffLine(line, kind)
    }
}
