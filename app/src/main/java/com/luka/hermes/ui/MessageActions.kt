package com.luka.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// ── Edit message dialog ───────────────────────────────────────────────────────

/**
 * Material3 dialog for editing a message. The text field is pre-filled with
 * the current [text]; tapping **Save** invokes [onConfirm] with the trimmed
 * value. Empty input disables the Save button so a user cannot blank out
 * a message accidentally.
 */
@Composable
fun MessageEditDialog(
    text: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = "Edit message",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Make your changes and resend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft.trim()) },
                enabled = draft.isNotBlank(),
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Delete confirmation dialog ────────────────────────────────────────────────

/**
 * Material3 dialog asking the user to confirm message deletion. Tapping
 * **Delete** invokes [onConfirm]; **Cancel** and outside-tap invoke [onDismiss].
 */
@Composable
fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            // Subtle "trash" hint via glyph — keeps the dialog icon-free
            // of extra dependencies while staying expressive.
            Text(
                text = "🗑",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Delete message?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "This message will be permanently removed from the conversation. " +
                    "This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Regenerate button ─────────────────────────────────────────────────────────

/**
 * Compact text button used to trigger regeneration of the last assistant
 * response. Renders a single line of expressive text using the small label
 * style and a leading ↻ glyph so it reads as an action even without an icon.
 */
@Composable
fun RegenerateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp,
        ),
    ) {
        Text(
            text = "↻ Regenerate",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

// ── Token usage bar ───────────────────────────────────────────────────────────

/**
 * Compact horizontal row showing prompt + completion token counts with a
 * thin progress indicator below. The bar visualises the completion share
 * of the combined prompt+completion total, giving a quick at-a-glance ratio
 * of how much of the round-trip was spent on the response.
 */
@Composable
fun TokenUsageBar(
    promptTokens: Int,
    completionTokens: Int,
    modifier: Modifier = Modifier,
) {
    val safePrompt = promptTokens.coerceAtLeast(0)
    val safeCompletion = completionTokens.coerceAtLeast(0)
    val total = safePrompt + safeCompletion
    val completionRatio = if (total > 0) {
        safeCompletion.toFloat() / total.toFloat()
    } else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Numeric breakdown — single line, small label style. The two
        // counts sit in their own Row so spacing stays predictable
        // regardless of the bar's measured width.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "↑ $safePrompt prompt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "↓ $safeCompletion completion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Track + indicator give the progress-bar affordance while
            // staying low-chrome; the ratio is the completion share.
            LinearProgressIndicator(
                progress = { completionRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }

        Text(
            text = if (total > 0) {
                "${(completionRatio * 100).toInt()}%"
            } else {
                "—"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
