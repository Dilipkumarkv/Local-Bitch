package com.dilipkumarkv.localgguf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

private sealed interface MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

@Composable
fun MarkdownContent(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        val annotated = remember(block.text, textColor) {
                            parseInlineMarkdown(block.text, textColor)
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val displayLang = if (language.isNotBlank()) language.uppercase(Locale.US) else "CODE"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayLang,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Code content
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color(0xFFF1F5F9),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    if (!input.contains("```")) {
        return listOf(MarkdownBlock.TextBlock(input))
    }

    val blocks = mutableListOf<MarkdownBlock>()
    val tokens = input.split("```")

    tokens.forEachIndexed { index, part ->
        if (index % 2 == 0) {
            // Regular text block
            if (part.isNotEmpty()) {
                blocks.add(MarkdownBlock.TextBlock(part))
            }
        } else {
            // Fenced code block
            val lines = part.split("\n", limit = 2)
            val firstLine = lines.firstOrNull()?.trim() ?: ""
            val codeBody = if (lines.size > 1) lines[1].trimEnd() else ""

            val hasLang = firstLine.isNotEmpty() && !firstLine.contains(" ") && firstLine.length <= 15
            val lang = if (hasLang) firstLine else ""
            val actualCode = if (hasLang) codeBody else part.trimEnd()

            blocks.add(MarkdownBlock.CodeBlock(language = lang, code = actualCode))
        }
    }

    return blocks
}

private fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            // Check for inline code `...`
            if (text[cursor] == '`') {
                val nextBacktick = text.indexOf('`', cursor + 1)
                if (nextBacktick != -1) {
                    val codeContent = text.substring(cursor + 1, nextBacktick)
                    val start = length
                    append(codeContent)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color(0x33888888)
                        ),
                        start,
                        length
                    )
                    cursor = nextBacktick + 1
                    continue
                }
            }

            // Check for bold **...**
            if (text.startsWith("**", cursor)) {
                val nextStar = text.indexOf("**", cursor + 2)
                if (nextStar != -1) {
                    val boldContent = text.substring(cursor + 2, nextStar)
                    val start = length
                    append(boldContent)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start,
                        length
                    )
                    cursor = nextStar + 2
                    continue
                }
            }

            // Normal character
            append(text[cursor])
            addStyle(SpanStyle(color = defaultColor), length - 1, length)
            cursor++
        }
    }
}
