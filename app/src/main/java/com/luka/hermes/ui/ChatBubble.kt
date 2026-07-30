package com.luka.hermes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.delay

private enum class BubbleSide { User, Assistant }

@Composable
private fun rememberCopyWithToast(
    context: Context,
    label: String = "Copied"
): (String) -> Unit {
    var toastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(toastVisible) {
        if (toastVisible) {
            delay(1500)
            toastVisible = false
        }
    }

    val onCopy: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("chat_message", text)
        clipboard.setPrimaryClip(clip)
        toastVisible = true
    }

    return onCopy
}

@Composable
private fun CopiedToast(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Copied",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ChatBubbleContainer(
    side: BubbleSide,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onLongPress: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrangement = if (side == BubbleSide.User) Arrangement.End else Arrangement.Start
    val alignment = if (side == BubbleSide.User) Alignment.End else Alignment.Start

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .pointerInput(onLongPress, onCopy) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onTap = {}
                    )
                },
            horizontalAlignment = alignment,
            content = content
        )
    }
}

@Composable
private fun ContextActions(
    visible: Boolean,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(onClick = {
                    onCopy()
                    onDismiss()
                }) {
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (onRegenerate != null) {
                    TextButton(onClick = {
                        onRegenerate()
                        onDismiss()
                    }) {
                        Text(
                            text = "Regenerate",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleTimeLabel(
    timestamp: Long,
    isStreaming: Boolean,
) {
    if (isStreaming) {
        StreamingCursor(isStreaming = true)
    } else {
        TimeDisplay(epochMillis = timestamp)
    }
}

@Composable
fun UserChatBubble(
    text: String,
    timestamp: Long,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
)
    val context = LocalContext.current
    var menuVisible by remember { mutableStateOf(false) }
    var toastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(toastVisible) {
        if (toastVisible) {
            delay(1500)
            toastVisible = false
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 280)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 320),
                    initialOffsetY = { it / 3 }
                )
    ) {
        ChatBubbleContainer(
            side = BubbleSide.User,
            onCopy = { onCopy(text); toastVisible = true },
            onRegenerate = null,
            onLongPress = { menuVisible = true }
        ) {
            // Context actions shown above bubble on long press
            ContextActions(
                visible = menuVisible,
                onCopy = { onCopy(text); toastVisible = true },
                onRegenerate = null,
                onDismiss = { menuVisible = false }
            )

            // The bubble itself
            Surface(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 6.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { menuVisible = true }
                        )
                    }
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Time label below
            TimeDisplay(epochMillis = timestamp)
        }
    }

    // Toast overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        CopiedToast(visible = toastVisible)
    }
}

@Composable
fun AssistantChatBubble(
    text: String,
    timestamp: Long,
    isStreaming: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    var menuVisible by remember { mutableStateOf(false) }
    var toastVisible by remember { mutableStateOf(false) }

    LaunchedEffect(toastVisible) {
        if (toastVisible) {
            delay(1500)
            toastVisible = false
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 280)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 320),
                    initialOffsetY = { it / 3 }
                )
    ) {
        ChatBubbleContainer(
            side = BubbleSide.Assistant,
            onCopy = { onCopy(text); toastVisible = true },
            onRegenerate = onRegenerate,
            onLongPress = { menuVisible = true }
        ) {
            // Context actions shown above bubble on long press
            ContextActions(
                visible = menuVisible,
                onCopy = { onCopy(text); toastVisible = true },
                onRegenerate = onRegenerate,
                onDismiss = { menuVisible = false }
            )

            // The bubble itself
            Surface(
                shape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { menuVisible = true }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                ) {
                    RichMarkdownText(markdown = text)

                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        StreamingCursor(isStreaming = true)
                    }
                }
            }

            // Time label below
            TimeDisplay(epochMillis = timestamp)
        }
    }

    // Toast overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        CopiedToast(visible = toastVisible)
    }
}