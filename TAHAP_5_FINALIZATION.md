# Tahap 5 — Finalisasi, optimasi, keamanan, QA, dan rilis

Dokumen ini menjadi gerbang penerimaan Tahap 5 untuk KOBRA OS. Tahap ini menyatukan pekerjaan UI/UX, aksesibilitas tunanetra, efisiensi sumber daya, privasi, pengujian regresi, dan kesiapan distribusi.

## Prinsip penerimaan

1. **Tidak mengklaim fitur yang belum diuji.** CI hijau membuktikan build dan tes otomatis yang dijalankan, bukan keberhasilan mikrofon, kamera, GPS, sensor, TalkBack, atau layanan latar belakang pada HP fisik.
2. **Offline-first tetap ketat.** Prompt, screenshot, teks layar, pesan, dan data pengguna tidak boleh dikirim ke AI cloud. Unduhan model/peta dan fungsi non-AI yang memang membutuhkan jaringan harus dijelaskan terpisah.
3. **Aksesibilitas tidak boleh merusak aksesibilitas sistem.** Jangan menonaktifkan TalkBack atau layanan lain tanpa persetujuan. Semua aksi yang mengirim komunikasi atau mengubah keadaan sensitif harus punya konfirmasi.
4. **Degradasi aman.** Jika izin, model, node aksesibilitas, data peta, atau hardware tidak tersedia, jelaskan keterbatasan dan hentikan tindakan yang tidak dapat diverifikasi.
5. **Tidak menjanjikan angka benchmark tanpa pengukuran.** Target memori/penyimpanan adalah sasaran uji pada perangkat yang disebutkan, bukan klaim universal.

## A. UI/UX dan aksesibilitas

- [ ] Navigasi utama dapat dijalankan dengan TalkBack dan kontrol suara.
- [ ] Label tombol, urutan fokus, heading, status, dan pesan error jelas.
- [ ] Ukuran teks mengikuti font scale Android; layout tidak memotong teks pada skala besar.
- [ ] Kontras teks/kontrol memadai; informasi status tidak disampaikan melalui warna saja.
- [ ] Target sentuh nyaman; kontrol penting tidak bergantung pada gestur presisi.
- [ ] Mode tunanetra/voice-first meminimalkan elemen dekoratif dan memberi umpan balik suara yang konsisten.
- [ ] Tombol berhenti menghentikan TTS/pengenalan suara/aksi berantai yang sedang berjalan.
- [ ] Dialog konfirmasi menyebut tindakan, penerima/target, dan konsekuensinya sebelum eksekusi.
- [ ] Tampilan landscape, layar kecil, keyboard terbuka, dan display size besar tidak menyebabkan kontrol utama hilang.
- [ ] Tidak ada overlay yang menghalangi TalkBack, target sentuh, atau konten aplikasi lain.

## B. Optimasi sumber daya dan stabilitas

- [ ] Audit lifecycle seluruh Activity, Service, Receiver, kamera, recognizer, TTS, coroutine/executor, listener, dan callback.
- [ ] Lepaskan CameraX/camera session, recognizer, callback, dan resource audio pada lifecycle yang tepat.
- [ ] Batalkan pekerjaan asynchronous saat pemilik lifecycle berhenti; hindari callback ke Activity yang sudah dihancurkan.
- [ ] Pastikan hanya satu sesi mikrofon/kamera aktif pada satu waktu.
- [ ] Model AI dimuat sesuai kebutuhan; hindari memuat salinan model berulang dan lepaskan interpreter/runtime saat aman.
- [ ] Cache memiliki batas ukuran/umur dan kebijakan pembersihan; jangan menghapus data pengguna tanpa persetujuan.
- [ ] Penjadwalan rutin tidak membuat alarm duplikat setelah reboot/perubahan zona waktu.
- [ ] Uji foreground/background, screen-off, rotasi, process recreation, low-memory, battery saver, dan permission revoke.
- [ ] Catat baseline RAM, CPU, ukuran APK/install, cold-start, dan konsumsi baterai pada perangkat uji nyata.
- [ ] Target awal benchmark: jejak RAM idle serendah mungkin dan sasaran sekitar 1 GB saat beban AI, serta paket/install 3–5 GB maksimum hanya bila seluruh model offline opsional memang dipasang. Angka ini harus dilaporkan per perangkat dan tidak boleh dianggap sudah tercapai sebelum diukur.

## C. Privasi dan keamanan

- [ ] Tinjau semua permission Android dan pastikan diminta saat fitur terkait digunakan, bukan tanpa konteks.
- [ ] Periksa bahwa tidak ada jalur AI cloud atau pengiriman prompt/screenshot/isi layar secara tersembunyi.
- [ ] Pastikan log lokal dibatasi, tidak menyimpan isi pesan, password, OTP, screenshot, atau teks layar sensitif.
- [ ] Pastikan riwayat dapat dihapus dan penghapusan benar-benar membersihkan penyimpanan lokal terkait.
- [ ] Validasi input command, nomor telepon, pesan, target UI, path file, dan URL unduhan.
- [ ] Validasi integritas artefak/model melalui HTTPS, ukuran, dan SHA-256 bila tersedia.
- [ ] Aksi SMS/panggilan/SOS/komunikasi dan perubahan sensitif wajib konfirmasi eksplisit.
- [ ] Pastikan tidak ada secret/API key/token dalam source, README, log, APK metadata, atau workflow output.
- [ ] Jelaskan fungsi jaringan non-AI dan data apa yang diproses sebelum pengguna mengaktifkannya.
- [ ] Uji pencabutan permission saat proses sedang berjalan; operasi harus berhenti aman.

## D. QA otomatis dan regresi

- [ ] `./gradlew testDebugUnitTest assembleDebug` lulus.
- [ ] Validasi/lint/build frontend yang dikonfigurasi repository lulus.
- [ ] Pemeriksaan statis tidak menemukan secret, endpoint AI cloud, atau fallback cloud tersembunyi.
- [ ] Uji parser perintah, kondisi rutinitas, konfirmasi komunikasi, dan keamanan target agen tetap lulus.
- [ ] Periksa workflow Actions, artefak APK, checksum, dan Release; pastikan semua berasal dari commit yang sama.
- [ ] Periksa README, panduan HP, nomor build, tautan unduhan, dan batasan fitur agar konsisten.
- [ ] Uji instalasi bersih dan upgrade dari build sebelumnya tanpa kehilangan data yang seharusnya dipertahankan.

## E. Uji penerimaan pada HP fisik

Wajib diuji pada minimal satu perangkat Android nyata; catat merek/model, versi Android, ukuran RAM, ruang kosong, dan versi APK.

- [ ] Mikrofon foreground, background, layar terkunci, dan pemulihan setelah aplikasi dihentikan.
- [ ] Speech recognition Bahasa Indonesia offline dan TTS offline.
- [ ] TalkBack aktif/nonaktif, pembaca layar KOBRA, urutan fokus, tombol berhenti.
- [ ] Kamera, OCR, barcode, label objek, flash, dan pemulihan setelah rotasi/layar terkunci.
- [ ] SMS/panggilan dengan konfirmasi batal dan setuju; uji izin dicabut dan konfigurasi dual-SIM.
- [ ] Rutinitas harian, kondisi baterai/charging, reboot, dan zona waktu.
- [ ] GPS dan navigasi dengan segmen lokal tersedia serta ketika segmen tidak tersedia.
- [ ] Uji tanpa internet untuk semua fungsi yang diklaim offline.
- [ ] Uji panas, baterai, RAM, crash, ANR, dan pemulihan setelah low-memory.
- [ ] Semua hasil dicatat; kegagalan keselamatan atau privasi adalah blocker rilis.

## F. Status gerbang rilis

| Gerbang | Bukti yang diterima | Status |
|---|---|---|
| Build dan unit test | GitHub Actions run dan log | Periksa pada setiap commit |
| Paket dan checksum | APK + SHA-256SUMS dari commit yang sama | Periksa pada setiap release |
| UI/aksesibilitas | Uji TalkBack/font scale/voice-first | Menunggu uji perangkat |
| Resource/performa | Pengukuran RAM/CPU/baterai nyata | Menunggu uji perangkat |
| Privacy/security | Audit source, permission, log, jaringan | Audit berulang |
| Hardware/E2E | Mikrofon, kamera, GPS, SMS, SIM, alarm, screen-off | Menunggu uji perangkat |

**Kriteria selesai:** semua tes otomatis lulus, tidak ada blocker keamanan/privasi, metadata rilis konsisten, dan semua pengujian fisik telah dijalankan atau secara eksplisit ditandai sebagai belum terverifikasi. Jangan menyebut Tahap 5 tervalidasi penuh jika uji perangkat belum tersedia.

## Catatan keterbatasan

Konektor repository dapat mengubah source dan memicu GitHub Actions, tetapi tidak dapat menggantikan pengujian hardware langsung di HP pengguna. Fitur yang bergantung pada microphone, camera, GPS, SIM/operator, Android power management, dan TalkBack memerlukan uji perangkat nyata.
