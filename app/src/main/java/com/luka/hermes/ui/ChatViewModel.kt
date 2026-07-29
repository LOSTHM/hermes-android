package com.luka.hermes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luka.hermes.gateway.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI data types ──────────────────────────────────────────────────────────────

enum class ToolStatus { Running, Complete }

sealed class ChatItem {
    data class UserMessage(val text: String) : ChatItem() {
        override val stableId: String get() = "user-${hashCode()}"
    }
    data class AssistantMessage(
        val text: String,
        val isStreaming: Boolean,
    ) : ChatItem() {
        override val stableId: String get() = "assistant-${hashCode()}"
    }
    data class ToolCallCard(
        val name: String,
        val status: ToolStatus,
        val args: String? = null,
        val result: String? = null,
        val summary: String? = null,
    ) : ChatItem() {
        override val stableId: String get() = "tool-$name-${hashCode()}"
    }
    data class ThinkingBlock(
        val text: String,
    ) : ChatItem() {
        override val stableId: String get() = "think-${hashCode()}"
    }
    data class ErrorItem(
        val message: String,
        val isColdStart: Boolean,
    ) : ChatItem() {
        override val stableId: String get() = "error-${hashCode()}"
    }

    abstract val stableId: String
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
)

// ── ViewModel ──────────────────────────────────────────────────────────────────

class ChatViewModel : ViewModel() {

    private val repository = HermesClient.repository
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
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendPrompt() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || sessionId.isEmpty()) return

        val userMsg = ChatItem.UserMessage(text = text)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isStreaming = true,
            coldStartWarning = false,
        )

        startColdStartTimer()

        streamJob = viewModelScope.launch {
            try {
                repository.sendPrompt(sessionId, text).collect { event ->
                    processEvent(event)
                }
            } catch (e: Exception) {
                addError(e.message ?: "Stream error")
            } finally {
                _uiState.value = _uiState.value.copy(isStreaming = false)
                coldStartJob?.cancel()
            }
        }
    }

    fun interrupt() {
        streamJob?.cancel()
        streamJob = null
        coldStartJob?.cancel()
        viewModelScope.launch {
            try {
                repository.interruptSession(sessionId)
            } catch (_: Exception) { }
        }
        val msgs = _uiState.value.messages.toMutableList()
        // mark last assistant message as not streaming
        for (i in msgs.indices.reversed()) {
            if (msgs[i] is ChatItem.AssistantMessage) {
                val msg = msgs[i] as ChatItem.AssistantMessage
                msgs[i] = msg.copy(isStreaming = false)
                break
            }
        }
        _uiState.value = _uiState.value.copy(
            messages = msgs,
            isStreaming = false,
            coldStartWarning = false,
        )
    }

    fun respondClarify(id: String, response: String) {
        viewModelScope.launch {
            try {
                repository.respondClarify(id, response)
            } catch (_: Exception) { }
        }
        _uiState.value = _uiState.value.copy(clarifyRequest = null)
    }

    fun dismissClarify() {
        _uiState.value = _uiState.value.copy(clarifyRequest = null)
    }

    fun respondApproval(id: String, approved: Boolean) {
        viewModelScope.launch {
            try {
                repository.respondApproval(id, approved)
            } catch (_: Exception) { }
        }
        _uiState.value = _uiState.value.copy(approvalRequest = null)
    }

    fun dismissApproval() {
        _uiState.value = _uiState.value.copy(approvalRequest = null)
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private fun startColdStartTimer() {
        coldStartJob?.cancel()
        coldStartJob = viewModelScope.launch {
            delay(7_000)
            if (_uiState.value.isStreaming) {
                _uiState.value = _uiState.value.copy(coldStartWarning = true)
            }
        }
    }

    private fun processEvent(event: GatewayEvent) {
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
                // complete gives the full text; use it to replace the streaming text
                val fullText = event.payload?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                val msgs = _uiState.value.messages.toMutableList()
                for (i in msgs.indices.reversed()) {
                    if (msgs[i] is ChatItem.AssistantMessage) {
                        val msg = msgs[i] as ChatItem.AssistantMessage
                        msgs[i] = msg.copy(text = fullText, isStreaming = false)
                        break
                    }
                }
                _uiState.value = _uiState.value.copy(
                    messages = msgs,
                    isStreaming = false,
                )
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
                // update the last RUNNING tool card for this name
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

    private fun addMessage(item: ChatItem) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + item,
        )
    }

    private fun addError(message: String) {
        addMessage(ChatItem.ErrorItem(message = message, isColdStart = false))
        _uiState.value = _uiState.value.copy(isStreaming = false)
        coldStartJob?.cancel()
    }

    private fun appendToLastAssistant(text: String) {
        val msgs = _uiState.value.messages.toMutableList()
        for (i in msgs.indices.reversed()) {
            if (msgs[i] is ChatItem.AssistantMessage) {
                val msg = msgs[i] as ChatItem.AssistantMessage
                msgs[i] = msg.copy(text = msg.text + text)
                break
            }
        }
        _uiState.value = _uiState.value.copy(messages = msgs)
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        coldStartJob?.cancel()
    }
}
