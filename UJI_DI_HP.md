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

## D2. Agen Android lokal (navigasi aman)

1. Aktifkan layanan Accessibility Kobra dan buka aplikasi uji yang tidak berisi data sensitif.
2. Ucapkan: “Kobra buka Chrome lalu klik Search kemudian scroll ke bawah”. Agen hanya boleh menjalankan langkah yang dipahami secara eksplisit.
3. Uji target label yang tidak ada atau ambigu. Agen harus berhenti dan menjelaskan bahwa target tidak dapat diverifikasi.
4. Uji perintah berisiko seperti “buka WhatsApp lalu kirim pesan”. Agen harus menolak urutan tersebut dan tidak mengirim apa pun.
5. Saat urutan berjalan, ucapkan “Kobra hentikan agen layar”. Agen harus berhenti sebelum langkah berikutnya.
6. Ucapkan “Kobra riwayat agen layar”. Pastikan hanya jenis aksi, label target terbatas, dan hasil yang dibacakan; tidak boleh ada screenshot atau isi pesan.
7. Ucapkan “Kobra hapus riwayat agen layar”, lalu ulangi perintah riwayat. Pastikan catatan lokal kosong.
8. Pastikan agen tidak mengetik, mengirim, menelepon, mengubah izin, atau mengubah pengaturan sistem secara otomatis.
9. Catat aplikasi, versi Android, langkah, dan hasil aktual. Pengujian ini tetap harus dilakukan di HP fisik.

## D3. System Control dan rutinitas harian

1. Ucapkan “Kobra atur volume ke 50 persen”. Pastikan slider volume media Android berubah mendekati 50%.
2. Ucapkan “Kobra mode senyap”, lalu “Kobra mode berdering”. Pastikan status mode mengikuti kebijakan perangkat/DND.
3. Ucapkan “Kobra Wi-Fi”, “Kobra Bluetooth”, dan “Kobra data seluler”. Kobra harus membuka halaman pengaturan Android; pengguna sendiri yang mengubah sakelar.
4. Ucapkan “Kobra ingatkan setiap hari jam 07:00 minum obat” dengan waktu uji beberapa menit ke depan. Pastikan konfirmasi suara, notifikasi lokal, dan pengumuman muncul.
5. Uji kondisi “jika baterai di bawah 20 persen” pada perangkat dengan baterai di bawah dan di atas ambang; kondisi yang tidak terpenuhi harus melewati pengumuman, bukan menjalankan aksi.
6. Uji kondisi “jika sedang dicas” dengan kabel daya terpasang dan dilepas.
7. Ucapkan “daftar rutinitas”. Pastikan waktu, kondisi, dan status eksekusi terakhir dibacakan.
8. Uji “hapus semua agenda”. Rutinitas dan alarm satu kali harus dibatalkan.
9. Restart HP dan pastikan rutinitas harian dipulihkan. Jika izin exact alarm tidak tersedia, catat bahwa waktu pengingat dapat tidak presisi.

## D4. Komunikasi

1. Uji pencarian kontak dengan nama yang hanya cocok ke satu kontak.
2. Uji nama ambigu. Kobra harus meminta pemilihan kontak dan tidak menebak penerima.
3. Susun SMS uji ke nomor milik sendiri. Pastikan Kobra membacakan penerima dan isi lalu meminta konfirmasi.
4. Jawab “tidak”; pastikan SMS tidak dikirim.
5. Jika dua SIM aktif dan izin status telepon diberikan, pastikan Kobra meminta konfirmasi tambahan bahwa SIM default SMS Android yang akan digunakan. Atur SIM default di Pengaturan SIM sebelum menyetujui jika perlu.
6. Ulangi dengan “iya” hanya untuk nomor uji yang benar. Periksa status kirim pada aplikasi pesan/operator; ucapan Kobra bahwa permintaan diserahkan ke Android bukan bukti SMS diterima.
7. Cabut izin SMS setelah Kobra meminta konfirmasi pengiriman. Pastikan Kobra menolak mengirim dan menjelaskan izin sudah tidak aktif.
8. Uji panggilan dengan membatalkan konfirmasi. Panggilan tidak boleh dimulai.

## D5. Navigasi offline

1. Ketika internet tersedia, unduh segmen routing BRouter untuk wilayah uji dan siapkan area peta offline.
2. Putuskan internet sepenuhnya.
3. Pilih titik awal dan tujuan yang masih berada dalam cakupan segmen tersimpan, lalu hitung rute.
4. Pastikan rute dihitung dari cache lokal tanpa percobaan unduhan jaringan.
5. Uji tujuan di luar cakupan cache. Kobra harus menyatakan segmen belum tersedia dan tidak boleh mengarang rute.
6. Uji keluar jalur. Jika segmen untuk rute ulang tidak tersedia, panduan harus dihentikan dengan peringatan suara.
7. Ingat: petunjuk rute bukan jaminan trotoar aman, penyeberangan aman, atau bebas hambatan.

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

## K. Mata KOBRA dan pembaca layar
1. Putuskan internet, buka Mata KOBRA, lalu uji kamera, OCR, warna, barcode/QR, wajah, uang, dan struk.
2. Uji pertanyaan kamera umum; KOBRA harus menjawab dari kemampuan lokal yang tersedia atau menyebutkan keterbatasan. Pastikan tidak ada permintaan ke AI server.
3. Buka Setelan Android > Aksesibilitas dan aktifkan layanan aksesibilitas KOBRA (TalkBack bawaan tidak perlu diaktifkan). Ucapkan "aktifkan pembaca layar Kobra", lalu uji daftar tombol/teks dengan "baca layar", "item berikutnya", "item sebelumnya", dan "klik [nama elemen]". Uji juga "matikan pembaca layar Kobra".
4. Aktifkan "tampilkan garis fokus". Pastikan kotak fokus mengikuti kata atau kontrol yang benar-benar mendapat fokus, transparan, kecil, dan tidak menghalangi sentuhan.
5. Uji tombol "berhenti" saat KOBRA sedang membacakan daftar panjang; TTS harus langsung berhenti.
6. Uji YouTube dengan judul video yang terlihat; periksa apakah label yang dibaca sesuai dan target yang dipilih tepat. Jika aplikasi tidak mengekspos label aksesibilitas, KOBRA harus menyatakan tidak dapat memastikan target.
7. Catat aplikasi yang tidak mengekspos node, konten kanvas, atau jendela aman sehingga tidak dapat dibaca oleh KOBRA.

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


---

## Tahap 5 — UI/UX, resource, privasi, dan regresi

### L. Aksesibilitas antarmuka
1. Aktifkan TalkBack. Navigasikan seluruh layar KOBRA tanpa melihat layar; pastikan setiap tombol memiliki nama yang bermakna dan urutan fokus logis.
2. Naikkan ukuran font Android dan Display size ke nilai besar. Pastikan teks tidak terpotong dan tombol penting tetap dapat dijangkau.
3. Uji mode gelap/terang jika tersedia, kontras, status error, status izin, serta informasi yang tidak boleh hanya dibedakan dengan warna.
4. Uji layar kecil, landscape, keyboard terbuka, dan rotasi. Pastikan tidak ada dialog atau overlay yang menutupi kontrol utama.
5. Uji tombol berhenti ketika TTS, pengenalan suara, atau urutan aksi sedang berjalan.

### M. Stabilitas dan pemakaian sumber daya
1. Catat model HP, versi Android, RAM, ruang kosong, versi APK, dan status battery saver.
2. Jalankan sesi kamera/OCR beberapa kali; keluar dari layar kamera setiap kali. Pastikan kamera benar-benar dilepas dan indikator kamera/mikrofon tidak tetap aktif tanpa alasan.
3. Ulangi sesi pengenalan suara dan TTS; pastikan tidak ada dua sesi mikrofon atau antrean suara yang berjalan bersamaan.
4. Pindah aplikasi, kunci layar, rotasi, kembali ke KOBRA, dan lakukan force-stop/relaunch. Pastikan tidak terjadi crash, ANR, atau callback ke layar yang sudah ditutup.
5. Jalankan model lokal, tutup modul, lalu ulangi beberapa kali. Catat RAM sebelum, saat, dan sesudah penggunaan; jangan mengklaim target RAM tercapai tanpa pengukuran.
6. Uji battery saver dan low-memory. KOBRA harus menurunkan beban atau memberi penjelasan, bukan terus memulai sesi hardware yang gagal.
7. Catat suhu/performa dan konsumsi baterai setelah sesi 15–30 menit; hentikan jika perangkat terlalu panas.

### N. Privasi dan keamanan
1. Periksa permission Android; cabut izin mikrofon, kamera, lokasi, SMS, dan notifikasi satu per satu ketika fitur terkait sedang tidak aktif. KOBRA harus menangani penolakan tanpa crash.
2. Putuskan internet dan uji AI lokal, OCR, pembaca layar, serta perintah dasar. Tidak boleh ada fallback AI cloud.
3. Periksa log riwayat agen layar. Pastikan tidak ada screenshot, isi pesan, password, OTP, teks layar sensitif, atau token.
4. Hapus riwayat lokal dan periksa kembali bahwa riwayat tidak muncul lagi setelah aplikasi dimulai ulang.
5. Uji semua alur SMS/panggilan dengan membatalkan konfirmasi. Tidak boleh ada tindakan yang tetap dijalankan.
6. Uji target agen layar yang tidak ada, ambigu, tidak terlihat, atau berisiko. Agen harus menolak dan tidak menebak.
7. Jangan memasukkan PIN, password, OTP, atau data finansial asli ke dalam pengujian.

### O. Checklist regresi sebelum rilis
- [ ] Instalasi bersih dan upgrade dari build sebelumnya.
- [ ] Kontrol suara dan TTS Bahasa Indonesia.
- [ ] TalkBack, pembaca layar KOBRA, navigasi fokus/kata, dan tombol berhenti.
- [ ] Kamera, OCR, barcode, label objek, dan senter.
- [ ] SMS/panggilan dengan uji batal dan izin dicabut.
- [ ] Rutinitas harian, kondisi baterai/charging, reboot, dan zona waktu.
- [ ] Navigasi dengan cache lokal dan tanpa internet.
- [ ] AI lokal tanpa jaringan dan tanpa endpoint AI cloud.
- [ ] RAM/CPU/baterai dicatat pada perangkat yang sama.
- [ ] Crash/ANR tidak ditemukan dalam skenario uji.
- [ ] Build APK, checksum, README, panduan HP, dan Release mengacu pada commit yang sama.

**Laporan hasil:** isi setiap checkbox berdasarkan hasil aktual. Jika perangkat belum diuji, biarkan belum dicentang; hasil CI bukan pengganti uji fisik.
