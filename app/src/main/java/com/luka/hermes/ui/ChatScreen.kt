package com.luka.hermes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luka.hermes.gateway.ConnectionState

@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(sessionId) {
        viewModel.setSession(sessionId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.let {
        if (it is ChatItem.AssistantMessage) it.text.length else 0
    }) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(uiState.connectionState)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                inputText = uiState.inputText,
                isStreaming = uiState.isStreaming,
                onInputChanged = viewModel::onInputChanged,
                onSend = viewModel::sendPrompt,
                onStop = viewModel::interrupt,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Send a message to start.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.stableId }) { item ->
                    when (item) {
                        is ChatItem.UserMessage -> UserBubble(item.text)
                        is ChatItem.AssistantMessage -> AssistantBubble(item)
                        is ChatItem.ToolCallCard -> ToolCallCardView(item)
                        is ChatItem.ThinkingBlock -> ThinkingBlockView(item)
                        is ChatItem.ErrorItem -> ErrorBubble(item)
                    }
                }
            }

            // Cold start warning overlay
            if (uiState.coldStartWarning) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text(
                        text = "Agent starting up… please wait",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    uiState.clarifyRequest?.let { req ->
        AlertDialog(
            onDismissRequest = viewModel::dismissClarify,
            title = { Text("Clarification") },
            text = { Text(req.question) },
            confirmButton = {
                TextButton(onClick = { viewModel.respondClarify(req.id, "yes") }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClarify() }) {
                    Text("Skip")
                }
            },
        )
    }

    uiState.approvalRequest?.let { req ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApproval,
            title = { Text(req.title ?: "Approval Required") },
            text = {
                Column {
                    if (req.description != null) {
                        Text(req.description)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondApproval(req.id, true) }) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondApproval(req.id, false) }) {
                    Text("Deny")
                }
            },
        )
    }
}

// ── Connection dot ────────────────────────────────────────────────────────────

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Open -> Color(0xFF4CAF50)
        else -> Color(0xFFBDBDBD)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color),
    )
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    inputText: String,
    isStreaming: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isStreaming) onSend() }),
                enabled = !isStreaming,
            )
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank(),
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────────────────

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            SimpleMarkdownText(
                text = text,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(msg: ChatItem.AssistantMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SimpleMarkdownText(text = msg.text)
                if (msg.isStreaming) {
                    val dots = when {
                        msg.text.isEmpty() -> "●"
                        msg.text.endsWith("\n") -> "..."
                        else -> " ●"
                    }
                    Text(
                        text = dots,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ── Tool call card ────────────────────────────────────────────────────────────

@Composable
private fun ToolCallCardView(card: ChatItem.ToolCallCard) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (card.status) {
                    ToolStatus.Running -> "\u2699\uFE0F" // gear
                    ToolStatus.Complete -> "\u2705"       // check
                }
                Text(text = icon, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                when (card.status) {
                    ToolStatus.Running -> {
                        Text(
                            text = "running…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ToolStatus.Complete -> {
                        Text(
                            text = "done",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }
            }

            if (card.summary != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = card.summary!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (card.args != null) {
                        Text(
                            text = "Args:",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = card.args!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    if (card.result != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Result:",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = card.result!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Thinking block ────────────────────────────────────────────────────────────

@Composable
private fun ThinkingBlockView(block: ChatItem.ThinkingBlock) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "\u25BC" else "\u25B6",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "\uD83E\uDD14 Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${block.text.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

// ── Error bubble ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBubble(item: ChatItem.ErrorItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = item.message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// ── Minimal markdown text (code blocks only) ──────────────────────────────────

@Composable
private fun SimpleMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return

    val segments = remember(text) { parseCodeBlocks(text) }

    Column(modifier = modifier) {
        for (segment in segments) {
            when (segment) {
                is MarkdownSegment.Text -> {
                    Text(
                        text = segment.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is MarkdownSegment.CodeBlock -> {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = segment.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class CodeBlock(val content: String) : MarkdownSegment()
}

private fun parseCodeBlocks(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    var remaining = text
    while (true) {
        val start = remaining.indexOf("```")
        if (start == -1) {
            if (remaining.isNotEmpty()) segments.add(MarkdownSegment.Text(remaining))
            break
        }
        // text before the code block
        if (start > 0) {
            segments.add(MarkdownSegment.Text(remaining.substring(0, start)))
        }
        val afterStart = remaining.substring(start + 3)
        val end = afterStart.indexOf("```")
        if (end == -1) {
            // unclosed backtick — treat rest as text
            segments.add(MarkdownSegment.Text("```" + afterStart))
            break
        }
        // extract code content, stripping optional language tag (first line)
        val codeContent = afterStart.substring(0, end)
        val codeLines = codeContent.split("\n", limit = 2)
        val code = if (codeLines.size > 1) codeLines[1] else codeLines[0]
        segments.add(MarkdownSegment.CodeBlock(code.trimEnd()))
        remaining = afterStart.substring(end + 3)
    }
    return segments
}
