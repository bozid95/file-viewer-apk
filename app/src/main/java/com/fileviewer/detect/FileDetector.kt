package com.fileviewer.detect

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class Kind { IMAGE, VIDEO, AUDIO, PDF, TEXT, OTHER }

data class FileType(
    val kind: Kind,
    /** Nama format untuk ditampilkan, mis. "MP4". */
    val label: String,
    val mime: String,
    /** Ekstensi yang benar untuk format ini (tanpa titik). */
    val extension: String,
    /** false = format dikenali tetapi tidak bisa ditampilkan di aplikasi ini. */
    val viewable: Boolean = true,
    /** Hanya untuk [Kind.TEXT]. */
    val charset: Charset? = null,
)

/**
 * Menentukan jenis file dari ISI-nya (magic bytes), bukan dari ekstensi nama file.
 * Murni Kotlin/JVM, tanpa dependensi Android, sehingga bisa di-unit-test.
 */
object FileDetector {
    /** Jumlah byte awal file yang dibaca untuk deteksi. */
    const val HEADER_SIZE = 8 * 1024

    const val OCTET = "application/octet-stream"
    const val UNKNOWN_LABEL = "Tidak dikenali"

    fun detect(header: ByteArray, length: Int = header.size): FileType {
        val h = Header(header, minOf(length, header.size))
        if (h.len == 0) return FileType(Kind.OTHER, "File kosong", OCTET, "", viewable = false)
        return detectBinary(h)
            ?: detectText(h)
            // Frame sync MP3/AAC lemah (mis. BOM UTF-16 "FF FE" juga cocok), jadi dicek setelah teks.
            ?: detectAudioFrame(h)
            ?: FileType(Kind.OTHER, UNKNOWN_LABEL, OCTET, "bin", viewable = false)
    }

    private fun detectAudioFrame(h: Header): FileType? = when {
        h.len < 4 || h.u(0) != 0xFF -> null
        (h.u(1) and 0xF6) == 0xF0 -> audio("AAC", "audio/aac", "aac")
        (h.u(1) and 0xE0) == 0xE0 && (h.u(1) and 0x06) != 0 -> audio("MP3", "audio/mpeg", "mp3")
        else -> null
    }

    // ---------------------------------------------------------------- binary

    private fun detectBinary(h: Header): FileType? = when {
        // Gambar
        h.bytes(0, 0xFF, 0xD8, 0xFF) -> image("JPEG", "image/jpeg", "jpg")
        h.bytes(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> image("PNG", "image/png", "png")
        h.ascii(0, "GIF87a") || h.ascii(0, "GIF89a") -> image("GIF", "image/gif", "gif")
        h.ascii(0, "RIFF") -> riff(h)
        h.ascii(0, "BM") && h.bytes(6, 0, 0, 0, 0) -> image("BMP", "image/bmp", "bmp")

        // Keluarga MP4 / MOV / 3GP / M4A / HEIC / AVIF
        h.ascii(4, "ftyp") -> isoBmff(h)

        // Video
        h.bytes(0, 0x1A, 0x45, 0xDF, 0xA3) ->
            if (h.contains("webm", 128)) video("WebM", "video/webm", "webm")
            else video("MKV", "video/x-matroska", "mkv")
        h.ascii(0, "FLV") && h.u(3) == 0x01 -> video("FLV", "video/x-flv", "flv")
        h.bytes(0, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11) ->
            video("WMV", "video/x-ms-wmv", "wmv", viewable = false)
        h.bytes(0, 0x00, 0x00, 0x01, 0xBA) || h.bytes(0, 0x00, 0x00, 0x01, 0xB3) ->
            video("MPEG", "video/mpeg", "mpg")
        isMpegTs(h) -> video("MPEG-TS", "video/mp2t", "ts")

        // Audio
        h.ascii(0, "ID3") -> audio("MP3", "audio/mpeg", "mp3")
        h.ascii(0, "OggS") -> when {
            h.contains("theora", 128) -> video("OGV", "video/ogg", "ogv")
            h.contains("OpusHead", 128) -> audio("Opus", "audio/ogg", "opus")
            else -> audio("OGG", "audio/ogg", "ogg")
        }
        h.ascii(0, "fLaC") -> audio("FLAC", "audio/flac", "flac")
        h.ascii(0, "#!AMR") -> audio("AMR", "audio/amr", "amr")
        h.ascii(0, "MThd") -> audio("MIDI", "audio/midi", "mid", viewable = false)

        // Dokumen
        h.contains("%PDF-", 1024) -> FileType(Kind.PDF, "PDF", "application/pdf", "pdf")

        // Lainnya: dikenali, tapi dibuka dengan aplikasi lain
        h.bytes(0, 0x50, 0x4B, 0x03, 0x04) -> zip(h)
        h.ascii(0, "Rar!") -> other("RAR", "application/vnd.rar", "rar")
        h.bytes(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> other("7-Zip", "application/x-7z-compressed", "7z")
        h.bytes(0, 0x1F, 0x8B) -> other("GZIP", "application/gzip", "gz")
        h.ascii(0, "SQLite format 3") -> other("SQLite", "application/vnd.sqlite3", "db")
        isWindowsExe(h) -> other("EXE (Windows)", "application/vnd.microsoft.portable-executable", "exe")
        else -> null
    }

    private fun riff(h: Header): FileType? = when (h.str(8, 4)) {
        "WEBP" -> image("WebP", "image/webp", "webp")
        "WAVE" -> audio("WAV", "audio/wav", "wav")
        "AVI " -> video("AVI", "video/x-msvideo", "avi")
        else -> null
    }

    private fun isoBmff(h: Header): FileType {
        val major = h.str(8, 4)
        val boxSize = (h.u(0) shl 24) or (h.u(1) shl 16) or (h.u(2) shl 8) or h.u(3)
        val brands = buildList {
            add(major)
            var i = 16
            val end = minOf(boxSize, h.len)
            while (i + 4 <= end) {
                add(h.str(i, 4)); i += 4
            }
        }
        fun has(vararg b: String) = brands.any { it in b }
        return when {
            major == "avif" || major == "avis" -> image("AVIF", "image/avif", "avif")
            major in HEIC_BRANDS -> image("HEIC", "image/heic", "heic")
            major == "M4A " || major == "M4B " || major == "M4P " -> audio("M4A", "audio/mp4", "m4a")
            major == "qt  " -> video("MOV", "video/quicktime", "mov")
            major.startsWith("3g2") -> video("3G2", "video/3gpp2", "3g2")
            major.startsWith("3gp") -> video("3GP", "video/3gpp", "3gp")
            has("avif") -> image("AVIF", "image/avif", "avif")
            has("heic", "heix") -> image("HEIC", "image/heic", "heic")
            else -> video("MP4", "video/mp4", "mp4")
        }
    }

    private fun zip(h: Header): FileType = when {
        h.contains("mimetypeapplication/epub+zip", 128) -> other("EPUB", "application/epub+zip", "epub")
        h.contains("AndroidManifest.xml") || h.contains("classes.dex") ->
            other("APK", "application/vnd.android.package-archive", "apk")
        h.contains("word/") ->
            other("Word (DOCX)", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
        h.contains("xl/") ->
            other("Excel (XLSX)", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        h.contains("ppt/") ->
            other("PowerPoint (PPTX)", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
        else -> other("ZIP", "application/zip", "zip")
    }

    /** MPEG-TS: byte sinkron 0x47 berulang setiap 188 byte. */
    private fun isMpegTs(h: Header): Boolean {
        if (h.len < 188 * 3) return false
        var i = 0
        while (i < h.len && i <= 188 * 4) {
            if (h.u(i) != 0x47) return false
            i += 188
        }
        return true
    }

    private fun isWindowsExe(h: Header): Boolean {
        if (!h.ascii(0, "MZ") || h.len < 64) return false
        val pe = h.u(60) or (h.u(61) shl 8) or (h.u(62) shl 16) or (h.u(63) shl 24)
        return pe in 64 until h.len - 4 && h.bytes(pe, 0x50, 0x45, 0x00, 0x00)
    }

    // ------------------------------------------------------------------ text

    private val ALLOWED_CONTROL = setOf(0x09, 0x0A, 0x0C, 0x0D, 0x1B)

    private fun detectText(h: Header): FileType? {
        // BOM UTF-16 → pasti teks
        if (h.bytes(0, 0xFF, 0xFE)) return text(h, Charsets.UTF_16LE, 2)
        if (h.bytes(0, 0xFE, 0xFF)) return text(h, Charsets.UTF_16BE, 2)
        val start = if (h.bytes(0, 0xEF, 0xBB, 0xBF)) 3 else 0

        var control = 0
        for (i in start until h.len) {
            val c = h.u(i)
            if (c == 0x00) return null                       // byte NUL → biner
            if ((c < 0x20 && c !in ALLOWED_CONTROL) || c == 0x7F) control++
        }
        val total = h.len - start
        if (total > 0 && control * 100 > total) return null  // > 1% karakter kontrol → biner

        val charset = if (isValidUtf8(h.b, start, h.len)) Charsets.UTF_8 else Charset.forName("windows-1252")
        return text(h, charset, start)
    }

    private fun text(h: Header, charset: Charset, start: Int): FileType {
        val prefix = String(h.b, start, minOf(h.len - start, 512), charset).trimStart().lowercase()
        return when {
            prefix.startsWith("{") || (prefix.startsWith("[") && prefix.getOrNull(1)?.isLetter() != true) ->
                FileType(Kind.TEXT, "JSON", "application/json", "json", charset = charset)
            prefix.startsWith("<?xml") ->
                if (prefix.contains("<svg")) FileType(Kind.TEXT, "SVG (XML)", "image/svg+xml", "svg", charset = charset)
                else FileType(Kind.TEXT, "XML", "text/xml", "xml", charset = charset)
            prefix.startsWith("<!doctype html") || prefix.startsWith("<html") ->
                FileType(Kind.TEXT, "HTML", "text/html", "html", charset = charset)
            prefix.startsWith("<svg") -> FileType(Kind.TEXT, "SVG (XML)", "image/svg+xml", "svg", charset = charset)
            else -> FileType(Kind.TEXT, "Teks", "text/plain", "txt", charset = charset)
        }
    }

    private fun isValidUtf8(b: ByteArray, start: Int, end: Int): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // endOfInput=false: karakter yang terpotong di akhir header tidak dianggap error.
        val result = decoder.decode(ByteBuffer.wrap(b, start, end - start), CharBuffer.allocate(end - start + 1), false)
        return !result.isError
    }

    // --------------------------------------------------------------- helpers

    private val HEIC_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1")

    private fun image(l: String, m: String, e: String) = FileType(Kind.IMAGE, l, m, e)
    private fun video(l: String, m: String, e: String, viewable: Boolean = true) = FileType(Kind.VIDEO, l, m, e, viewable)
    private fun audio(l: String, m: String, e: String, viewable: Boolean = true) = FileType(Kind.AUDIO, l, m, e, viewable)
    private fun other(l: String, m: String, e: String) = FileType(Kind.OTHER, l, m, e, viewable = false)

    private class Header(val b: ByteArray, val len: Int) {
        fun u(i: Int): Int = if (i in 0 until len) b[i].toInt() and 0xFF else -1
        fun bytes(off: Int, vararg v: Int): Boolean = v.indices.all { u(off + it) == v[it] }
        fun ascii(off: Int, s: String): Boolean = s.indices.all { u(off + it) == s[it].code }
        fun str(off: Int, n: Int): String = buildString {
            for (i in off until off + n) {
                val c = u(i); if (c < 0) break; append(c.toChar())
            }
        }
        fun contains(s: String, limit: Int = len): Boolean {
            val last = minOf(limit, len) - s.length
            for (i in 0..last) if (ascii(i, s)) return true
            return false
        }
    }
}

/** Tampilan hex klasik: offset, 16 byte hex, lalu ASCII. */
fun hexDump(bytes: ByteArray, length: Int): String = buildString {
    val len = minOf(length, bytes.size)
    for (row in 0 until len step 16) {
        append("%08X  ".format(row))
        for (i in 0 until 16) {
            if (row + i < len) append("%02X ".format(bytes[row + i])) else append("   ")
            if (i == 7) append(' ')
        }
        append(" |")
        for (i in row until minOf(row + 16, len)) {
            val c = bytes[i].toInt() and 0xFF
            append(if (c in 0x20..0x7E) c.toChar() else '.')
        }
        append("|\n")
    }
}
