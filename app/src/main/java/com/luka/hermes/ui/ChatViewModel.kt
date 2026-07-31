package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// ── UI data types ──────────────────────────────────────────────────────────────

enum class ToolStatus { Running, Complete }

enum class ChatMode { HERMES, DIRECT }

sealed class ChatItem {
    abstract val stableId: String
    abstract val timestamp: Long  // epoch millis

    data class UserMessage(
        val text: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem() {
        override val stableId: String get() = "user-${hashCode()}-$timestamp"
    }
    data class AssistantMessage(
        val text: String,
        val isStreaming: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem() {
        override val stableId: String get() = "assistant-${hashCode()}-$timestamp"
    }
    data class ToolCallCard(
        val name: String,
        val status: ToolStatus,
        val args: String? = null,
        val result: String? = null,
        val summary: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem() {
        override val stableId: String get() = "tool-$name-${hashCode()}"
    }
    data class ThinkingBlock(
        val text: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem() {
        override val stableId: String get() = "think-${hashCode()}"
    }
    data class ErrorItem(
        val message: String,
        val isColdStart: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatItem() {
        override val stableId: String get() = "error-${hashCode()}"
    }
}

data class ClarifyRequestData(
    val id: String,
    val question: String,
)

data class ApprovalRequestData(
    val id: String,
    val title: String?,
    val description: String?,
)

data class ChatUiState(
    val sessionId: String = "",
    val messages: List<ChatItem> = emptyList(),
    val isStreaming: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val clarifyRequest: ClarifyRequestData? = null,
    val approvalRequest: ApprovalRequestData? = null,
    val coldStartWarning: Boolean = false,
    val inputText: String = "",
    val chatMode: ChatMode = ChatMode.HERMES,
    /** For DIRECT mode: API configuration */
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val apiModel: String = "qwen3.6",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    /** Attachment */
    val attachedImageUri: String? = null,
    /** Token usage */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

// ── ViewModel ──────────────────────────────────────────────────────────────────

class ChatViewModel : ViewModel() {

    private val repository = HermesClient.repository
    private val directApi = HermesClient.directApi
    private var sessionId: String = ""
    private var streamJob: Job? = null
    private var coldStartJob: Job? = null
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun setSession(id: String) {
        if (sessionId == id) return
        sessionId = id
        _uiState.value = _uiState.value.copy(
            sessionId = id,
            messages = emptyList(),
            isStreaming = false,
            clarifyRequest = null,
            approvalRequest = null,
            coldStartWarning = false,
        )
        // Load session history in Hermes mode
        if (_uiState.value.chatMode == ChatMode.HERMES) {
            viewModelScope.launch {
                // Resume the session first so the daemon loads it from storage;
                // session.history only works for sessions resident in its memory.
                try {
                    repository.resumeSession(id)
                } catch (_: Exception) {
                    // Silently degrade — resume failure must not block chatting.
                }
                loadSessionHistory(id)
            }
        }
    }

    fun setDirectConfig(baseUrl: String, apiKey: String, model: String, temperature: Float = 0.7f, maxTokens: Int = 4096, systemPrompt: String = "") {
        _uiState.value = _uiState.value.copy(
            apiBaseUrl = baseUrl,
            apiKey = apiKey,
            apiModel = model,
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt,
        )
    }

    fun setChatMode(mode: ChatMode) {
        _uiState.value = _uiState.value.copy(chatMode = mode)
    }

    /**
     * Load past messages for an existing Hermes session.
     */
    private fun loadSessionHistory(sessionId: String) {
        viewModelScope.launch {
            try {
                val result = repository.getSessionHistory(sessionId)
                val items = mutableListOf<ChatItem>()
                // Expects array of { role, content } objects
                if (result is JsonArray) {
                    for (entry in result) {
                        val obj = entry.jsonObject
                        val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: continue
                        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                        when (role) {
                            "user" -> items.add(ChatItem.UserMessage(content))
                            "assistant" -> items.add(ChatItem.AssistantMessage(content, false))
                            "tool" -> { /* ignore tool messages */ }
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(messages = items)
                }
            } catch (_: Exception) {
                // Silently ignore history load errors (fresh session, etc.)
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendPrompt() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val mode = _uiState.value.chatMode

        // For DIRECT mode, require config
        if (mode == ChatMode.DIRECT) {
            val cfg = _uiState.value
            if (cfg.apiBaseUrl.isBlank() || cfg.apiKey.isBlank()) {
                addError("Configure API key and base URL in Settings first")
                return
            }
        }

        if (mode == ChatMode.HERMES && sessionId.isEmpty()) {
            addError("No active session")
            return
        }

        val userMsg = ChatItem.UserMessage(text = text)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isStreaming = true,
            coldStartWarning = false,
        )

        when (mode) {
            ChatMode.HERMES -> startHermesPrompt(text)
            ChatMode.DIRECT -> startDirectPrompt(text)
        }
    }

    // ── Hermes daemon mode ──────────────────────────────────────────────

    private fun startHermesPrompt(text: String) {
        startColdStartTimer()

        streamJob = viewModelScope.launch {
            try {
                repository.sendPrompt(sessionId, text).collect { event ->
                    processHermesEvent(event)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    addError(e.message ?: "Stream error")
                }
            } finally {
                _uiState.update { it.copy(isStreaming = false) }
                coldStartJob?.cancel()
            }
        }
    }

    private fun processHermesEvent(event: GatewayEvent) {
        when (event) {
            is MessageStart -> {
                coldStartJob?.cancel()
                _uiState.value = _uiState.value.copy(coldStartWarning = false)
                addMessage(ChatItem.AssistantMessage(text = "", isStreaming = true))
            }
            is MessageDelta -> {
                val text = event.text ?: return
                appendToLastAssistant(text)
            }
            is MessageComplete -> {
                val fullText = event.payload?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                val msgs = _uiState.value.messages.toMutableList()
                for (i in msgs.indices.reversed()) {
                    if (msgs[i] is ChatItem.AssistantMessage) {
                        val msg = msgs[i] as ChatItem.AssistantMessage
                        msgs[i] = msg.copy(text = fullText, isStreaming = false)
                        break
                    }
                }
                _uiState.value = _uiState.value.copy(messages = msgs, isStreaming = false)
                coldStartJob?.cancel()
            }
            is ThinkingDelta -> {
                val text = event.text ?: return
                val msgs = _uiState.value.messages.toMutableList()
                val lastIdx = msgs.indexOfLast { it is ChatItem.ThinkingBlock }
                if (lastIdx >= 0) {
                    val block = msgs[lastIdx] as ChatItem.ThinkingBlock
                    msgs[lastIdx] = block.copy(text = block.text + text)
                } else {
                    msgs.add(ChatItem.ThinkingBlock(text = text))
                }
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
            is ReasoningDelta -> {
                val text = event.text ?: return
                val msgs = _uiState.value.messages.toMutableList()
                val lastIdx = msgs.indexOfLast { it is ChatItem.ThinkingBlock }
                if (lastIdx >= 0) {
                    val block = msgs[lastIdx] as ChatItem.ThinkingBlock
                    msgs[lastIdx] = block.copy(text = block.text + text)
                } else {
                    msgs.add(ChatItem.ThinkingBlock(text = text))
                }
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
            is ToolStart -> {
                val name = event.toolName ?: "unknown"
                val context = event.payload?.get("context")?.jsonPrimitive?.contentOrNull
                addMessage(ChatItem.ToolCallCard(
                    name = name,
                    status = ToolStatus.Running,
                    args = context,
                ))
            }
            is ToolComplete -> {
                val name = event.toolName ?: "unknown"
                val summary = event.payload?.get("summary")?.jsonPrimitive?.contentOrNull
                val resultRaw = event.payload?.get("result")?.toString()
                val msgs = _uiState.value.messages.toMutableList()
                for (i in msgs.indices.reversed()) {
                    if (msgs[i] is ChatItem.ToolCallCard) {
                        val card = msgs[i] as ChatItem.ToolCallCard
                        if (card.status == ToolStatus.Running) {
                            msgs[i] = card.copy(
                                status = ToolStatus.Complete,
                                summary = summary,
                                result = resultRaw,
                            )
                            break
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
            is ClarifyRequest -> {
                val id = event.requestId ?: return
                val question = event.question ?: "Clarification needed"
                _uiState.value = _uiState.value.copy(
                    clarifyRequest = ClarifyRequestData(id = id, question = question),
                )
            }
            is ApprovalRequest -> {
                val id = event.requestId ?: return
                _uiState.value = _uiState.value.copy(
                    approvalRequest = ApprovalRequestData(
                        id = id,
                        title = event.title,
                        description = event.description,
                    ),
                )
            }
            is EventError -> {
                val msg = event.errorMessage ?: "Unknown error"
                if (msg.contains("initialization timed out", ignoreCase = true) ||
                    msg.contains("cold", ignoreCase = true)
                ) {
                    _uiState.value = _uiState.value.copy(coldStartWarning = true)
                } else {
                    addError(msg)
                }
            }
            else -> { /* ignore unhandled event types */ }
        }
    }

    // ── Direct API mode ─────────────────────────────────────────────────

    private fun startDirectPrompt(text: String) {
        val cfg = _uiState.value

        addMessage(ChatItem.AssistantMessage(text = "", isStreaming = true))

        streamJob = directApi.sendMessage(
            text = text,
            baseUrl = cfg.apiBaseUrl,
            apiKey = cfg.apiKey,
            model = cfg.apiModel,
            temperature = cfg.temperature,
            maxTokens = cfg.maxTokens,
            systemPrompt = cfg.systemPrompt,
            onDelta = { token ->
                viewModelScope.launch {
                    appendToLastAssistant(token)
                }
            },
            onComplete = { fullText ->
                viewModelScope.launch {
                    val msgs = _uiState.value.messages.toMutableList()
                    for (i in msgs.indices.reversed()) {
                        if (msgs[i] is ChatItem.AssistantMessage) {
                            val msg = msgs[i] as ChatItem.AssistantMessage
                            msgs[i] = msg.copy(text = fullText, isStreaming = false)
                            break
                        }
                    }
                    _uiState.value = _uiState.value.copy(messages = msgs, isStreaming = false)
                }
            },
            onError = { errorMsg ->
                viewModelScope.launch {
                    addError(errorMsg)
                }
            },
        )
    }

    // ── Shared actions ──────────────────────────────────────────────────

    fun interrupt() {
        streamJob?.cancel()
        streamJob = null
        coldStartJob?.cancel()

        if (_uiState.value.chatMode == ChatMode.HERMES) {
            viewModelScope.launch {
                try {
                    repository.interruptSession(sessionId)
                } catch (e: Exception) {
                    // Session interrupt failure is non-critical
                }
            }
        } else {
            directApi.interrupt()
        }

        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            for (i in msgs.indices.reversed()) {
                if (msgs[i] is ChatItem.AssistantMessage) {
                    val msg = msgs[i] as ChatItem.AssistantMessage
                    msgs[i] = msg.copy(isStreaming = false)
                    break
                }
            }
            state.copy(messages = msgs, isStreaming = false, coldStartWarning = false)
        }
    }

    fun newDirectSession() {
        directApi.clearHistory()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            isStreaming = false,
        )
    }

    fun respondClarify(id: String, response: String) {
        viewModelScope.launch {
            try {
                repository.respondClarify(id, response)
            } catch (e: Exception) {
                addError("Failed to send clarification: ${e.message}")
            }
        }
        _uiState.update { it.copy(clarifyRequest = null) }
    }

    fun dismissClarify() {
        _uiState.update { it.copy(clarifyRequest = null) }
    }

    fun respondApproval(id: String, approved: Boolean) {
        viewModelScope.launch {
            try {
                repository.respondApproval(id, approved)
            } catch (e: Exception) {
                addError("Failed to respond approval: ${e.message}")
            }
        }
        _uiState.update { it.copy(approvalRequest = null) }
    }

    fun dismissApproval() {
        _uiState.update { it.copy(approvalRequest = null) }
    }

    /**
     * Replace the user message at [index] with [newText].
     * Trims following messages and re-sends.
     */
    fun editMessage(index: Int, newText: String) {
        if (newText.isBlank()) return
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            if (index in msgs.indices && msgs[index] is ChatItem.UserMessage) {
                msgs[index] = (msgs[index] as ChatItem.UserMessage).copy(text = newText)
            }
            state.copy(messages = msgs.take(index + 1), isStreaming = false)
        }
        _uiState.update { it.copy(inputText = newText) }
        sendPrompt()
    }

    /**
     * Remove the [ChatItem] at [index] from the message list.
     */
    fun deleteMessage(index: Int) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            if (index in msgs.indices) msgs.removeAt(index)
            state.copy(messages = msgs)
        }
    }

    /**
     * Re-issue the last user prompt so the assistant regenerates a response.
     */
    fun regenerateLast() {
        val msgs = _uiState.value.messages
        val lastUserIdx = msgs.indexOfLast { it is ChatItem.UserMessage }
        if (lastUserIdx < 0) return
        val lastText = (msgs[lastUserIdx] as ChatItem.UserMessage).text
        _uiState.update { state ->
            state.copy(messages = state.messages.take(lastUserIdx + 1), isStreaming = false)
        }
        _uiState.update { it.copy(inputText = lastText) }
        sendPrompt()
    }

    // ── Session actions ─────────────────────────────────────────────────

    /**
     * Undo the last turn. In Hermes mode the daemon is asked to drop the
     * trailing assistant/tool messages plus the last user message; the local
     * list is trimmed to match. In DIRECT mode the last turn is removed locally.
     */
    fun undoLast() {
        if (_uiState.value.isStreaming) return
        val mode = _uiState.value.chatMode
        viewModelScope.launch {
            try {
                if (mode == ChatMode.HERMES) {
                    repository.undoSession(sessionId)
                }
                removeLastTurn()
            } catch (e: Exception) {
                addError("Failed to undo: ${e.message}")
            }
        }
    }

    /**
     * Refresh the session's token usage into
     * [ChatUiState.promptTokens] / [ChatUiState.completionTokens].
     */
    fun loadSessionUsage() {
        viewModelScope.launch {
            try {
                val result = repository.getSessionUsage(sessionId)
                val obj = result as? JsonObject ?: return@launch
                val prompt = listOf("prompt", "prompt_tokens", "input")
                    .firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.intOrNull }
                val completion = listOf("completion", "completion_tokens", "output")
                    .firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.intOrNull }
                if (prompt != null || completion != null) {
                    _uiState.update { it.copy(
                        promptTokens = prompt ?: 0,
                        completionTokens = completion ?: 0,
                    ) }
                }
            } catch (_: Exception) {
                // Silently ignore usage load failures
            }
        }
    }

    /**
     * Close the current Hermes session on the daemon.
     */
    fun closeSession() {
        viewModelScope.launch {
            try {
                repository.closeSession(sessionId)
            } catch (e: Exception) {
                addError("Failed to close session: ${e.message}")
            }
        }
    }

    // ── Attachment helpers ─────────────────────────────────────────────────

    /** Set the URI of an image picked from the gallery. */
    fun attachImage(uri: android.net.Uri) {
        _uiState.update { it.copy(attachedImageUri = uri.toString()) }
    }

    /** Clear the currently attached image. */
    fun removeAttachment() {
        _uiState.update { it.copy(attachedImageUri = null) }
    }

    /** Record token usage from the last response. */
    fun recordTokenUsage(prompt: Int, completion: Int) {
        _uiState.update { it.copy(promptTokens = prompt, completionTokens = completion) }
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun startColdStartTimer() {
        coldStartJob?.cancel()
        coldStartJob = viewModelScope.launch {
            delay(7_000)
            _uiState.update { state ->
                if (state.isStreaming) state.copy(coldStartWarning = true) else state
            }
        }
    }

    private fun addMessage(item: ChatItem) {
        _uiState.update { state ->
            state.copy(messages = state.messages + item)
        }
    }

    private fun addError(message: String) {
        addMessage(ChatItem.ErrorItem(message = message, isColdStart = false))
        _uiState.update { state ->
            state.copy(isStreaming = false, coldStartWarning = false)
        }
        coldStartJob?.cancel()
    }

    private fun appendToLastAssistant(text: String) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            for (i in msgs.indices.reversed()) {
                if (msgs[i] is ChatItem.AssistantMessage) {
                    val msg = msgs[i] as ChatItem.AssistantMessage
                    msgs[i] = msg.copy(text = msg.text + text)
                    break
                }
            }
            state.copy(messages = msgs)
        }
    }

    /**
     * Drop everything after the last user message, then the user message
     * itself — mirrors what `session.undo` removes server-side.
     */
    private fun removeLastTurn() {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            while (msgs.isNotEmpty() && msgs.last() !is ChatItem.UserMessage) {
                msgs.removeAt(msgs.size - 1)
            }
            if (msgs.isNotEmpty() && msgs.last() is ChatItem.UserMessage) {
                msgs.removeAt(msgs.size - 1)
            }
            state.copy(messages = msgs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        coldStartJob?.cancel()
    }
}
