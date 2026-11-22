package com.fileviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fileviewer.detect.formatSize
import com.fileviewer.files.Dir
import com.fileviewer.files.FileEntry
import com.fileviewer.files.Folders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Daftar isi folder + pencarian nama file (termasuk subfolder). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    dir: Dir,
    onOpenDir: (Dir) -> Unit,
    onOpenFile: (files: List<FileEntry>, index: Int) -> Unit,
    onChangeFolder: () -> Unit,
    /** false saat viewer sedang terbuka di atas browser, agar tombol back menutup viewer dulu. */
    backEnabled: Boolean = true,
) {
    val context = LocalContext.current
    var query by remember(dir) { mutableStateOf("") }

    // Back saat sedang mencari → hapus pencarian dulu, belum keluar folder.
    BackHandler(enabled = backEnabled && query.isNotEmpty()) { query = "" }

    val listing by produceState<Result<List<FileEntry>>?>(null, dir) {
        value = withContext(Dispatchers.IO) { runCatching { Folders.list(context, dir) } }
    }
    val search by produceState<Result<List<FileEntry>>?>(null, dir, query) {
        value = null
        val q = query.trim()
        if (q.isEmpty()) return@produceState
        delay(400) // tunggu selesai mengetik
        value = withContext(Dispatchers.IO) { runCatching { Folders.search(context, dir, q) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("Folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = { TextButton(onClick = onChangeFolder) { Text("Ganti folder") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Cari nama file, mis. .bin") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val searching = query.isNotBlank()
            val result = if (searching) search else listing
            val entries = result?.getOrNull()
            when {
                result == null -> LoadingBox()
                entries == null -> MessageBox("Gagal membaca folder", result.exceptionOrNull()?.message.orEmpty())
                entries.isEmpty() -> MessageBox(
                    if (searching) "Tidak ditemukan" else "Folder kosong",
                    if (searching) "Tidak ada file yang namanya mengandung \"${query.trim()}\"." else "",
                )
                else -> {
                    if (searching) {
                        Text(
                            "${entries.size} file ditemukan (termasuk subfolder)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    val files = remember(entries) { entries.filter { !it.isDir } }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries, key = { it.uri.toString() }) { e ->
                            ListItem(
                                leadingContent = { Text(if (e.isDir) "📁" else "📄") },
                                headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(if (e.isDir) "Folder" else e.size?.let(::formatSize) ?: "")
                                },
                                modifier = Modifier.clickable {
                                    if (e.isDir) {
                                        e.docId?.let { onOpenDir(Dir(dir.tree, it, e.name)) }
                                    } else {
                                        onOpenFile(files, files.indexOf(e))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
