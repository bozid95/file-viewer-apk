package com.fileviewer.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.MainActivity
import com.fileviewer.detect.Kind
import com.fileviewer.detect.OpenedFile
import com.fileviewer.detect.formatSize
import com.fileviewer.detect.loadFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LoadState {
    data object Empty : LoadState
    data object Loading : LoadState
    class Ready(val file: OpenedFile) : LoadState
    class Failed(val message: String) : LoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerApp(uri: Uri?, onPick: (Uri) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onPick) }
    val pick = { picker.launch(arrayOf("*/*")) }

    val state by produceState<LoadState>(LoadState.Empty, uri) {
        if (uri == null) {
            value = LoadState.Empty
            return@produceState
        }
        value = LoadState.Loading
        value = try {
            LoadState.Ready(withContext(Dispatchers.IO) { loadFile(context, uri) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val s = state
                    if (s is LoadState.Ready) {
                        Column {
                            Text(
                                s.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                subtitle(s.file), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text("File Viewer")
                    }
                },
                actions = {
                    val s = state
                    if (s is LoadState.Ready) {
                        TextButton(onClick = { openWith(context, s.file.uri, s.file.type.mime) }) { Text("Buka di…") }
                    }
                    TextButton(onClick = pick) { Text("Pilih") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LoadState.Empty -> EmptyScreen(pick)
                LoadState.Loading -> LoadingBox()
                is LoadState.Failed -> MessageBox("Gagal membuka file", s.message) {
                    Button(onClick = pick) { Text("Pilih file lain") }
                }
                is LoadState.Ready -> FileContent(s.file)
            }
        }
    }
}

@Composable
private fun FileContent(file: OpenedFile) {
    val type = file.type
    if (type.kind == Kind.OTHER || !type.viewable) {
        UnknownScreen(file)
        return
    }
    when (type.kind) {
        Kind.IMAGE -> ImageScreen(file)
        Kind.VIDEO, Kind.AUDIO -> MediaScreen(file)
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

@Composable
private fun EmptyScreen(onPick: () -> Unit) {
    MessageBox(
        "Buka file apa saja",
        "Jenis file dideteksi otomatis dari isinya, jadi file yang di-rename (mis. .bin) tetap bisa dibuka.\n\n" +
            "Bisa juga dari file manager: Buka dengan → File Viewer.",
    ) {
        Button(onClick = onPick) { Text("Pilih File") }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageBox(title: String, message: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        action()
    }
}

/** Ditampilkan saat viewer gagal (codec tidak didukung, file rusak, dll). */
@Composable
fun ViewerError(file: OpenedFile, detail: String) {
    val context = LocalContext.current
    MessageBox("Tidak bisa ditampilkan", "Terdeteksi sebagai ${file.type.label}, tetapi gagal dibuka di sini.\n$detail") {
        OutlinedButton(onClick = { openWith(context, file.uri, file.type.mime) }) { Text("Buka dengan aplikasi lain") }
    }
}

/** Meneruskan file ke aplikasi lain dengan MIME type HASIL DETEKSI, bukan dari ekstensi. */
fun openWith(context: Context, uri: Uri, mime: String) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(view, "Buka dengan")
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Tidak ada aplikasi untuk $mime", Toast.LENGTH_SHORT).show()
    }
}
