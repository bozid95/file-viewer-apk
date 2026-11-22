package com.fileviewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileviewer.detect.FileDetector
import com.fileviewer.detect.OpenedFile
import com.fileviewer.detect.hexDump

/** Untuk format yang tidak dikenali, atau dikenali tapi tidak bisa ditampilkan di sini (ZIP, DOCX, WMV, ...). */
@Composable
fun UnknownScreen(file: OpenedFile) {
    val context = LocalContext.current
    val type = file.type
    val unknown = type.label == FileDetector.UNKNOWN_LABEL
    val hex = remember(file) { hexDump(file.header, minOf(file.headerLength, 512)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(type.label, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            if (unknown) "Jenis file tidak bisa dikenali dari isinya. Di bawah ini isi awal file dalam bentuk hex."
            else "Format ini dikenali (${type.mime}), tetapi tidak ditampilkan di aplikasi ini. " +
                "Gunakan aplikasi lain untuk membukanya.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openWith(context, file.uri, type.mime) }) { Text("Buka dengan aplikasi lain") }
        Spacer(Modifier.height(24.dp))
        Text("Header file (${minOf(file.headerLength, 512)} byte pertama)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    hex,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }
}
