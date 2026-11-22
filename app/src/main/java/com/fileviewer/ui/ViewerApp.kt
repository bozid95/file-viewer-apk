package com.fileviewer.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fileviewer.MainActivity
import com.fileviewer.detect.OpenedFile
import com.fileviewer.files.Dir
import com.fileviewer.files.FileEntry
import com.fileviewer.files.Folders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File yang dikirim dari aplikasi lain. Objek baru per intent, agar file yang sama bisa dibuka ulang. */
class ExternalOpen(val uri: Uri)

/** Daftar file yang bisa di-swipe, dimulai dari [start]. */
class ViewerState(val files: List<FileEntry>, val start: Int)

@Composable
fun ViewerApp(external: ExternalOpen?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<ViewerState?>(null) }
    var dirs by remember { mutableStateOf<List<Dir>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    /** Buka satu file; jika foldernya sudah diizinkan, file lain di folder itu ikut bisa di-swipe. */
    fun openSingle(uri: Uri) {
        scope.launch {
            busy = true
            viewer = withContext(Dispatchers.IO) {
                runCatching { Folders.siblingsOf(context, uri) }.getOrNull()
                    ?.let { (files, index) -> ViewerState(files, index) }
                    ?: Folders.queryNameSize(context, uri).let { (name, size) ->
                        ViewerState(listOf(FileEntry(uri, name ?: "file", size)), 0)
                    }
            }
            busy = false
        }
    }

    fun enterDir(dir: Dir) {
        dirs = dirs + dir
        Folders.saveLast(context, dir)
    }

    LaunchedEffect(external) { external?.let { openSingle(it.uri) } }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openSingle(it) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            val root = withContext(Dispatchers.IO) {
                runCatching {
                    Folders.takePermission(context, tree)
                    Folders.rootOf(context, tree)
                }.getOrNull()
            }
            if (root == null) {
                Toast.makeText(context, "Folder tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            } else {
                dirs = emptyList()
                enterDir(root)
            }
        }
    }
    val openFolder = {
        val last = Folders.last(context)
        if (last != null) dirs = listOf(last) else folderPicker.launch(null)
    }

    // Urutan tombol back: viewer → hasil pencarian/daftar file → folder induk → layar utama → keluar.
    BackHandler(enabled = viewer != null) { viewer = null }
    BackHandler(enabled = viewer == null && dirs.isNotEmpty()) {
        dirs = dirs.dropLast(1)
        dirs.lastOrNull()?.let { Folders.saveLast(context, it) }
    }

    Box(Modifier.fillMaxSize()) {
        // Browser tetap ada di bawah viewer, supaya hasil pencarian & posisi scroll tidak hilang.
        if (dirs.isNotEmpty()) {
            BrowserScreen(
                dir = dirs.last(),
                onOpenDir = ::enterDir,
                onOpenFile = { files, index -> viewer = ViewerState(files, index) },
                onChangeFolder = { folderPicker.launch(null) },
                backEnabled = viewer == null,
            )
        } else {
            HomeScreen(
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                onOpenFolder = openFolder,
            )
        }
        viewer?.let { v -> key(v) { ViewerPager(v) } }
        if (busy) LoadingBox()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onPickFile: () -> Unit, onOpenFolder: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("File Viewer") }) }) { padding ->
        Box(Modifier.padding(padding)) {
            MessageBox(
                "Buka file apa saja",
                "Jenis file dideteksi otomatis dari isinya, jadi file yang di-rename (mis. .bin) tetap bisa dibuka.\n\n" +
                    "Buka Folder → cari file (mis. \".bin\") → ketuk file → geser kiri/kanan untuk file berikutnya.",
            ) {
                Button(onClick = onOpenFolder) { Text("Buka Folder") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPickFile) { Text("Pilih 1 File") }
            }
        }
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
