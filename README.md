# File Viewer

Aplikasi Android (Kotlin + Jetpack Compose) untuk membuka file **apa pun** dengan mendeteksi
jenisnya dari **isi file (magic bytes)**, bukan dari ekstensi. File yang di-rename menjadi
`.bin` atau ekstensi lain tetap bisa dibuka.

## Format

| Jenis  | Format                                              | Viewer            |
|--------|-----------------------------------------------------|-------------------|
| Gambar | JPEG, PNG, GIF (animasi), WebP, BMP, HEIC, AVIF     | bawaan Android    |
| Video  | MP4, MOV, 3GP, MKV, WebM, AVI, FLV, MPEG, MPEG-TS   | ExoPlayer (Media3)|
| Audio  | MP3, AAC, M4A, WAV, FLAC, OGG, Opus, AMR            | ExoPlayer (Media3)|
| PDF    | PDF                                                 | PdfRenderer       |
| Teks   | TXT, JSON, XML, HTML, log (UTF-8/UTF-16/ANSI)       | teks monospace    |

Format yang dikenali tapi tidak ditampilkan (ZIP, RAR, 7z, DOCX, XLSX, PPTX, APK, EPUB, WMV, ...)
diteruskan ke aplikasi lain lewat tombol **"Buka dengan"** dengan MIME type yang benar.
File yang sama sekali tidak dikenali ditampilkan sebagai hex dump.

## Cara pakai

- Buka aplikasi lalu **Pilih File**, atau
- Dari file manager: tekan file, pilih **Buka dengan**, lalu **File Viewer**.

## Build

Otomatis lewat GitHub Actions di setiap push ke `main`:
tab **Actions**, pilih run terakhir, lalu unduh artifact **file-viewer-apk**.

Untuk membuat rilis yang bisa diunduh langsung dari HP:

```bash
git tag v1.0
git push origin v1.0
```

APK lalu muncul di halaman **Releases**.

Build lokal (butuh Android SDK / Android Studio):

```bash
./gradlew testDebugUnitTest   # unit test deteksi format
./gradlew assembleRelease     # APK: app/build/outputs/apk/release/
```

> APK ditandatangani dengan debug key milik mesin yang mem-build. Kalau Android menolak saat
> update ("konflik paket"), uninstall versi lama terlebih dahulu.
