package com.fileviewer.detect

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class OpenedFile(
    val uri: Uri,
    val name: String,
    val size: Long?,
    val type: FileType,
    val header: ByteArray,
    val headerLength: Int,
)

/** Membaca nama, ukuran, dan beberapa KB pertama file, lalu mendeteksi jenisnya. Jalankan di thread IO. */
fun loadFile(context: Context, uri: Uri): OpenedFile {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size: Long? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
    }

    val buffer = ByteArray(FileDetector.HEADER_SIZE)
    var read = 0
    val stream = resolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
    stream.use {
        while (read < buffer.size) {
            val r = it.read(buffer, read, buffer.size - read)
            if (r < 0) break
            read += r
        }
    }
    return OpenedFile(uri, name, size, FileDetector.detect(buffer, read), buffer, read)
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
