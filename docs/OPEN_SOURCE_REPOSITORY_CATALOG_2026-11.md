# Katalog Riset Repository Open-Source KOBRA OS — 15 November 2026

Target: `adebojel/Asisten-bantu`  
Basis: branch `main`, commit `6d45653410a34f7e44872f629c895e4ed7aa422b`  
Branch riset: `research/open-source-expansion-2026-11-15`

Dokumen ini menambahkan hasil pemeriksaan repository publik pada 15 November 2026. Ini adalah hasil riset dan seleksi, bukan klaim bahwa kode pihak ketiga sudah digabung atau semua kemampuan README telah terbukti di perangkat. Kode, model weights, dataset, suara, dan aset harus dinilai lisensinya masing-masing.

## Kandidat baru dan keputusan

| Repository | Lisensi repository terdeteksi | Kemampuan yang relevan | Keputusan untuk KOBRA |
|---|---|---|---|
| [JMAN730/opendroid](https://github.com/JMAN730/opendroid) | NOASSERTION pada metadata GitHub; jangan reuse kode sebelum LICENSE diverifikasi | Agent Android, perencanaan multi-langkah, observasi layar, verifikasi aksi, disambiguasi kontak, model manager LiteRT-LM, task routines | Audit arsitektur dan test cases saja sampai lisensi kode dan model jelas. Periksa klaim aksi perangkat satu per satu; API Android biasa tidak memberi kontrol bebas atas semua pengaturan sistem. |
| [j0taaa/android-use](https://github.com/j0taaa/android-use) | Belum terkonfirmasi | Agent Android native, accessibility snapshots, semantic taps, coordinate gestures, app launch, foreground service, pause/stop, encrypted task history, OpenAI-compatible/Anthropic providers | Referensi desain agent loop dan UI hasil aksi. Repository menyebut dirinya early usable release dan tidak menjamin reliabilitas di semua aplikasi. Jangan menyalin sampai lisensi diverifikasi. |
| [ansaribilal14/jarvis](https://github.com/ansaribilal14/jarvis) | MIT | Local-first agent loop, observe-plan-act-verify, hasil jujur (completed/partial/failed/blocked/unverified), skill builder, per-action verification, provider routing | Kandidat kuat untuk adaptasi pola task state, skill runner, hasil terstruktur, dan verifikasi. Jangan mengganti VoiceAccessEngine atau AccessibilityService Kobra; adaptasi harus masuk ke satu service yang sudah ada. |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | Streaming/offline ASR, TTS, VAD, keyword spotting, diarization, speaker ID, language ID, audio tagging, enhancement dan source separation; Android arm64/arm32 | Prioritas tinggi untuk prototipe adapter audio offline. Bandingkan Bahasa Indonesia, latency, ukuran model, ABI, RAM, baterai, dan kepemilikan sesi mikrofon. Tidak boleh menjalankan recognizer kedua bersamaan. |
| [dineshsoudagar/local-llms-on-android](https://github.com/dineshsoudagar/local-llms-on-android) | MIT | LiteRT-LM dan ONNX local LLM, Qwen/Gemma/DeepSeek, import model, OCR/PDF, Whisper dictation, cancellable jobs, model download validation, BM25 document retrieval | Referensi untuk model lifecycle, unduhan aman, pemulihan error, dan retrieval dokumen. Kobra sudah memakai Qwen3/LiteRT-LM; jangan menambah engine kedua tanpa benchmark nyata. |
| [DrophouseLtd/SightBuddy](https://github.com/DrophouseLtd/SightBuddy) | MIT | Assistive vision, object discovery/find, OCR halaman, live text, image chat, warna/cahaya, Whisper offline, accessibility-first UI | Sudah tercatat pada roadmap utama. Lanjutkan audit algoritma object-find dan temporal detection; model weights dan seluruh lisensi aset diperiksa terpisah. |
| [michael2505byte/SeeForMe](https://github.com/michael2505byte/SeeForMe) | Tidak ada lisensi terdeteksi | CameraX + YOLO, OCR multi-bahasa India, Vosk offline, multilingual TTS | Referensi fitur saja; tidak boleh menyalin kode tanpa lisensi eksplisit. Bahasa Indonesia dan kompatibilitas model harus diuji terpisah. |
| [Cambric-software/c-around](https://github.com/Cambric-software/c-around) | Proprietary; source tersedia untuk inspeksi, hak penggunaan ulang tidak diberikan | YOLO awareness, YAMNet sound monitor, offline captions, AAC phrase bank, emergency card, saved places, multi-platform | Jangan menyalin kode, model, atau aset. Gunakan hanya sebagai daftar kebutuhan fitur dan benchmark perilaku yang dapat diuji secara independen. |

## Prioritas implementasi yang disarankan

### P0 — Agent yang dapat dipercaya
1. Definisikan kontrak aksi tunggal: `ActionRequest`, `ActionResult`, status `SUCCESS`, `FAILED`, `PARTIAL`, `BLOCKED`, `NEEDS_CONFIRMATION`, `STALE_TARGET`, dan `UNVERIFIED`.
2. Setiap snapshot layar memiliki timestamp dan fingerprint. Sebelum aksi, resolve ulang target pada snapshot terbaru; tolak target yang berubah atau ambigu.
3. Jalankan satu aksi per iterasi, amati ulang layar, verifikasi hasil, lalu rencanakan langkah berikutnya.
4. Batasi jumlah langkah, timeout, retry, penggunaan token, dan durasi tugas. Sediakan Pause, Stop, dan Cancel global.
5. Minta konfirmasi sebelum mengirim pesan, menelepon, membagikan data, melakukan pembayaran, menghapus, memasang aplikasi, atau mengubah pengaturan sensitif.
6. Simpan task journal yang dapat dipulihkan setelah proses mati, tetapi lindungi data pribadi dan kredensial dengan Android Keystore.

### P1 — Satu pipeline suara offline
1. Buat interface backend STT/TTS/VAD/keyword spotting agar engine dapat diganti tanpa menggandakan pemilik mikrofon.
2. Benchmark sherpa-onnx dan Vosk untuk Bahasa Indonesia, termasuk noise, aksen, nama kontak, angka, dan perintah singkat.
3. Uji streaming TTS yang dapat dipotong ketika pengguna mulai berbicara (barge-in).
4. Pastikan wake word, VAD, ASR, TTS, dan voice command memakai lifecycle audio terkoordinasi.

### P2 — Vision dan awareness
1. Satu sesi CameraX dibagikan ke OCR dan deteksi objek; semua analyzer harus menutup frame/ImageProxy dengan benar.
2. Deteksi objek harus memiliki bounding box, confidence, timestamp, tracking/temporal confirmation, dan deduplikasi pengumuman.
3. Jangan mengklaim jarak, arah, kedalaman, atau keamanan jalur hanya dari label kelas gambar.
4. Pisahkan mode baca dokumen, cari objek, deskripsi adegan, warna/cahaya, dan deteksi suara agar pengguna dapat mengendalikan beban baterai.

### P3 — Otak lokal dan pengetahuan
1. Pertahankan Qwen3/LiteRT-LM yang sudah ada sebagai baseline; bandingkan runtime lain memakai benchmark pada perangkat yang sama.
2. Tambahkan retrieval dokumen lokal dengan chunking, BM25/hybrid search, kutipan halaman, dan penghapusan indeks.
3. Pisahkan memori percakapan, preferensi, fakta sementara, dan data sensitif; sediakan kontrol hapus dan ekspor.
4. Router online/offline harus menampilkan provider, model, status jaringan, biaya/kuota jika diketahui, dan fallback yang benar-benar tersedia.

## Gerbang sebelum kode pihak ketiga masuk

- Lisensi kode repository dan lisensi model/weights/dataset/aset diperiksa terpisah.
- Dependency tree, minSdk/targetSdk, Kotlin/Java/AGP, ABI native, ukuran APK, dan konflik transitive dependency diaudit.
- Tidak membawa service aksesibilitas, kamera, mikrofon, overlay, atau foreground service kedua.
- Tidak meminta root, ADB, Shizuku, atau izin luas sebagai asumsi default.
- Secret/API key tidak disimpan di source, log, atau plaintext preferences.
- Tambahkan unit test, instrumentation test, dan benchmark; build sukses saja bukan bukti fungsi lapangan.
- Catat sumber, versi/commit upstream, lisensi, file yang diadaptasi, atribusi, dan perubahan lokal.

## Status pemeriksaan KOBRA

Pada saat katalog dibuat, repository utama memiliki commit `6d45653410a34f7e44872f629c895e4ed7aa422b`. GitHub Actions menunjukkan workflow **Build Kobra OS APK** untuk commit tersebut selesai dengan `success`; workflow **Auto Build and Release** masih `in_progress` saat diperiksa. Status ini hanya membuktikan pipeline build yang bersangkutan, bukan uji perangkat atau validasi semua fitur.

## Referensi utama

- https://github.com/JMAN730/opendroid
- https://github.com/j0taaa/android-use
- https://github.com/ansaribilal14/jarvis
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/dineshsoudagar/local-llms-on-android
- https://github.com/DrophouseLtd/SightBuddy
- https://github.com/michael2505byte/SeeForMe
- https://github.com/Cambric-software/c-around
