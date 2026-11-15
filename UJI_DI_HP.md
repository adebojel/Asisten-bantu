# Urutan uji di HP (kerjakan berurutan, berhenti di langkah yang gagal)

Siapkan: HP Android 9 ke atas, satu kontak uji (teman yang tahu), dan USB debugging jika ingin melihat log.

## A. Build dan pasang
1. Build lewat GitHub Actions atau Android Studio. Jika build gagal, salin 20 baris pertama pesan error (bagian `error:`) ke AI lanjutan beserta `PROMPT_LANJUTAN.md`.
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

## E. Layar
15. Buka aplikasi apa saja, ucapkan "Kobra baca layar". Pastikan Kobra membacakan teks/label dan kontrol, bukan hanya tombol.
16. Pilih elemen teks yang tidak memiliki tombol aksesibilitas: "Kobra klik [teks yang terlihat]". Pastikan Kobra mengetuk area teks yang benar atau jujur menyatakan target tidak tersedia.
17. Uji dua label mirip, misalnya beberapa tombol "Putar". Kobra tidak boleh menebak; harus meminta nomor.
18. "Kobra tampilkan nomor", pilih target, lalu ucapkan "Kobra klik nomor 1". Pastikan nomor diperbarui setelah layar berubah.
19. "Kobra scroll bawah", "Kobra geser kiri", "Kobra kembali", "Kobra beranda".

## F. Pesan masuk dan telepon
20. Minta teman mengirim WhatsApp dan SMS. Notifikasi harus dibacakan; lalu "Kobra baca notifikasi", "Kobra baca SMS".
21. Minta teman menelepon: Kobra harus menyebut status panggilan; coba "Kobra angkat telepon", lalu "Kobra tolak telepon" pada panggilan berikutnya.
22. Uji WhatsApp panggilan suara dan video pada kontak uji; periksa apakah tombol yang benar dapat ditemukan dan status panggilan dilaporkan tanpa mengarang.

## G. Baca saldo bank/e-wallet (hanya baca)
23. Buka aplikasi bank atau e-wallet uji, masuk sendiri, dan tampilkan halaman saldo. Ucapkan "Kobra baca saldo".
24. Kobra hanya boleh menyebut nominal yang berlabel saldo dan tampak pada teks layar. Jika nominal tidak tersedia, Kobra harus menyatakan tidak menemukan saldo.
25. Pastikan Kobra tidak meminta atau membacakan PIN, kata sandi, CVV, atau OTP. Jangan lakukan transfer saat pengujian.

## H. TalkBack dan Android Accessibility Suite
26. Buka Pengaturan Kobra > Kontrol Suara, lalu pilih "Buka Pengaturan Aksesibilitas Android".
27. Periksa TalkBack, Select to Speak, Accessibility Menu, pengaturan suara, gestur, dan fokus. Aktifkan TalkBack secara manual bila ingin menggunakannya.
28. Uji "Kobra status TalkBack" ketika TalkBack mati dan aktif. Pastikan Kobra tidak salah menganggap layanan aksesibilitas lain sebagai TalkBack.
29. Uji apakah suara TalkBack dan Kobra bertumpuk. Catat konfigurasi layanan yang aktif; jangan menonaktifkan layanan yang diperlukan pengguna tanpa persetujuan.

## I. Keselamatan
19. "Kobra tolong" lalu "iya": SMS berisi tautan lokasi harus sampai ke kontak uji dan Kobra menelepon. Coba dengan GPS menyala dan mati.
20. "Kobra mode tongkat", "mulai", arahkan ke dinding atau kursi.

## J. Offline
21. Matikan WiFi dan data (lewat pengaturan HP, bukan Kobra). Ucapkan "Kobra hitung dua puluh lima kali empat" (harus 100), "Kobra tersedak", "Kobra warna apa" lalu scan, "Kobra baca KTP".
22. "Kobra cari resep" harus dijawab butuh internet.

## K. AI online
23. Isi API key OpenRouter di Pengaturan AI, tekan Tes AI. Lalu tanyakan sesuatu yang tidak ada di perintah, misalnya "Kobra ceritakan tentang Candi Borobudur".

## Melaporkan masalah
Sertakan: merek dan versi Android, langkah nomor berapa yang gagal, apa yang diucapkan, apa yang dijawab Kobra.
Log (opsional, lewat komputer): `adb logcat -s KobraOS AndroidRuntime`.
