package com.fileviewer.files

/**
 * Aturan document ID untuk penyimpanan internal/SD (com.android.externalstorage.documents),
 * mis. "primary:Download/video.bin". Murni Kotlin agar bisa di-unit-test.
 */
object DocIds {
    /** "primary:Download/a.bin" → "primary:Download"; "primary:a.bin" → "primary:"; lainnya → null. */
    fun parentOf(docId: String): String? {
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val path = docId.substring(colon + 1)
        if (path.isEmpty()) return null
        return if ('/' in path) docId.substringBeforeLast('/') else docId.substring(0, colon + 1)
    }

    /** Apakah [docId] berada di dalam folder [treeId] (atau subfoldernya). */
    fun isInside(docId: String, treeId: String): Boolean = when {
        docId == treeId -> false
        treeId.endsWith(":") -> docId.startsWith(treeId)
        else -> docId.startsWith("$treeId/")
    }
}
