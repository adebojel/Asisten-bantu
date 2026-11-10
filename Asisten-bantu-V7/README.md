# Kobra OS 7.1 - Asisten Suara dan Mata Kedua untuk Tunanetra

Aplikasi Android (Java, minSdk 28) yang mengendalikan HP lewat suara: membuka aplikasi dan chat, membaca layar,
membaca notifikasi dan SMS, menelepon, mengirim pesan, SOS dengan lokasi, mengenali KTP/warna/wajah, dan
menjawab pertanyaan dasar secara offline. Bagian ringan berjalan offline, bagian yang butuh internet memakai AI online,
dan pemilihannya otomatis.

> **Status jujur:** kode ini sudah lulus pengecekan sintaks, pengujian logika murni (70 kasus), dan pengecekan
> silang resource/manifest, tetapi **belum pernah dikompilasi dengan Android SDK dan belum diuji di HP**.
> Lihat `UJI_DI_HP.md` untuk urutan uji, dan bagian "Keterbatasan" di bawah.

## Cara memakai
1. Ucapkan **"Kobra"** lalu perintah, misalnya "Kobra buka WhatsApp". Setelah Kobra menjawab, 15 detik berikutnya
   Anda boleh bicara tanpa mengucap "Kobra" lagi.
2. Tombol mengambang di layar: ketuk untuk bicara; tahan lama untuk mematikan/menyalakan mikrofon.
   Warna: hijau siaga, kuning mendengarkan, biru Kobra bicara, abu-abu mati, merah izin kurang.
3. Perintah yang butuh konfirmasi (SOS, telepon, kirim pesan) dijawab dengan "iya" atau "batal".

## Daftar perintah suara (awali dengan "Kobra")
| Kelompok | Contoh |
|---|---|
| Membuka aplikasi | buka WhatsApp / buka WA / buka kamera / buka YouTube / buka Gojek. Nama mirip atau singkatan (wa, ig, fb, yt, peta, foto) dikenali; jika ada beberapa kandidat Kobra menyebut nomor, jawab "satu", "dua", dst. |
| Mencari aplikasi | cari aplikasi bank / berapa aplikasi / sebutkan aplikasi |
| Chat langsung | buka chat Ibu / chat ke Ibu / WhatsApp ke Ibu / Telegram ke Ibu / buka chat Ibu di Telegram |
| Pesan | kirim pesan ke Ibu (WhatsApp, dikonfirmasi) / kirim SMS ke Ibu / baca SMS / baca notifikasi |
| Notifikasi otomatis | matikan baca notifikasi / nyalakan baca notifikasi |
| Telepon | panggil Ibu / angkat telepon / tolak telepon |
| Baca layar dan tombol | baca layar / sebutkan tombol, lalu "tekan nomor 3" / tekan Simpan / ketik halo |
| Gerak layar | scroll bawah, scroll atas, geser kiri, geser kanan, ketuk layar, tahan layar |
| Sistem | kembali, beranda, aplikasi terbaru, notifikasi, pengaturan cepat, kunci layar, screenshot, menu daya |
| Perangkat | senter nyala/mati, volume naik/turun, mode getar, mode normal, baterai, jam berapa, tanggal |
| Alarm dan timer | timer lima menit, alarm jam enam tiga puluh |
| Pengaturan | buka pengaturan, buka pengaturan Bluetooth / lokasi / layar / suara / baterai / aksesibilitas |
| Web | buka situs detik titik com, cari resep nasi goreng, putar lagu X di YouTube, arahkan ke Stasiun Gambir |
| Mata kedua | baca KTP, baca uang, warna apa, ada orang berapa, apa di depan, lalu "scan" |
| Keselamatan | mode tongkat (lalu "mulai"/"berhenti"), tolong (SOS), TalkBack |
| Otak offline | hitung dua puluh lima kali empat, tersedak, luka berdarah, nomor darurat, teman ngobrol |
| Kontrol Kobra | bantuan, ulangi, diam, matikan mikrofon, nyalakan mikrofon |

## Offline dan online otomatis
| Fitur | Tanpa internet | Dengan internet |
|---|---|---|
| Perintah suara, baca layar, tombol, gestur, aplikasi, chat langsung, telepon, SMS, senter, volume, alarm | Jalan | Jalan |
| Baca KTP/KK | Jalan (ML Kit) | Jalan |
| Warna, hitung wajah | Jalan | Jalan |
| Lihat sekitar | Kategori benda umum | Deskripsi AI |
| Kalkulator dan pengetahuan dasar (pertolongan pertama, hiburan) | Jalan (OfflineBrain) | Jalan (didahulukan) |
| Baca uang | Tidak tersedia | AI gambar |
| Pertanyaan bebas, teman ngobrol | Hanya hal dasar | n8n lalu AI OpenRouter |
| Buka situs, cari di Google | Diberi tahu butuh internet | Jalan |

Urutan jawaban untuk perintah yang tidak dikenali: otak offline, lalu (jika online) webhook n8n, lalu AI OpenRouter.
Kobra mengumumkan lewat suara saat koneksi tersambung atau terputus.

## Pemasangan di HP
1. Buka aplikasi Kobra OS. Ikuti tombol 1 sampai 8 dari atas ke bawah:
   izin, aksesibilitas, akses notifikasi, pengaturan AI, kontak SOS, bebaskan dari hemat baterai,
   jalankan di latar belakang, jadikan layar utama (opsional).
2. API key OpenRouter (gratis di openrouter.ai) diisi di **Pengaturan AI**. Opsional; tanpa key, fitur offline tetap jalan.
3. Webhook n8n (opsional) diisi di tempat yang sama. n8n menerima JSON `{"perintah": "...", "sumber": "kobra-os"}`
   dan sebaiknya membalas JSON `{"balasan": "teks yang dibacakan"}`.

## Build
- **GitHub Actions**: push ke cabang `main`; workflow `.github/workflows/build-apk.yml` menjalankan uji logika lalu
  `gradle assembleDebug` dan mengunggah APK sebagai artifact.
- **Android Studio**: buka folder ini, tunggu sync Gradle, lalu Run. Berkas `gradlew` tidak disertakan
  (tidak bisa dibuat tanpa jaringan); Android Studio memakai Gradle miliknya atau menawarkan membuatnya.

## Struktur
```
app/src/main/java/com/asisten/bantu/   25 kelas Java (lihat PROMPT_LANJUTAN.md untuk peta lengkap)
app/src/main/assets/kobra_kb.txt       basis pengetahuan offline (mudah diedit, format: kunci => jawaban)
app/src/main/res/                      layout, tema, ikon, konfigurasi aksesibilitas
tools/                                 pengujian logika murni (tanpa Android)
UJI_DI_HP.md                           urutan uji di HP dan cara melaporkan error
PROMPT_LANJUTAN.md                     prompt untuk melanjutkan proyek lewat AI lain
```

## Menguji logika murni (tanpa Android)
```
J=app/src/main/java/com/asisten/bantu
javac -encoding UTF-8 -d /tmp/ob $J/OfflineBrain.java $J/AppMatcher.java $J/PhoneFormat.java tools/*.java
java -cp /tmp/ob com.asisten.bantu.OfflineBrainCheck
java -cp /tmp/ob com.asisten.bantu.AppMatcherCheck
```

## Keterbatasan (jujur)
- **Belum dikompilasi dan belum diuji di HP.** Error build atau perilaku yang beda antar merek HP masih mungkin.
- **WiFi dan data seluler tidak dikendalikan** (keputusan pemilik proyek; Android 10 ke atas juga melarang aplikasi biasa).
- **TalkBack tidak bisa diaktifkan oleh aplikasi lain.** Kobra hanya membuka pengaturannya.
- **Mikrofon di latar belakang:** Android 11 ke atas membatasi akses mikrofon saat aplikasi tidak tampil. Kobra mencoba
  layanan foreground bertipe mikrofon; hasilnya berbeda per HP/versi Android. Cadangan: tombol mengambang.
- **Membuka aplikasi dari latar belakang** bisa diblokir sistem pada sebagian versi Android.
- **Chat langsung** memakai nomor di kontak. Grup atau nama yang tidak ada di kontak memakai cara cadangan (mengklik nama di daftar chat WhatsApp), yang bisa rusak jika tampilan WhatsApp berubah.
- **Angkat dan tolak telepon** memakai izin ANSWER_PHONE_CALLS; sistem tidak memberi konfirmasi pasti.
- **Baca SMS dan QUERY_ALL_PACKAGES** tidak lolos kebijakan Google Play. Aplikasi ini untuk pemasangan manual (sideload).
- **Notifikasi dibacakan keras-keras**, termasuk isi pesan pribadi. Matikan dengan "Kobra matikan baca notifikasi" atau pakai earphone.
- **Otak offline bukan model bahasa besar.** Ia mencocokkan frasa dan menghitung ekspresi. Model bahasa di HP (misalnya Gemma lewat MediaPipe) belum disertakan karena butuh unduhan ratusan MB.
- **Model AI gratis OpenRouter bisa berganti atau penuh.** Daftar bawaan ada di `KobraConfig.java`; model bisa diganti di Pengaturan AI.
- **Pengenalan objek offline kasar** (kategori umum). Baca uang butuh internet.
- Aplikasi ini **alat bantu, bukan pengganti tongkat putih** atau pendamping. Uji SOS bersama kontak darurat sebelum diandalkan.
