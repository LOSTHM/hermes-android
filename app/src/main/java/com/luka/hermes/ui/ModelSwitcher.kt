package com.luka.hermes.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// 1. ModelDropdownInChat
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-chat model selector. Designed to sit inside a [TopAppBar]'s `title` slot.
 *
 * Material3 expressive look:
 *   • Anchor = pill-shaped `Surface` (surfaceContainerHigh) with a Memory glyph,
 *     the selected model name, and a drop-down chevron.
 *   • Menu items show a leading check for the current selection.
 *
 * If [models] is empty (e.g. the user hasn't tested the API connection yet),
 * the component degrades to a plain text label so the TopAppBar still renders.
 *
 * @param models    Models available for the active provider (from Settings test).
 * @param selected  Currently active model id.
 * @param onSelect  Invoked when the user picks a new model from the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdownInChat(
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to choose → just show the current model as static text.
    if (models.isEmpty()) {
        Text(
            text = selected.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        // ── Anchor chip ────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier
                .menuAnchor()
                .padding(horizontal = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 6.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selected,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Dropdown menu ──────────────────────────────────────────────────
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                val isSelected = model == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold
                            else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(model)
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. ContextUsageIndicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Visualises context-window consumption as a small ring + numeric label.
 *
 * Layout:  ⟨ ring 32dp ⟩  1.2k / 8k tokens
 *                            15% context
 *
 * Colour transitions:
 *   • < 80%  →  primary
 *   • ≥ 80%  →  error (red)
 *
 * Pass any (usedTokens, maxTokens) pair; the indicator clamps progress to
 * [0f, 1f] so an overflow renders as a full red ring rather than crashing
 * the layout.
 *
 * @param usedTokens Tokens currently occupied by the conversation context.
 * @param maxTokens  Model's effective context window.
 */
@Composable
fun ContextUsageIndicator(
    usedTokens: Int,
    maxTokens: Int,
    modifier: Modifier = Modifier,
) {
    val safeMax = maxTokens.coerceAtLeast(1)
    val ratio = (usedTokens.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)

    val isWarn = ratio >= 0.80f
    val targetColor = if (isWarn) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250),
        label = "contextColor",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 300),
        label = "contextProgress",
    )

    val percent = (ratio * 100).toInt()
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Ring ───────────────────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp),
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                trackColor = trackColor,
                color = animatedColor,
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = "$percent",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = animatedColor,
            )
        }

        Spacer(Modifier.width(8.dp))

        // ── Label column ───────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${formatTokenCount(usedTokens)} / " +
                    "${formatTokenCount(maxTokens)} tokens",
                style = MaterialTheme.typography.labelMedium,
                color = animatedColor,
                maxLines = 1,
            )
            Text(
                text = "Context",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Compact token formatter:  `812 → "812"`,  `1200 → "1.2k"`,  `8000 → "8k"`,
 *  `1_500_000 → "1.5M"`.
 */
private fun formatTokenCount(n: Int): String {
    if (n < 1000) return n.toString()
    val k = n / 1000.0
    if (k < 1000) {
        return if (k >= 10) "${k.toInt()}k"
        else String.format("%.1fk", k)
    }
    val m = n / 1_000_000.0
    return if (m >= 10) "${m.toInt()}M"
    else String.format("%.1fM", m)
}