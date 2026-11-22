package com.fileviewer.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fileviewer.detect.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sisi terpanjang maksimal; gambar lebih besar di-downsample agar tidak kehabisan memori. */
private const val MAX_SIDE = 4096

@Composable
fun ImageScreen(file: OpenedFile) {
    val context = LocalContext.current
    val result by produceState<Result<Drawable>?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(context, file.uri) } }
    }
    val r = result
    when {
        r == null -> LoadingBox()
        r.isFailure -> ViewerError(file, r.exceptionOrNull()?.message.orEmpty())
        else -> ZoomBox { DrawableView(r.getOrThrow()) }
    }
}

@Composable
private fun DrawableView(drawable: Drawable) {
    if (drawable is BitmapDrawable) {
        Image(
            drawable.bitmap.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
        )
    } else {
        // GIF / WebP animasi (AnimatedImageDrawable)
        AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = {
                it.setImageDrawable(drawable)
                (drawable as? Animatable)?.start()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Decoder Android membaca format dari isi file, bukan ekstensi. */
private fun decodeImage(context: Context, uri: Uri): Drawable {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) decoder.setTargetSampleSize((side + MAX_SIDE - 1) / MAX_SIDE)
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val side = maxOf(bounds.outWidth, bounds.outHeight)
    if (side <= 0) error("Format gambar tidak didukung perangkat ini")
    var sample = 1
    while (side / sample > MAX_SIDE) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Gambar tidak bisa dibaca")
    return BitmapDrawable(context.resources, bitmap)
}
