package com.luka.hermes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Time display ──────────────────────────────────────────────────────────────

@Composable
fun TimeDisplay(
    epochMillis: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val relativeTime = remember(epochMillis) {
        getRelativeTimeString(epochMillis, System.currentTimeMillis())
    }
    Text(
        text = relativeTime,
        modifier = modifier,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
}

private fun getRelativeTimeString(epochMillis: Long, now: Long): String {
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "yesterday"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
            val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val fmt = if (cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR))
                SimpleDateFormat("MMM d", Locale.getDefault())
            else
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            fmt.format(Date(epochMillis))
        }
    }
}

// ── Scroll-to-bottom FAB ──────────────────────────────────────────────────────

@Composable
fun ScrollToBottomFAB(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + expandIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)) + shrinkOut(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Scroll to bottom",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Streaming cursor ──────────────────────────────────────────────────────────

@Composable
fun StreamingCursor(isStreaming: Boolean) {
    if (!isStreaming) return
    val alpha by animateFloatAsState(
        targetValue = if (isStreaming) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "cursor",
    )
    Text(
        text = "●",
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        style = MaterialTheme.typography.bodyMedium,
    )
}
