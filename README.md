<div align="center">

<img src="docs/kobra-hero-3d.svg" width="100%" alt="Ilustrasi 3D konsep Kobra OS dengan ponsel, asisten suara, dan AI hybrid">

# 🐍 KOBRA OS
### **Asisten Suara • Mata Kedua • Aksesibilitas Android**

**Satu asisten untuk membantu memahami layar, berinteraksi melalui suara, dan mengakses fitur AI lokal sepenuhnya di perangkat.**

[![Unduh APK](https://img.shields.io/badge/⬇_UNDUH_APK-BUILD_TERBARU-06b6d4?style=for-the-badge)](https://github.com/adebojel/Asisten-bantu/releases/latest/download/Kobra-OS-main.apk)
[![Rilis](https://img.shields.io/badge/RELEASES-LIHAT_SEMUA-2563eb?style=for-the-badge)](https://github.com/adebojel/Asisten-bantu/releases)
[![Build](https://img.shields.io/badge/CI-GITHUB_ACTIONS-7c3aed?style=for-the-badge)](https://github.com/adebojel/Asisten-bantu/actions/workflows/release.yml)

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.x-7F52FF?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Branch](https://img.shields.io/badge/Branch-main-111827?logo=git)
![Status](https://img.shields.io/badge/Development-Active-06b6d4)

</div>

---

## ✨ Selamat datang di Kobra OS

Kobra OS adalah aplikasi Android aksesibilitas yang dirancang untuk membantu pengguna—terutama tunanetra dan pengguna dengan keterbatasan penglihatan—mengoperasikan ponsel melalui suara serta memperoleh informasi dari layar dan kamera.

Kobra menggabungkan beberapa jalur dalam satu pengalaman:

- **Voice-first:** kendali dan navigasi melalui perintah suara.
- **Mata Kedua:** OCR, pengenalan label objek, barcode, dan analisis visual sesuai kemampuan modul.
- **AI offline-only:** pemrosesan percakapan menggunakan model dan basis pengetahuan lokal; tidak ada fallback AI cloud.
- **Aksesibilitas Android:** integrasi Accessibility Service dan interoperabilitas dengan TalkBack.

> **Prinsip produk:** jelas tentang apa yang benar-benar terdeteksi, apa yang belum didukung, dan kapan pengguna perlu memeriksa hasil secara langsung. Kobra tidak boleh mengarang status perangkat, objek, saldo, atau keselamatan jalur.

## 🚀 Unduh dan mulai

<div align="center">

### KOBRA OS — BUILD 314

**APK debug • Branch `main` • 520.125.358 byte**

[![Download Build 314](https://img.shields.io/badge/UNDUH_KOBRA--OS--main.apk-00A6C7?style=for-the-badge&logo=android&logoColor=white)](https://github.com/adebojel/Asisten-bantu/releases/download/kobra-os-build-314/Kobra-OS-main.apk)

[Release Build 314](https://github.com/adebojel/Asisten-bantu/releases/tag/kobra-os-build-314) · [Checksum SHA-256](https://github.com/adebojel/Asisten-bantu/releases/download/kobra-os-build-314/SHA256SUMS.txt) · [Unduhan Latest](https://github.com/adebojel/Asisten-bantu/releases/latest)

</div>

### Instalasi di HP Android

1. Unduh APK melalui tombol di atas atau halaman [Releases Latest](https://github.com/adebojel/Asisten-bantu/releases/latest).
2. Buka file APK dari folder Unduhan.
3. Jika Android meminta izin, izinkan instalasi aplikasi dari sumber yang digunakan.
4. Selesaikan instalasi, lalu buka Kobra OS.
5. Berikan izin hanya untuk fitur yang ingin digunakan. Aktifkan layanan Kobra melalui **Pengaturan Android → Aksesibilitas**.
6. Ikuti [panduan uji di HP](UJI_DI_HP.md) secara bertahap.

**Tidak perlu Android Studio.** APK siap pasang disediakan melalui GitHub Releases.

### Status build dan pengujian

<details>
<summary><strong>Detail verifikasi Build 314</strong></summary>

- Commit sumber: `bd44ec8e99d35f5227cf4c08ead183bbdc2eb4dd`
- GitHub Actions: seluruh langkah workflow berhasil, termasuk validasi frontend/navigasi, tes unit, build APK, upload artefak, dan publikasi Release.
- Aset rilis: `Kobra-OS-main.apk` dan `SHA256SUMS.txt`.
- [Lihat workflow Build 314](https://github.com/adebojel/Asisten-bantu/actions/runs/43859441145).

**Penting:** keberhasilan CI membuktikan proses build dan publikasi, bukan pengujian pada perangkat Android fisik. Mikrofon ketika layar terkunci/latar belakang, kamera, TalkBack, sensor, dan navigasi tetap harus diuji di HP nyata. Build 314 adalah **debug APK**, bukan rilis final tersertifikasi.

</details>

---

## 🧭 Jelajahi fitur

| Modul | Yang tersedia |
|---|---|
| 🎙️ **Kontrol suara** | Membuka aplikasi, navigasi layar, membaca fokus, dan menjalankan tindakan yang diizinkan Android |
| 🗣️ **Voice Access** | Membaca teks/label/kontrol yang diekspos pohon aksesibilitas, memilih target, mengetik, menggulir, dan gestur |
| 👁️ **Mata Kedua Terpadu** | Sesi kamera dengan OCR, label objek offline, panduan posisi teks, senter adaptif, dan analisis sesuai perintah |
| 🔎 **Pembaca informasi** | OCR, barcode, pembaca uang/struk, terjemahan, dan pembacaan teks yang tersedia |
| 🧠 **Otak offline** | Perintah dasar, kalkulator, dan basis pengetahuan lokal |
| 🤖 **AI lokal** | Jalur model MediaPipe dan LiteRT-LM, termasuk Qwen3 LiteRT-LM pada build `main` |
| 🔒 **AI offline-only** | Tidak memakai OpenRouter, Gemini API, webhook n8n, atau server AI eksternal |
| 🧭 **Navigasi pedestrian** | Peta dan routing berbasis data yang dapat disimpan untuk penggunaan offline |
| ♿ **Aksesibilitas** | Accessibility Service, navigasi fokus/kata, serta interoperabilitas TalkBack |
| 📞 **Komunikasi** | Alur panggilan, SMS, notifikasi, dan aplikasi pesan dengan izin serta konfirmasi |

## 🖼️ Identitas visual & arsitektur

<div align="center">

<img src="docs/kobra-hero-3d.svg" width="100%" alt="Artwork konsep 3D Kobra OS">

<sub>Artwork 3D konseptual untuk identitas produk — bukan screenshot atau representasi pasti tampilan build aplikasi.</sub>

</div>

### Bagaimana Kobra bekerja

<img src="docs/kobra-overview.svg" width="100%" alt="Diagram arsitektur Kobra OS: input, router, Android accessibility, AI lokal, AI online, dan output suara">

<sub>Diagram arsitektur konseptual. Jalur yang tersedia bergantung pada izin, perangkat, model, jaringan, dan provider.</sub>

### Arah desain antarmuka

- Tema gelap modern dengan aksen cyan dan status yang mudah dibedakan.
- Area kerja utama dibuat lapang; kontrol sekunder dikelompokkan agar tidak memenuhi layar.
- Ikon dan label dibuat konsisten, dengan target sentuh yang mudah dijangkau.
- Dialog ringkas, teks terbaca, kontras tinggi, dan kompatibel dengan pembaca layar.
- Animasi dan ornamen visual tidak boleh menghalangi aksesibilitas atau mengganggu pembacaan layar.

> Screenshot aplikasi asli belum dipublikasikan sebagai galeri repository. Ilustrasi di atas adalah artwork/diagram konsep, bukan bukti visual fitur yang sudah berjalan.

---

## 🎙️ Tahap 2 — Suara dan komunikasi offline

- Chat voice capture hanya memakai `SpeechRecognizer.createOnDeviceSpeechRecognizer` pada perangkat Android yang mendukungnya.
- Kobra tidak lagi berpindah ke recognizer sistem generik ketika paket bahasa offline tidak tersedia; aplikasi memberi tahu pengguna bahwa paket ASR offline Bahasa Indonesia diperlukan.
- Pemilihan suara TTS Bahasa Indonesia memprioritaskan dan hanya memilih voice yang ditandai tidak memerlukan jaringan.
- Kesiapan suara offline dilaporkan secara eksplisit; ketersediaan paket ASR/TTS tetap bergantung pada mesin suara dan paket bahasa yang terpasang di perangkat.
- Pembacaan notifikasi dan balasan cepat memakai Notification Listener dan RemoteInput jika aplikasi pengirim menyediakan tindakan balas.
- SMS memerlukan izin Android yang sesuai. WhatsApp, panggilan suara/video, voice note, dan aplikasi pesan lain harus dioperasikan melalui kontrol aksesibilitas yang tersedia; dukungan setiap aplikasi perlu diverifikasi pada perangkat nyata.

## 👁️ Tahap 3 — Mata KOBRA dan pembaca layar

- Kamera dan pertanyaan visual diarahkan ke analisis lokal: OCR ML Kit, label objek on-device, deteksi wajah, barcode/QR, warna, dan parser uang/struk.
- Jalur kamera tidak lagi memanggil Gemini Vision, OpenRouter Vision, atau endpoint AI lain. Jika analisis lokal tidak mampu menjawab, KOBRA menyebutkan keterbatasannya, bukan mengirim gambar ke server.
- Focus Box KOBRA menggunakan geometri kata yang benar-benar disediakan node aksesibilitas; untuk tombol/kontrol, menggunakan batas node yang sedang mendapat accessibility focus. Overlay transparan tidak menerima sentuhan dan tidak menebak lokasi.
- Pembaca layar KOBRA dapat mengindeks elemen yang diekspos oleh AccessibilityService, membacakan label, memindahkan fokus, dan menandai target fokus.
- Pembaca layar mandiri dapat diaktifkan dengan perintah suara “aktifkan pembaca layar Kobra” dan dimatikan dengan “matikan pembaca layar Kobra”. Layanan aksesibilitas KOBRA harus diaktifkan di Setelan Android; TalkBack bawaan tidak harus dinyalakan.
- Batasan: aplikasi pihak ketiga dapat menyembunyikan node atau memakai tampilan kanvas/secure window; KOBRA tidak dapat menjamin seluruh konten aplikasi terbaca. Label objek bukan pengukuran jarak, deteksi bahaya, atau jaminan rute aman. Tetap gunakan alat bantu mobilitas fisik.
- Build Gradle mengunduh EfficientDet-Lite0 dari URL HTTPS yang dipatok, memeriksa ukuran dan SHA-256, lalu memasukkannya ke assets aplikasi. Jika inisialisasi model gagal saat runtime, jalur label objek cadangan digunakan.

## 🧠 AI lokal sepenuhnya (offline-only)

Kobra menggunakan model lokal dan basis pengetahuan perangkat. Jalur AI server eksternal dinonaktifkan; kegagalan model lokal tidak boleh mengirim prompt, screenshot, atau data pengguna ke provider cloud.

| Jalur | Teknologi / format | Catatan |
|---|---|---|
| AI lokal Qwen | LiteRT-LM · `.litertlm` | Qwen3 0.6B INT4 disertakan pada build `main` |
| AI lokal Gemma | MediaPipe · `.task` | Digunakan bila runtime/model kompatibel tersedia |
| AI server | Dinonaktifkan | Tidak ada panggilan ke OpenRouter, Gemini API, atau webhook AI eksternal |
| Otak dasar offline | Mesin aturan + kalkulator + basis pengetahuan | Bukan LLM generatif penuh |

### Integritas model

Model Qwen3 LiteRT-LM diambil dari revisi Hugging Face yang dipatok. Proses Gradle memvalidasi ukuran **347.251.840 byte** dan SHA-256 sebelum model digunakan. Model berlisensi Apache-2.0: [Qwen3 0.6B INT4 model card](https://huggingface.co/litert-community/Qwen3-0.6B-int4).

MediaPipe EfficientDet-Lite0 digunakan untuk bounding box objek; file model diverifikasi dengan SHA-256. Label objek dan posisi dalam bingkai bukan pengukuran jarak fisik.

Paket model terpisah untuk impor manual/pemulihan: [Kobra Offline Models v1.0](https://github.com/adebojel/Asisten-bantu/releases/tag/kobra-offline-models-v1.0).

### Catatan suara offline

Pengenalan suara Chat menggunakan Android on-device recognition bila tersedia. Jika paket bahasa Indonesia tidak tersedia, KOBRA memberi tahu pengguna dan tidak beralih ke recognizer jaringan. TTS menggunakan voice Bahasa Indonesia offline jika sudah terpasang. Model ASR/TTS mandiri belum dibundel, sehingga pengalaman offline bergantung pada paket bahasa dan voice di perangkat.

---

## 🤖 Agen Android lokal — Tahap 4

Agen layar menjalankan urutan navigasi eksplisit secara lokal tanpa meminta rencana ke model cloud. Pada implementasi awal ini, langkah yang didukung dibatasi pada membuka aplikasi, klik label kontrol yang unik dan aman, scroll, kembali, serta beranda.

- Perintah multi-langkah dapat dirangkai dengan kata **lalu** atau **kemudian**.
- Setiap target klik dicari ulang dari pohon aksesibilitas aktif; target ambigu, tidak terlihat, tidak clickable, editable, checkable, atau berisiko ditolak.
- Perubahan layar diverifikasi setelah setiap langkah. Jika layar tidak dapat diverifikasi, agen berhenti dan tidak melanjutkan.
- Perintah **“hentikan agen layar”** atau **“batalkan agen layar”** meminta pembatalan di antara langkah.
- Perintah **“riwayat agen layar”** membacakan catatan lokal; **“hapus riwayat agen layar”** menghapusnya. Riwayat hanya menyimpan jenis aksi, label target yang dibatasi panjangnya, dan hasil. Screenshot, isi layar, isi pesan, password, serta notifikasi tidak disimpan.
- Agen tahap awal tidak mengetik teks, mengirim pesan, melakukan panggilan, transaksi, mengubah izin, atau mengubah pengaturan sistem.
- Klik yang tidak didukung harus dilakukan secara manual melalui kontrol suara biasa.

Contoh: **“Kobra buka Chrome lalu klik Search kemudian scroll ke bawah.”**

### System Control, Communication, dan Routine Automation

Kontrol sistem yang tersedia memakai API Android yang memang diizinkan. Volume media dapat diatur relatif atau dengan persentase eksplisit; senter dapat dikendalikan bila perangkat menyediakan flash; mode dering/getar/senyap mengikuti izin sistem. Wi-Fi, Bluetooth, data seluler, lokasi, kecerahan, dan rotasi membuka halaman pengaturan Android agar pengguna menyetujui perubahan di sistem. Kobra tidak mengklaim dapat memaksa toggle Wi-Fi atau mobile data pada Android modern.

Contoh perintah:
- **“Kobra atur volume ke 50 persen.”**
- **“Kobra mode senyap.”**, **“Kobra mode getar.”**, **“Kobra mode berdering.”**
- **“Kobra Wi-Fi.”**, **“Kobra data seluler.”**, **“Kobra rotasi layar.”**

Routine lokal tahap ini mendukung **pengingat suara harian** yang disimpan di perangkat, dijadwalkan dengan AlarmManager, dipulihkan setelah reboot, dan dapat dibatalkan melalui **“hapus semua agenda”**. Contoh: **“Kobra ingatkan setiap hari jam 07:00 minum obat.”** Kondisi opsional yang didukung: **“jika baterai di bawah 20 persen”** atau **“jika sedang dicas”**. Kobra dapat membacakan daftar rutinitas dan status eksekusi melalui **“daftar rutinitas”**. Aksi yang diizinkan hanya notifikasi dan pengumuman suara lokal; belum menjalankan tindakan UI arbitrer atau kondisi lintas aplikasi.

Komunikasi tetap memakai alur terpisah: Kobra meminta isi pesan, membacakan penerima dan isi, lalu menunggu konfirmasi sebelum pengiriman SMS. Panggilan juga meminta konfirmasi. Jika lebih dari satu SIM aktif dan izin status telepon tersedia, Kobra meminta konfirmasi tambahan bahwa pengiriman memakai SIM default SMS Android; pemilihan SIM per pesan belum dilakukan di dalam Kobra. Izin yang dicabut, nomor tidak valid, layanan SMS yang tidak tersedia, atau SIM/operator bermasalah akan menghentikan tindakan.

### Offline pedestrian routing

BRouter menghitung rute dari segmen RD5 lokal yang telah tersimpan dan diverifikasi SHA-256. Kobra memeriksa cache terlebih dahulu. Jika segmen belum tersedia dan tidak ada internet tervalidasi, rute ditolak dengan penjelasan; segmen dapat diunduh ketika online agar perjalanan berikutnya dapat dihitung offline. Data peta dan data routing merupakan paket berbeda. Peta dasar MapLibre masih memakai style sumber eksternal saat pemuatan awal, sehingga tampilan peta tanpa jaringan bergantung pada cache/offline region yang berhasil disiapkan sebelumnya.



## ♿ Aksesibilitas dan kontrol suara

Kobra dapat membaca dan berinteraksi dengan elemen yang diekspos oleh pohon aksesibilitas Android. Fitur ini tidak menjamin akses ke semua aplikasi atau seluruh elemen layar.

Contoh perintah yang didokumentasikan:

| Perintah | Fungsi |
|---|---|
| `Kobra fokus pertama` | Pindah ke elemen fokus pertama |
| `Kobra fokus berikutnya` | Pindah ke elemen berikutnya |
| `Kobra fokus sebelumnya` | Pindah ke elemen sebelumnya |
| `Kobra fokus terakhir` | Pindah ke elemen terakhir |
| `Kobra baca fokus` | Membaca elemen yang sedang fokus |
| `Kobra baca dari atas` | Membaca layar dari awal |
| `Kobra kata berikutnya` | Navigasi ke kata berikutnya jika teks mendukung |
| `Kobra kata sebelumnya` | Navigasi ke kata sebelumnya jika teks mendukung |
| `Kobra baca kata fokus` | Membaca kata yang sedang dipilih |
| `Kobra klik kata ini` | Menjalankan aksi pada kata jika koordinat karakter tersedia |
| `Kobra bicara lebih cepat` | Menaikkan kecepatan bicara |
| `Kobra bicara lebih lambat` | Menurunkan kecepatan bicara |
| `Kobra kecepatan bicara normal` | Mengembalikan kecepatan bicara normal |

Kobra hanya menampilkan batas fokus kata bila Android menyediakan rentang seleksi dan koordinat karakter. Jika informasi tersebut tidak tersedia, batas disembunyikan—bukan diganti kotak perkiraan yang menyesatkan. Outline TalkBack asli tetap diatur oleh Android.

### Batas interoperabilitas

Aplikasi yang memakai canvas, jendela aman, menyembunyikan teks dari AccessibilityNodeInfo, atau memblokir screenshot/gestur dapat membatasi kemampuan kontrol. Jika target tidak tersedia, Kobra harus menyatakan keterbatasan tersebut dan tidak menebak.

---

## 👁️ Mata Kedua & Tongkat Digital

Mata Kedua menggabungkan OCR dan label objek dalam satu sesi kamera. Hasil OCR dapat membantu membaca teks dan memberi petunjuk posisi teks dalam bingkai. Label objek dapat membantu mengenali kategori objek yang didukung model.

**Batas keselamatan yang wajib dipahami:**

- Label objek bukan pengukur jarak, kedalaman, atau arah gerak.
- Posisi kiri/tengah/kanan hanya relatif terhadap bingkai kamera.
- Deteksi objek tidak membuktikan bahwa jalur di depan aman.
- Kobra bukan pengganti tongkat putih, pendamping, atau kewaspadaan lingkungan.
- Hasil pembacaan uang/struk berbasis OCR harus diperiksa; jumlah lembar uang tidak dijamin akurat.

---

## 🧭 Navigasi pedestrian

Navigasi menggunakan MapLibre Native, OpenFreeMap, data OpenStreetMap, dan routing BRouter. Area peta dan segmen routing perlu diunduh saat internet tersedia; perhitungan rute dapat menggunakan data lokal yang sudah tersimpan.

- Unduhan area peta dibatasi hingga 3.000 tile atau 50 MB per area.
- Routing menggunakan profil `trekking` dan data segmen OSM `.rd5`.
- Instruksi belokan diterjemahkan ke Bahasa Indonesia melalui TTS.
- Deteksi keluar jalur dan rerouting otomatis belum tersedia; bila menyimpang, hentikan panduan dan hitung ulang rute secara manual.

**Navigasi masih eksperimental.** Kelengkapan data peta dan kondisi jalan nyata dapat berbeda. Kobra tidak menjamin ketersediaan trotoar, penyeberangan, atau jalur aman. Jangan gunakan sebagai satu-satunya alat bantu mobilitas.

---

## 🛡️ Privasi, izin, dan keselamatan

- AI Kobra diproses secara lokal; kredensial provider cloud tidak digunakan.
- Izin jaringan yang masih ada hanya untuk fungsi non-AI yang terpisah seperti pembaruan aplikasi, pengambilan model/peta, atau sumber data yang memang memerlukan unduhan.
- Android membatasi aplikasi biasa untuk mengaktifkan Wi-Fi/data seluler dan mengendalikan layar sistem.
- TalkBack adalah layanan Android terpisah. Pengguna mengaktifkannya sendiri melalui Pengaturan Aksesibilitas.
- Pembacaan saldo hanya boleh berasal dari nominal berlabel saldo yang benar-benar tampil. Kobra tidak boleh meminta atau membaca PIN, password, CVV, maupun OTP.
- Transfer, pembayaran, panggilan, pesan, dan SOS harus memerlukan konfirmasi eksplisit pengguna.
- Pembacaan notifikasi/SMS dapat mengungkap informasi pribadi; aktifkan sesuai kebutuhan.
- Akses kamera, mikrofon, lokasi, notifikasi, dan aksesibilitas harus diberikan secara sadar dan sesuai fitur.

---

## 📦 Status pengembangan

| Area | Status dokumentasi |
|---|---|
| Fondasi perintah offline dan router AI lokal | Tersedia |
| Model Qwen3 LiteRT-LM pada build `main` | Tersedia |
| Fallback pengenalan objek offline | Tersedia |
| OCR dan sesi kamera Mata Kedua Terpadu | Tersedia |
| Navigasi fokus dan kata berbasis Accessibility | Tersedia dengan batasan provider |
| Workflow tes, build, dan publikasi APK | Tersedia |
| Balasan voice note native otomatis | Belum tersedia |
| Pemilihan SIM 1/SIM 2 tervalidasi | Belum tersedia |
| Screen sharing/capture lintas aplikasi satu sesi | Belum tersedia |
| Pengujian end-to-end pada HP Android fisik | Menunggu hasil uji perangkat |

### Roadmap dan audit

- [Audit integrasi open-source](OPEN_SOURCE_INTEGRATION_AUDIT.md)
- [Roadmap ekspansi open-source](OPEN_SOURCE_EXPANSION_ROADMAP.md)
- [Panduan pengujian di HP](UJI_DI_HP.md)

---

## 🧪 Panduan pengujian di HP

Ikuti [UJI_DI_HP.md](UJI_DI_HP.md) secara berurutan. Mulai dari instalasi dan izin, lanjutkan ke kontrol suara, aksesibilitas, kamera, komunikasi, dan fitur offline. Hentikan pengujian pada langkah yang gagal dan catat:

- Merek/model HP dan versi Android.
- Nomor langkah yang gagal.
- Perintah yang diucapkan.
- Respons yang diberikan Kobra.
- Screenshot atau log yang sudah disamarkan dari data pribadi.

---

## 🛠️ Build dari source

Branch pengembangan utama: `main`.

```bash
git clone --branch main https://github.com/adebojel/Asisten-bantu.git
cd Asisten-bantu
./gradlew testDebugUnitTest assembleDebug
```

APK debug hasil build lokal berada di `app/build/outputs/apk/debug/app-debug.apk`. Untuk pengguna yang hanya ingin mencoba aplikasi, gunakan APK dari [GitHub Releases](https://github.com/adebojel/Asisten-bantu/releases/latest)—tidak perlu membangun source sendiri.

## 📚 Lisensi & atribusi

Periksa lisensi masing-masing dependensi dan model sebelum distribusi turunan. Komponen yang digunakan mencakup model berlisensi Apache-2.0, BRouter (MIT), dan data OpenStreetMap (ODbL); atribusi peta ditampilkan di aplikasi. Kobra OS juga bergantung pada batasan dan ketentuan Android serta provider eksternal.

## 🐞 Laporan bug & kontribusi

Laporkan masalah melalui [GitHub Issues](https://github.com/adebojel/Asisten-bantu/issues). Sertakan langkah reproduksi dan hasil aktual. **Samarkan** nomor telepon, lokasi pribadi, isi pesan, token, API key, serta data sensitif lain sebelum mengunggah screenshot atau log.

<div align="center">

---

### **KOBRA OS**
**Suara sebagai kendali. Informasi sebagai panduan. Aksesibilitas sebagai prioritas.**

[⬇ Unduh APK](https://github.com/adebojel/Asisten-bantu/releases/latest/download/Kobra-OS-main.apk) · [Semua rilis](https://github.com/adebojel/Asisten-bantu/releases) · [Laporkan masalah](https://github.com/adebojel/Asisten-bantu/issues)

<sub>Artwork 3D dan diagram adalah ilustrasi konsep. Fitur nyata, kompatibilitas, dan keselamatan tetap harus diverifikasi pada perangkat.</sub>

</div>
