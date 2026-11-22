package com.fileviewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.fileviewer.detect.Kind
import com.fileviewer.detect.OpenedFile
import com.fileviewer.detect.formatSize
import com.fileviewer.detect.loadFile
import com.fileviewer.files.FileEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LoadState {
    data object Loading : LoadState
    class Ready(val file: OpenedFile) : LoadState
    class Failed(val message: String) : LoadState
}

/** Viewer layar penuh: geser kiri/kanan untuk pindah ke file sebelumnya/berikutnya. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerPager(state: ViewerState) {
    val context = LocalContext.current
    val files = state.files
    val pager = rememberPagerState(initialPage = state.start) { files.size }
    val loaded = remember { mutableStateMapOf<Int, OpenedFile>() }
    val page = pager.currentPage
    val current = loaded[page]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            current?.name ?: files[page].name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val counter = if (files.size > 1) "${page + 1} / ${files.size}" else null
                        Text(
                            listOfNotNull(counter, current?.let(::subtitle)).joinToString(" · "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    if (current != null) {
                        TextButton(onClick = { openWith(context, current.uri, current.type.mime) }) { Text("Buka di…") }
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { index ->
            FilePage(
                entry = files[index],
                active = pager.settledPage == index,
                onLoaded = { loaded[index] = it },
            )
        }
    }
}

@Composable
private fun FilePage(entry: FileEntry, active: Boolean, onLoaded: (OpenedFile) -> Unit) {
    val context = LocalContext.current
    val state by produceState<LoadState>(LoadState.Loading, entry.uri) {
        value = try {
            LoadState.Ready(withContext(Dispatchers.IO) { loadFile(context, entry.uri) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
    LaunchedEffect(state) { (state as? LoadState.Ready)?.let { onLoaded(it.file) } }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            LoadState.Loading -> LoadingBox()
            is LoadState.Failed -> MessageBox("Gagal membuka file", s.message)
            is LoadState.Ready -> FileContent(s.file, active)
        }
    }
}

@Composable
private fun FileContent(file: OpenedFile, active: Boolean) {
    val type = file.type
    if (type.kind == Kind.OTHER || !type.viewable) {
        UnknownScreen(file)
        return
    }
    when (type.kind) {
        Kind.IMAGE -> ImageScreen(file)
        Kind.VIDEO, Kind.AUDIO -> MediaScreen(file, active)
        Kind.PDF -> PdfScreen(file)
        Kind.TEXT -> TextScreen(file)
        Kind.OTHER -> UnknownScreen(file)
    }
}

/** Contoh: "MP4 · 12.3 MB · terdeteksi dari isi (bukan .bin)" */
private fun subtitle(file: OpenedFile): String = buildList {
    add(file.type.label)
    file.size?.let { add(formatSize(it)) }
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val expected = file.type.extension
    if (expected.isNotEmpty() && ext != expected && !(ext == "jpeg" && expected == "jpg")) {
        add(if (ext.isEmpty()) "terdeteksi dari isi" else "terdeteksi dari isi (bukan .$ext)")
    }
}.joinToString(" · ")
