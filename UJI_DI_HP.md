# Urutan uji di HP (kerjakan berurutan, berhenti di langkah yang gagal)

Siapkan: HP Android 9 ke atas, satu kontak uji (teman yang tahu), dan USB debugging jika ingin melihat log.

## A. Build dan pasang
1. **Tidak perlu Android Studio.** Unduh APK siap uji dari [GitHub Releases Latest](https://github.com/adebojel/Asisten-bantu/releases/latest/download/Kobra-OS-main.apk) atau [Release Build 314](https://github.com/adebojel/Asisten-bantu/releases/tag/kobra-os-build-314). Jika build berikutnya gagal, buka [GitHub Actions](https://github.com/adebojel/Asisten-bantu/actions/workflows/release.yml) dan salin pesan error.
2. Pasang APK, buka Kobra OS.

## B. Izin dan layanan
3. Tombol 1: izinkan semua. Status di atas harus menampilkan Mikrofon dan Kamera "siap".
4. Tombol 2: aktifkan layanan aksesibilitas Kobra OS. Kobra harus berkata "Kobra OS aktif".
5. Tombol 3: aktifkan akses notifikasi. Status harus "Akses notifikasi: aktif".
6. Tombol 5, 6, 7: isi kontak SOS (pakai kontak uji), bebaskan dari hemat baterai, jalankan layanan.

## C. Suara (penyebab keluhan awal)
7. Tombol mengambang harus hijau. Ucapkan "Kobra jam berapa". Harus dijawab jam. Jika tidak: lihat warna tombol (merah = izin, abu-abu = mikrofon mati).
8. Ucapkan "Kobra" lalu "baterai" tanpa jeda panjang; lalu tunggu 20 detik dan ulangi. Mikrofon harus tetap hidup.
9. Kunci layar, tunggu 1 menit, bangunkan, ucapkan "Kobra jam berapa". Catat apakah berfungsi saat layar mati (batasan Android, lihat README).

## D. Aplikasi dan chat
10. "Kobra buka kamera", "Kobra buka WA", "Kobra buka YouTube", lalu aplikasi lain yang Anda punya.
11. "Kobra buka chat <nama kontak uji>" harus membuka chat WhatsApp langsung.
12. "Kobra kirim pesan ke <kontak uji>", ucapkan isi, jawab "iya". Pesan harus terkirim.
13. "Kobra kirim SMS ke <kontak uji>", isi, "iya".
14. "Kobra buka" lalu nama yang mirip beberapa aplikasi (misalnya "Google"): Kobra harus menyebut nomor; jawab "dua".

## E. Layar, kata, dan tampilan minimal
15. Buka aplikasi apa saja, ucapkan "Kobra baca layar". Pastikan Kobra membacakan teks, label, dan kontrol yang benar-benar diekspos aplikasi.
16. Ucapkan "Kobra klik [nama tombol]" pada satu target unik. Jika ada beberapa target mirip, Kobra harus meminta pemilihan yang lebih tepat dan tidak menebak.
17. Ucapkan "Kobra tampilkan nomor" atau ketuk mikrofon dua kali. Pastikan indeks target dibacakan, tetapi tidak ada lencana nomor yang digambar di layar.
18. Ucapkan "Kobra kata berikutnya" dan "Kobra kata sebelumnya" saat fokus berada pada teks yang mendukung navigasi granularitas kata. Pastikan kata yang dibacakan berubah sesuai seleksi.
19. Ucapkan "Kobra baca kata fokus". Pastikan hanya kata yang sedang difokuskan yang dibacakan.
20. Ucapkan "Kobra klik kata ini" pada teks yang menyediakan koordinat karakter. Jika koordinat tidak tersedia, Kobra harus menyatakan tidak tersedia dan tidak menebak titik ketukan.
21. Ucapkan "Kobra ketuk B3" dan "Kobra geser A1 ke C5" pada aplikasi uji. Pastikan aksi dikirim tanpa menampilkan grid visual.
22. Ucapkan "Kobra fokus pertama", "Kobra fokus berikutnya", "Kobra fokus sebelumnya", "Kobra fokus terakhir", dan "Kobra baca fokus".
23. Ubah seleksi teks atau pindah aplikasi. Batas fokus hanya muncul untuk kata yang benar-benar memiliki rentang seleksi dan koordinat karakter; ukurannya pas mengelilingi kata, sangat tipis, transparan, dan tidak menerima sentuhan. Jika provider tidak menyediakan geometri kata, batas harus hilang (bukan berubah menjadi kotak baris atau elemen).
24. Pastikan hanya ikon mikrofon kecil 44dp yang mengambang; tidak boleh ada tab tepi atau auto-hide yang menggantikannya. Tidak boleh ada panel, toolbar, lencana nomor, grid, dimming, atau glow yang menutupi aplikasi. Uji juga "Kobra bicara lebih cepat", "Kobra bicara lebih lambat", dan "Kobra kecepatan bicara normal".

## F. Pesan masuk dan telepon
25. Minta teman mengirim WhatsApp dan SMS. Notifikasi harus dibacakan; lalu "Kobra baca notifikasi", "Kobra baca SMS".
26. Minta teman menelepon: Kobra harus menyebut status panggilan; coba "Kobra angkat telepon", lalu "Kobra tolak telepon" pada panggilan berikutnya.
27. Uji WhatsApp panggilan suara dan video pada kontak uji; periksa apakah tombol yang benar dapat ditemukan dan status panggilan dilaporkan tanpa mengarang.

## G. Baca saldo bank/e-wallet (hanya baca)
28. Buka aplikasi bank atau e-wallet uji, masuk sendiri, dan tampilkan halaman saldo. Ucapkan "Kobra baca saldo".
29. Kobra hanya boleh menyebut nominal yang berlabel saldo dan tampak pada teks layar. Jika nominal tidak tersedia, Kobra harus menyatakan tidak menemukan saldo.
30. Pastikan Kobra tidak meminta atau membacakan PIN, kata sandi, CVV, atau OTP. Jangan lakukan transfer saat pengujian.

## H. TalkBack dan Android Accessibility Suite
31. Buka Pengaturan Kobra > Kontrol Suara, lalu pilih "Buka Pengaturan Aksesibilitas Android".
32. Periksa TalkBack, Select to Speak, Accessibility Menu, pengaturan suara, gestur, dan fokus. Aktifkan TalkBack secara manual bila ingin menggunakannya.
33. Uji "Kobra status TalkBack" ketika TalkBack mati dan aktif. Pastikan Kobra tidak salah menganggap layanan aksesibilitas lain sebagai TalkBack.
34. Uji apakah suara TalkBack dan Kobra bertumpuk. Catat konfigurasi layanan yang aktif; jangan menonaktifkan layanan yang diperlukan pengguna tanpa persetujuan.

## I. Keselamatan
35. "Kobra tolong" lalu "iya": SMS berisi tautan lokasi harus sampai ke kontak uji dan Kobra menelepon. Coba dengan GPS menyala dan mati.
36. "Kobra mode tongkat", "mulai", arahkan ke dinding atau kursi.

## J. Offline
37. Matikan WiFi dan data (lewat pengaturan HP, bukan Kobra). Ucapkan "Kobra hitung dua puluh lima kali empat" (harus 100), "Kobra tersedak", "Kobra warna apa" lalu scan, "Kobra baca KTP".
38. "Kobra cari resep" harus dijawab butuh internet.

## J. Suara dan komunikasi offline
1. Putuskan koneksi internet sebelum menguji input suara.
2. Pastikan Kobra hanya memulai pengenalan suara on-device; bila mesin/paket Bahasa Indonesia offline tidak tersedia, Kobra harus memberi tahu pengguna dan tidak beralih ke layanan jaringan.
3. Uji tombol berhenti saat TTS membacakan teks panjang; suara dan antrean bacaan harus berhenti.
4. Periksa status voice TTS Bahasa Indonesia dan pastikan voice yang dipilih tidak memerlukan jaringan.
5. Uji pembacaan notifikasi serta balasan RemoteInput hanya pada aplikasi yang menyediakan action balas.
6. Uji SMS, WhatsApp, voice note, telepon, dan video call secara terpisah; catat aplikasi/versi Android dan hasil aktual. Jangan menganggap fitur berhasil hanya karena aplikasi dapat dibuka.

## K. AI lokal
39. Pastikan tidak ada menu pengisian API key cloud atau webhook AI. Putuskan internet, jalankan pertanyaan yang didukung model lokal, dan pastikan Kobra tidak mencoba menghubungi server AI. Jika model tidak tersedia/gagal, Kobra harus menjelaskan keterbatasan dan tetap menyediakan perintah dasar offline.

## Melaporkan masalah
Sertakan: merek dan versi Android, langkah nomor berapa yang gagal, apa yang diucapkan, apa yang dijawab Kobra.
Log (opsional, lewat komputer): `adb logcat -s KobraOS AndroidRuntime`.
