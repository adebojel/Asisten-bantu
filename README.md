<div align="center">

<img src="docs/kobra-hero-3d.svg" width="100%" alt="Artwork konsep 3D Kobra OS, bukan tangkapan layar aplikasi">

# KOBRA OS
### Asisten Suara & Mata Kedua untuk Android

**Akses ponsel dengan suara. Baca informasi di sekitar. Tetap gunakan fungsi inti saat offline.**

[**Unduh APK**](https://github.com/adebojel/Asisten-bantu/releases) · [Build APK](https://github.com/adebojel/Asisten-bantu/actions) · [Source Android](https://github.com/adebojel/Asisten-bantu/tree/main) · [Laporkan masalah](https://github.com/adebojel/Asisten-bantu/issues)

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.x-7F52FF?logo=kotlin&logoColor=white)
![Development](https://img.shields.io/badge/Status-Active%20Development-00B8D9)

</div>

---

## Tentang Kobra OS

Kobra OS adalah aplikasi Android aksesibilitas yang dikembangkan untuk membantu pengguna, terutama tunanetra dan pengguna dengan keterbatasan penglihatan, mengoperasikan ponsel melalui suara dan mendapatkan informasi dari kamera maupun layar.

Arah pengembangannya adalah **satu asisten hybrid**: fungsi ringan berjalan lokal, sedangkan permintaan yang membutuhkan penalaran lebih luas dapat diteruskan ke AI online jika jaringan dan provider tersedia.

> **Status pengujian:** build sebelumnya berhasil di GitHub Actions. Integrasi deteksi objek offline dengan fallback Image Labeling sudah digabung ke branch `main`. GitHub Actions pada commit pengembangan sebelum merge berhasil menjalankan tes logika dan build APK. Belum ada verifikasi menyeluruh pada perangkat fisik. Build berhasil tidak berarti aplikasi bebas crash atau seluruh fitur telah bekerja.

## Tampilan aplikasi

Artwork 3D di bagian atas adalah ilustrasi identitas produk. Screenshot aplikasi asli yang dikirim digunakan sebagai acuan perbaikan UI; screenshot belum ditambahkan sebagai aset repository. Artwork banner 3D tambahan sudah dibuat untuk pembaruan identitas visual, tetapi belum tersedia sebagai file repository yang dapat ditautkan secara permanen. Karena itu README hanya menampilkan artwork SVG yang memang sudah tersimpan di repository, bukan tautan gambar yang belum ada.

| Beranda Kobra OS | Pilihan fitur Mata Kedua |
|---|---|
| Header status, indikator baterai/RAM/jaringan, orb suara, dan kartu modul | Pilihan pembaca uang, OCR, barcode, objek, terjemahan, dan KTP |

**Arah desain UI yang sedang dikerjakan**
- Tampilan gelap modern dengan aksen cyan dan indikator status yang jelas.
- Header ringkas; status baterai, RAM, jaringan, dan aksesibilitas tidak saling bertumpuk.
- Kartu fitur lebih padat dan konsisten agar ruang layar utama lebih luas.
- Dialog dan menu lebih ringkas, mudah dibaca, serta tetap nyaman disentuh.
- Kontras teks dan label tombol harus tetap tinggi. Minimalis tidak boleh mengurangi aksesibilitas.

## Kemampuan utama

| Modul | Fungsi |
|---|---|
| Kontrol suara | Membuka aplikasi, navigasi layar, dan menjalankan perintah yang diizinkan Android |
| Aksesibilitas | Membaca teks layar dan berinteraksi dengan elemen antarmuka melalui Accessibility Service |
| Mata Kedua | OCR, warna pakaian, wajah, barcode/QR, dan pengenalan objek |
| Mode Tongkat | Panduan arah objek berbasis kamera, suara, dan getaran; bukan pengganti tongkat putih |
| Komunikasi | Alur panggilan, SMS, notifikasi, dan aplikasi pesan dengan izin serta konfirmasi |
| Otak offline | Perintah dasar, kalkulator, dan basis pengetahuan lokal |
| AI online | Router provider ketika internet dan konfigurasi tersedia |
| AI lokal | Runtime MediaPipe dan LiteRT-LM untuk model yang kompatibel |

Ketersediaan fitur bergantung pada versi Android, izin, perangkat, koneksi, provider, dan model yang terpasang. Chat, input suara dari chat, dan perintah global dirancang masuk ke jalur percakapan yang sama; riwayat chat kini disimpan lokal dan dipulihkan saat layar chat dibuka kembali.

## AI hybrid: online dan offline

Paket APK sudah menyertakan **otak offline dasar Kobra** (mesin aturan, kalkulator, dan basis pengetahuan `kobra_kb.txt`) sehingga fungsi bantuan dasar tidak memerlukan unduhan model tambahan. Ini bukan LLM generatif penuh. Untuk percakapan generatif lokal, urutan Chat adalah Gemma 3 1B jika tersedia, lalu Qwen3 0.6B LiteRT-LM jika sudah diimpor dan lolos pemeriksaan RAM, kemudian AI online jika internet tersedia. Jika semua model gagal/tidak tersedia, Kobra tetap menjalankan perintah lokal, kalkulator, memori, agenda, dan pengetahuan dasar. Model LLM besar tetap dipisah dari APK agar ukuran instalasi dan kebutuhan RAM tidak membengkak; Qwen3 sekitar 300–400 MB. Pengenalan suara offline tetap bergantung pada dukungan dan paket bahasa offline layanan SpeechRecognizer Android.

| Jalur | Format | Runtime |
|---|---|---|
| Gemma on-device | `.task` | MediaPipe LLM Inference |
| Qwen LiteRT-LM | `.litertlm` | LiteRT-LM |

Paket model terpisah dari APK:

[**Buka Kobra Offline Models v1.0**](https://github.com/adebojel/Asisten-bantu/releases/tag/kobra-offline-models-v1.0)

Model tidak otomatis cocok untuk setiap ponsel. Kobra menggunakan pemeriksaan memori dan jalur fallback; jika model gagal dimuat, fungsi dasar tetap harus dapat digunakan.

## Unduh dan instalasi

- **APK rilis:** [GitHub Releases](https://github.com/adebojel/Asisten-bantu/releases)
- **APK debug terbaru:** [Build main #134 — unduh artifact Kobra-OS-APK](https://github.com/adebojel/Asisten-bantu/actions/runs/43637435906) → bagian Artifacts → `Kobra-OS-APK` → unzip → pasang `app-debug.apk`.
- **Model offline Qwen3 0.6B:** [Kobra Offline Models v1.0](https://github.com/adebojel/Asisten-bantu/releases/tag/kobra-offline-models-v1.0). Unduh berkas `.litertlm` dan file `.sha256`, verifikasi checksum, lalu buka Kobra → Pengaturan → AI & Model → Impor Model. Pilih berkas `.litertlm`, tunggu penyalinan selesai, kemudian tekan Muat / Uji. Pastikan ruang penyimpanan cukup (model sekitar 300–400 MB) dan RAM tersedia memadai.

APK debug #134 sudah tersedia untuk pengujian perangkat. Saat ini belum ada rilis APK stabil yang dinyatakan siap untuk penggunaan rutin. APK debug ditujukan untuk pengujian. Workflow prerelease APK sudah tersedia di [Kobra OS APK Release](.github/workflows/release-apk.yml); rilis baru dianggap tersedia setelah workflow dijalankan dan aset APK muncul di halaman Releases.

## Status pengembangan

- [x] Fondasi perintah offline dan router AI online.
- [x] Pemisahan jalur runtime model `.task` dan `.litertlm`.
- [x] Paket model offline tersedia terpisah.
- [x] Pemeriksaan RAM sebelum pemuatan model lokal.
- [x] Chat mencoba Gemma lokal, lalu Qwen3 LiteRT-LM lokal sebelum AI online.
- [x] Callback runtime Qwen dikembalikan ke main thread untuk menjaga keamanan UI Android.
- [x] Riwayat chat terpadu (suara/teks dan respons lokal/online) disimpan lokal dan dipulihkan saat chat dibuka kembali.
- [x] Fallback perintah dan pengetahuan dasar tetap tersedia ketika model generatif tidak bisa dimuat.
- [x] Fallback pengenalan objek ke Image Labeling ditambahkan pada branch pengembangan.
- [x] Build Android `main` dan tes unit setelah integrasi fallback (GitHub Actions #134 berhasil; artifact APK tersedia).
- [ ] Uji instalasi dan fungsi di HP Android nyata (belum dilakukan; ikuti [UJI_DI_HP.md](UJI_DI_HP.md)).
- [ ] Uji instalasi dan fungsi di HP Android nyata.
- [ ] Uji mikrofon pada layar terkunci dan penggunaan latar belakang.
- [ ] Uji alur tunanetra end-to-end dengan TalkBack.
- [ ] Penyempurnaan visual seluruh layar dan dialog.
- [ ] Screenshot autentik dari build terbaru.

Panduan pengujian: [UJI_DI_HP.md](UJI_DI_HP.md).

## Arsitektur singkat

<img src="docs/kobra-overview.svg" width="100%" alt="Diagram arsitektur Kobra OS">

```mermaid
flowchart TD
    A["Suara / input pengguna"] --> B["Kobra Command Router"]
    B --> C["Android & Accessibility"]
    B --> D["Offline Brain"]
    B --> E["AI Online Router"]
    B --> F["AI lokal"]
    F --> G["MediaPipe .task"]
    F --> H["LiteRT-LM .litertlm"]
    C --> I["Respons suara"]
    D --> I
    E --> I
    G --> I
    H --> I
```

## Build dari source

Branch utama: `main`. Perubahan pengembangan dari `kobra-os-v7` telah digabung melalui PR #1.

```bash
git clone --branch main https://github.com/adebojel/Asisten-bantu.git
cd Asisten-bantu
./gradlew testDebugUnitTest assembleDebug
```

APK debug, jika build berhasil, berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Privasi dan batasan Android

- API key provider AI harus disimpan sebagai rahasia dan tidak boleh di-commit.
- Provider gratis dapat memiliki kuota, batas kecepatan, atau berubah sewaktu-waktu.
- Aplikasi biasa tidak dapat bebas mengaktifkan atau menonaktifkan Wi-Fi/data seluler.
- TalkBack tetap dikendalikan oleh pengguna melalui pengaturan Android.
- Pembacaan notifikasi dan SMS dapat membacakan informasi pribadi; aktifkan hanya jika diperlukan.
- Panggilan, pesan, dan SOS harus melalui konfirmasi pengguna.
- Kobra OS adalah alat bantu eksperimental, bukan pengganti tongkat putih, pendamping, atau layanan darurat.

## Kontribusi dan laporan bug

Gunakan [Issues](https://github.com/adebojel/Asisten-bantu/issues) untuk melaporkan masalah. Sertakan model HP, versi Android, langkah reproduksi, perilaku yang diharapkan, dan hasil aktual. Samarkan nomor telepon, lokasi, pesan, serta data pribadi pada screenshot atau log.

<div align="center">

**KOBRA OS — satu asisten, suara sebagai kendali.**

</div>
