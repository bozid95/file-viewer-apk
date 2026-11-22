package com.fileviewer.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FileDetectorTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun pad(b: ByteArray, size: Int = 64) = b.copyOf(maxOf(size, b.size))

    /** Kotak ftyp ISO-BMFF dengan major brand + compatible brands. */
    private fun ftyp(major: String, vararg compat: String): ByteArray {
        val size = 16 + compat.size * 4
        return pad(bytes(0, 0, 0, size) + ascii("ftyp") + ascii(major) + bytes(0, 0, 0, 0) +
            compat.fold(ByteArray(0)) { acc, s -> acc + ascii(s) })
    }

    private fun detect(b: ByteArray) = FileDetector.detect(b)

    private fun assertType(b: ByteArray, kind: Kind, label: String, ext: String) {
        val t = detect(b)
        assertEquals("kind", kind, t.kind)
        assertEquals("label", label, t.label)
        assertEquals("extension", ext, t.extension)
    }

    // ---- gambar
    @Test fun jpeg() = assertType(pad(bytes(0xFF, 0xD8, 0xFF, 0xE0) + ascii("JFIF")), Kind.IMAGE, "JPEG", "jpg")
    @Test fun png() = assertType(pad(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)), Kind.IMAGE, "PNG", "png")
    @Test fun gif() = assertType(pad(ascii("GIF89a")), Kind.IMAGE, "GIF", "gif")
    @Test fun webp() = assertType(pad(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WEBPVP8 ")), Kind.IMAGE, "WebP", "webp")
    @Test fun bmp() = assertType(pad(ascii("BM") + bytes(0x36, 0, 0, 0, 0, 0, 0, 0)), Kind.IMAGE, "BMP", "bmp")
    @Test fun heic() = assertType(ftyp("heic", "mif1", "heic"), Kind.IMAGE, "HEIC", "heic")
    @Test fun heicViaMif1() = assertType(ftyp("mif1", "heic"), Kind.IMAGE, "HEIC", "heic")
    @Test fun avif() = assertType(ftyp("avif", "mif1"), Kind.IMAGE, "AVIF", "avif")

    // ---- video
    @Test fun mp4() = assertType(ftyp("isom", "isom", "iso2", "avc1", "mp41"), Kind.VIDEO, "MP4", "mp4")
    @Test fun mp4Mp42() = assertType(ftyp("mp42", "mp42", "isom"), Kind.VIDEO, "MP4", "mp4")
    @Test fun mov() = assertType(ftyp("qt  ", "qt  "), Kind.VIDEO, "MOV", "mov")
    @Test fun threeGp() = assertType(ftyp("3gp4", "isom", "3gp4"), Kind.VIDEO, "3GP", "3gp")
    @Test fun mkv() = assertType(pad(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x82, 0x88) + ascii("matroska")), Kind.VIDEO, "MKV", "mkv")
    @Test fun webm() = assertType(pad(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x82, 0x84) + ascii("webm")), Kind.VIDEO, "WebM", "webm")
    @Test fun avi() = assertType(pad(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("AVI LIST")), Kind.VIDEO, "AVI", "avi")
    @Test fun flv() = assertType(pad(ascii("FLV") + bytes(1, 5)), Kind.VIDEO, "FLV", "flv")
    @Test fun mpegTs() {
        val b = ByteArray(188 * 5)
        for (i in 0 until 5) b[i * 188] = 0x47
        assertType(b, Kind.VIDEO, "MPEG-TS", "ts")
    }
    @Test fun wmvKnownButNotViewable() {
        val t = detect(pad(bytes(0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11)))
        assertEquals("WMV", t.label)
        assertFalse(t.viewable)
    }

    // ---- audio
    @Test fun mp3Id3() = assertType(pad(ascii("ID3") + bytes(3, 0)), Kind.AUDIO, "MP3", "mp3")
    @Test fun mp3Frame() = assertType(pad(bytes(0xFF, 0xFB, 0x90, 0x64)), Kind.AUDIO, "MP3", "mp3")
    @Test fun aacAdts() = assertType(pad(bytes(0xFF, 0xF1, 0x50, 0x80)), Kind.AUDIO, "AAC", "aac")
    @Test fun m4a() = assertType(ftyp("M4A ", "M4A ", "mp42", "isom"), Kind.AUDIO, "M4A", "m4a")
    @Test fun wav() = assertType(pad(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WAVEfmt ")), Kind.AUDIO, "WAV", "wav")
    @Test fun flac() = assertType(pad(ascii("fLaC")), Kind.AUDIO, "FLAC", "flac")
    @Test fun oggVorbis() = assertType(pad(ascii("OggS") + ByteArray(24) + bytes(1) + ascii("vorbis")), Kind.AUDIO, "OGG", "ogg")
    @Test fun opus() = assertType(pad(ascii("OggS") + ByteArray(24) + ascii("OpusHead")), Kind.AUDIO, "Opus", "opus")

    // ---- dokumen & lainnya
    @Test fun pdf() = assertType(pad(ascii("%PDF-1.7\n%âãÏÓ\n")), Kind.PDF, "PDF", "pdf")
    @Test fun zip() = assertType(pad(bytes(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + ascii("foto/a.jpg")), Kind.OTHER, "ZIP", "zip")
    @Test fun docx() = assertType(pad(bytes(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + ascii("word/document.xml")), Kind.OTHER, "Word (DOCX)", "docx")
    @Test fun apk() = assertType(pad(bytes(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + ascii("AndroidManifest.xml")), Kind.OTHER, "APK", "apk")
    @Test fun rar() = assertType(pad(ascii("Rar!") + bytes(0x1A, 0x07, 0x01, 0x00)), Kind.OTHER, "RAR", "rar")

    // ---- teks
    @Test fun plainText() {
        val t = detect("Halo dunia\nIni baris kedua\n".toByteArray())
        assertEquals(Kind.TEXT, t.kind)
        assertEquals(Charsets.UTF_8, t.charset)
    }
    @Test fun utf8WithMultibyte() {
        val t = detect("Café — naïve 日本語 ✓\n".toByteArray(Charsets.UTF_8))
        assertEquals(Kind.TEXT, t.kind)
        assertEquals(Charsets.UTF_8, t.charset)
    }
    @Test fun utf8CutInsideCharacter() {
        val full = "abc 日本語".toByteArray(Charsets.UTF_8)
        val t = FileDetector.detect(full, full.size - 1) // karakter terakhir terpotong
        assertEquals(Kind.TEXT, t.kind)
        assertEquals(Charsets.UTF_8, t.charset)
    }
    @Test fun latin1Text() {
        val t = detect("café crème\n".toByteArray(Charsets.ISO_8859_1))
        assertEquals(Kind.TEXT, t.kind)
        assertEquals("windows-1252", t.charset!!.name())
    }
    @Test fun utf16Bom() {
        val t = detect(bytes(0xFF, 0xFE) + "hello".toByteArray(Charsets.UTF_16LE))
        assertEquals(Kind.TEXT, t.kind)
        assertEquals(Charsets.UTF_16LE, t.charset)
    }
    @Test fun json() = assertType("  {\"a\": 1}".toByteArray(), Kind.TEXT, "JSON", "json")
    @Test fun jsonArray() = assertType("[1, 2, 3]".toByteArray(), Kind.TEXT, "JSON", "json")
    @Test fun iniIsNotJson() = assertType("[section]\nkey=value\n".toByteArray(), Kind.TEXT, "Teks", "txt")
    @Test fun xml() = assertType("<?xml version=\"1.0\"?><root/>".toByteArray(), Kind.TEXT, "XML", "xml")
    @Test fun html() = assertType("<!DOCTYPE html><html></html>".toByteArray(), Kind.TEXT, "HTML", "html")

    // ---- tidak dikenali
    @Test fun randomBinaryIsUnknown() {
        val t = detect(Random(42).nextBytes(4096).also { it[0] = 0x12; it[1] = 0x34 })
        assertEquals(Kind.OTHER, t.kind)
        assertEquals(FileDetector.UNKNOWN_LABEL, t.label)
        assertFalse(t.viewable)
    }
    @Test fun emptyFile() {
        val t = FileDetector.detect(ByteArray(0))
        assertEquals(Kind.OTHER, t.kind)
        assertFalse(t.viewable)
    }
    @Test fun shortHeaderDoesNotCrash() {
        for (b in 0..255) FileDetector.detect(bytes(b))
        FileDetector.detect(bytes(0xFF))
        FileDetector.detect(ascii("RIFF"))
        FileDetector.detect(ascii("\u0000\u0000\u0000 ftyp"))
    }

    // ---- hex dump
    @Test fun hexDumpFormat() {
        val line = hexDump(ascii("ABC"), 3).lines().first()
        assertTrue(line, line.startsWith("00000000  41 42 43"))
        assertTrue(line, line.endsWith("|ABC|"))
    }
}
