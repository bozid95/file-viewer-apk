package com.fileviewer.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileviewer.detect.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** Batas teks yang dimuat agar file log raksasa tetap cepat dibuka. */
private const val MAX_TEXT_BYTES = 1 shl 20 // 1 MB

private class TextContent(val lines: List<String>, val truncated: Boolean)

@Composable
fun TextScreen(file: OpenedFile) {
    val context = LocalContext.current
    val result by produceState<Result<TextContent>?>(null, file.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { readText(context, file.uri, file.type.charset ?: Charsets.UTF_8) }
        }
    }
    val r = result
    when {
        r == null -> LoadingBox()
        r.isFailure -> ViewerError(file, r.exceptionOrNull()?.message.orEmpty())
        else -> TextBody(r.getOrThrow())
    }
}

@Composable
private fun TextBody(content: TextContent) {
    Column(Modifier.fillMaxSize()) {
        if (content.truncated) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "File besar: hanya 1 MB pertama yang ditampilkan.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(content.lines) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

private fun readText(context: Context, uri: Uri, charset: Charset): TextContent {
    val buffer = ByteArray(MAX_TEXT_BYTES)
    var read = 0
    var truncated = false
    val stream = context.contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
    stream.use {
        while (read < buffer.size) {
            val r = it.read(buffer, read, buffer.size - read)
            if (r < 0) break
            read += r
        }
        if (read == buffer.size) truncated = it.read() != -1
    }
    val text = String(buffer, 0, read, charset).removePrefix("﻿")
    return TextContent(text.lines(), truncated)
}
