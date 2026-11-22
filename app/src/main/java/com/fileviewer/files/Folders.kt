package com.fileviewer.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns

/** Satu file atau folder. [docId] hanya ada untuk entri dari folder yang diizinkan (SAF tree). */
data class FileEntry(
    val uri: Uri,
    val name: String,
    val size: Long?,
    val isDir: Boolean = false,
    val docId: String? = null,
)

/** Folder di dalam tree yang sudah diberi izin oleh pengguna. */
data class Dir(val tree: Uri, val docId: String, val name: String)

/**
 * Akses folder lewat Storage Access Framework: pengguna memilih folder sekali,
 * izinnya disimpan permanen, sehingga tidak butuh izin "akses semua file".
 */
object Folders {
    private const val PREFS = "folders"

    fun takePermission(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun rootOf(context: Context, tree: Uri): Dir {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val name = queryNameSize(context, DocumentsContract.buildDocumentUriUsingTree(tree, docId)).first
        return Dir(tree, docId, name ?: docId.substringAfterLast('/').substringAfterLast(':').ifEmpty { docId })
    }

    /** Folder terakhir yang dibuka (dipakai lagi saat "Buka Folder" & untuk mencari file tetangga). */
    fun last(context: Context): Dir? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tree = p.getString("tree", null)?.let(Uri::parse) ?: return null
        val doc = p.getString("doc", null) ?: return null
        if (grantedTrees(context).none { it == tree }) return null
        return Dir(tree, doc, p.getString("name", null) ?: doc)
    }

    fun saveLast(context: Context, dir: Dir) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tree", dir.tree.toString())
            .putString("doc", dir.docId)
            .putString("name", dir.name)
            .apply()
    }

    /** Isi satu folder: folder dulu, lalu file, urut nama. */
    fun list(context: Context, dir: Dir): List<FileEntry> =
        children(context, dir.tree, dir.docId).sortedWith(ORDER)

    /** Cari file yang namanya mengandung [query], termasuk di subfolder. */
    fun search(context: Context, dir: Dir, query: String, limit: Int = 2000): List<FileEntry> {
        val results = ArrayList<FileEntry>()
        val queue = ArrayDeque(listOf(dir.docId))
        var visited = 0
        while (queue.isNotEmpty() && results.size < limit && visited < 3000) {
            val id = queue.removeFirst()
            visited++
            for (e in runCatching { children(context, dir.tree, id) }.getOrDefault(emptyList())) {
                if (e.isDir) e.docId?.let(queue::addLast)
                else if (e.name.contains(query, ignoreCase = true)) results += e
            }
        }
        return results.sortedWith(ORDER)
    }

    /**
     * Untuk file yang dibuka dari luar (file manager / "Pilih File"): cari file-file lain
     * di folder yang sama, agar bisa di-swipe. Hanya berhasil jika foldernya sudah diizinkan.
     */
    fun siblingsOf(context: Context, uri: Uri): Pair<List<FileEntry>, Int>? {
        val trees = grantedTrees(context)

        // 1) URI dokumen yang berada di dalam folder yang diizinkan
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val parent = DocIds.parentOf(docId)
            if (parent != null) {
                for (tree in trees) {
                    if (tree.authority != uri.authority) continue
                    val treeId = DocumentsContract.getTreeDocumentId(tree)
                    if (!DocIds.isInside(docId, treeId)) continue
                    if (parent != treeId && !DocIds.isInside(parent, treeId)) continue
                    val files = runCatching { list(context, Dir(tree, parent, "")) }.getOrNull()
                        ?.filter { !it.isDir } ?: continue
                    val i = files.indexOfFirst { it.docId == docId }
                    if (i >= 0) return files to i
                }
            }
        }

        // 2) File manager lain memakai URI miliknya sendiri → cocokkan nama (+ukuran) di folder terakhir
        val last = last(context) ?: return null
        val (name, size) = queryNameSize(context, uri)
        if (name == null) return null
        val files = runCatching { list(context, last) }.getOrNull()?.filter { !it.isDir } ?: return null
        val i = files.indexOfFirst { it.name == name && (size == null || it.size == null || it.size == size) }
        return if (i >= 0) files to i else null
    }

    fun queryNameSize(context: Context, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
        }
        return name to size
    }

    private fun grantedTrees(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }

    private fun children(context: Context, tree: Uri, docId: String): List<FileEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val out = ArrayList<FileEntry>()
        context.contentResolver.query(childrenUri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: id
                if (name.startsWith(".")) continue // file tersembunyi
                out += FileEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = name,
                    size = if (c.isNull(3)) null else c.getLong(3),
                    isDir = c.getString(2) == Document.MIME_TYPE_DIR,
                    docId = id,
                )
            }
        }
        return out
    }

    private val ORDER = compareBy<FileEntry>({ !it.isDir }, { it.name.lowercase() })
}
