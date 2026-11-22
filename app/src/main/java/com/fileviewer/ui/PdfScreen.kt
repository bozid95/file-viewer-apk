package com.fileviewer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fileviewer.detect.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

@Composable
fun PdfScreen(file: OpenedFile) {
    val context = LocalContext.current
    val result by produceState<Result<PdfFile>?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { runCatching { PdfFile.open(context, file.uri) } }
    }
    val r = result
    val doc = r?.getOrNull()
    DisposableEffect(doc) { onDispose { doc?.close() } }

    when {
        r == null -> LoadingBox()
        doc == null -> ViewerError(file, r.exceptionOrNull()?.message.orEmpty())
        else -> PdfPages(doc)
    }
}

@Composable
private fun PdfPages(doc: PdfFile) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF525659))) {
        val widthPx = constraints.maxWidth.coerceIn(1, 2048)
        ZoomBox(panVertical = false) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(doc.pageCount) { index -> PdfPage(doc, index, widthPx) }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfFile, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) { runCatching { doc.render(index, widthPx) }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(doc.aspectRatios[index]).background(Color.White)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = "Halaman ${index + 1}", modifier = Modifier.fillMaxSize())
        }
    }
}

/** Pembungkus PdfRenderer bawaan Android yang aman dipakai dari beberapa thread. */
class PdfFile private constructor(private val pfd: ParcelFileDescriptor) : Closeable {
    private val renderer = PdfRenderer(pfd)
    private val lock = Any()
    private var closed = false

    val pageCount: Int = renderer.pageCount
    val aspectRatios: FloatArray = FloatArray(pageCount) { i ->
        val page = renderer.openPage(i)
        try {
            (page.width.toFloat() / page.height).coerceIn(0.1f, 10f)
        } finally {
            page.close()
        }
    }

    fun render(index: Int, width: Int): Bitmap? = synchronized(lock) {
        if (closed) return null
        val page = renderer.openPage(index)
        try {
            val height = (width / aspectRatios[index]).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            page.close()
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfFile {
            val resolver = context.contentResolver
            resolver.openFileDescriptor(uri, "r")?.let { pfd ->
                try {
                    return PdfFile(pfd)
                } catch (e: SecurityException) {
                    pfd.close()
                    throw IllegalStateException("PDF dilindungi password", e)
                } catch (e: Exception) {
                    pfd.close() // mis. stream tidak bisa di-seek → salin dulu ke cache
                }
            }
            val copy = File(context.cacheDir, "view.pdf")
            val input = resolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
            input.use { inp -> copy.outputStream().use { inp.copyTo(it) } }
            return PdfFile(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }
}
