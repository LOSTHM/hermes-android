package com.luka.hermes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Full-featured markdown renderer for chat messages.
 * Supports: headings, bold, italic, inline code, code blocks with copy,
 * links, lists, blockquotes, horizontal rules, and images.
 */
@Composable
fun RichMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            MarkdownBlockView(block)
        }
    }
}

// ── Data model ────────────────────────────────────────────────────────────────

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class UnorderedList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
    data class Image(val alt: String, val url: String) : MarkdownBlock
}

// ── Block-level parser ─────────────────────────────────────────────────────────

private fun parseMarkdown(input: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = input.lines()
    var i = 0
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n"))
            paragraphBuffer.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimEnd()

        // Image: ![alt](url) — standalone line
        val imgMatch = Regex("""^!\[([^\]]*)\]\(([^)]+)\)\s*$""").find(trimmed)
        if (imgMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Image(imgMatch.groupValues[1], imgMatch.groupValues[2])
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            i++ // skip closing ```
            blocks += MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n"))
            continue
        }

        // Horizontal rule
        if (trimmed.matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$"))) {
            flushParagraph()
            blocks += MarkdownBlock.HorizontalRule
            i++
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2])
            i++
            continue
        }

        // Blockquote
        if (trimmed.startsWith("> ")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && (lines[i].trimStart().startsWith("> ") || lines[i].trim().isEmpty())) {
                quoteLines += lines[i].trimStart().removePrefix("> ").trimStart()
                i++
            }
            blocks += MarkdownBlock.Blockquote(quoteLines.joinToString("\n"))
            continue
        }

        // Unordered list
        if (Regex("^[-*+]\\s+.+").containsMatchIn(trimmed)) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (i < lines.size && Regex("^[-*+]\\s+.+").containsMatchIn(lines[i].trimEnd())) {
                items += lines[i].trimEnd().replace(Regex("^[-*+]\\s+"), "")
                i++
            }
            blocks += MarkdownBlock.UnorderedList(items)
            continue
        }

        // Ordered list
        if (Regex("^\\d+\\.\\s+.+").containsMatchIn(trimmed)) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (i < lines.size && Regex("^\\d+\\.\\s+.+").containsMatchIn(lines[i].trimEnd())) {
                items += lines[i].trimEnd().replace(Regex("^\\d+\\.\\s+"), "")
                i++
            }
            blocks += MarkdownBlock.OrderedList(items)
            continue
        }

        // Blank line — flush paragraph
        if (trimmed.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        paragraphBuffer += trimmed
        i++
    }
    flushParagraph()
    return blocks
}

// ── Block renderers ────────────────────────────────────────────────────────────

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> HeadingView(block.level, block.text)
        is MarkdownBlock.Paragraph -> ParagraphView(block.text)
        is MarkdownBlock.CodeBlock -> CodeBlockView(block.language, block.code)
        is MarkdownBlock.UnorderedList -> UnorderedListView(block.items)
        is MarkdownBlock.OrderedList -> OrderedListView(block.items)
        is MarkdownBlock.Blockquote -> BlockquoteView(block.text)
        MarkdownBlock.HorizontalRule -> HorizontalRuleView()
        is MarkdownBlock.Image -> InlineImageView(block.alt, block.url)
    }
}

@Composable
private fun HeadingView(level: Int, text: String) {
    val size = when (level) {
        1 -> 24.sp
        2 -> 20.sp
        3 -> 18.sp
        else -> 16.sp
    }
    Text(
        text = parseInline(text),
        fontSize = size,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ParagraphView(text: String) {
    Text(
        text = parseInline(text),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun CodeBlockView(language: String?, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = language ?: "code",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (copied) {
                    Text(
                        text = "Copied!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(
                        onClick = {
                            copyToClipboard(context, code)
                            copied = true
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = SyntaxHighlight.highlightSyntax(code, language),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun UnorderedListView(items: List<String>) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text("•  ", fontWeight = FontWeight.Bold)
                Text(parseInline(item))
            }
        }
    }
}

@Composable
private fun OrderedListView(items: List<String>) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text("${index + 1}.  ", fontWeight = FontWeight.SemiBold)
                Text(parseInline(item))
            }
        }
    }
}

@Composable
private fun BlockquoteView(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = parseInline(text),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun HorizontalRuleView() {
    Divider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun InlineImageView(alt: String, url: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = alt,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

// ── Inline parser (bold, italic, code, links, images) ──────────────────────────

private fun parseInline(text: String) = buildAnnotatedString {
    var i = 0
    val n = text.length

    while (i < n) {
        val c = text[i]

        // Strikethrough: ~~text~~
        if (c == '~' && i + 1 < n && text[i + 1] == '~') {
            val end = text.indexOf("~~", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }

        // Inline code: `code`
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        // Bold+Italic: ***text***
        if (c == '*' && i + 2 < n && text[i + 1] == '*' && text[i + 2] == '*') {
            val end = text.indexOf("***", i + 3)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 3, end))
                }
                i = end + 3
                continue
            }
        }

        // Bold: **text**
        if (c == '*' && i + 1 < n && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1 && (end + 2 >= n || text[end + 2] != '*')) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }

        // Italic: *text* (single star, not followed by another star)
        if (c == '*' && (i + 1 >= n || text[i + 1] != '*')) {
            val end = text.indexOf('*', i + 1)
            if (end != -1 && (end + 1 >= n || text[end + 1] != '*')) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        // Image: ![alt](url) — inline
        if (c == '!' && i + 1 < n && text[i + 1] == '[') {
            val closeBracket = text.indexOf(']', i + 2)
            if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val alt = text.substring(i + 2, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    appendInlineContent("image", "$alt||$url")
                    i = closeParen + 1
                    continue
                }
            }
        }

        // Link: [text](url)
        if (c == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    withStyle(SpanStyle(
                        color = Color(0xFF1E88E5),
                        textDecoration = TextDecoration.Underline,
                    )) {
                        append(linkText)
                    }
                    i = closeParen + 1
                    continue
                }
            }
        }

        append(c.toString())
        i++
    }
}

// ── Clipboard helper ──────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("code", text))
}
