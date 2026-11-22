package com.fileviewer.detect

import android.content.Context
import android.net.Uri
import com.fileviewer.files.Folders

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
    val (queriedName, size) = Folders.queryNameSize(context, uri)
    val name = queriedName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    val buffer = ByteArray(FileDetector.HEADER_SIZE)
    var read = 0
    val stream = context.contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
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
