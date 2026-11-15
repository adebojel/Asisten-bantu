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


## Tambahan hasil pencarian 16 November 2026

| Repository | Lisensi kode | Sistem/fungsi yang relevan | Keputusan KOBRA |
|---|---|---|---|
| [soniqo/speech-android](https://github.com/soniqo/speech-android) | Apache-2.0 | Android SDK streaming ASR, TTS, VAD, noise cancellation via ONNX; demo loop offline VAD→ASR→FunctionGemma→action→TTS. README menyebut model utama 25 bahasa Eropa; model multilingual lebih besar masih opsional/belum terukur. | Kandidat riset audio engine. Jangan ganti engine aktif sebelum bahasa Indonesia, RAM, latency, ABI dan lisensi model diuji. |
| [edgeaitechnologies/offline-voice-ai](https://github.com/edgeaitechnologies/offline-voice-ai) | Tidak terdeteksi / belum terverifikasi | Kotlin coroutine API, Vosk STT, Sherpa-ONNX VITS TTS, streamed TTS, stop-on-silence. | Referensi API saja; tidak menyalin kode sampai lisensi dan dependensi dipastikan. |
| [SHWETHAA-US/VisionAssist_Android](https://github.com/SHWETHAA-US/VisionAssist_Android) | MIT | ML Kit object detection/OCR, Android TTS, haptic navigation, voice commands, offline-first dan TalkBack compatibility. | Kandidat audit kode untuk pembanding dengan VisionActivity/ML Kit KOBRA; jangan menambah pipeline kamera kedua. |
| [NTDuck/uet-2627i-int3011e-3-visionaid](https://github.com/NTDuck/uet-2627i-int3011e-3-visionaid) | Tidak terdeteksi pada fork | Explore/Find, YOLOv5, OCR offline, auto-capture, audio-first onboarding, haptic pulse guidance. | Feature reference; fork tidak punya lisensi terverifikasi. Cari dan audit lisensi upstream Shravan sebelum reuse. |
| [qsn-7274-ywn/SightSync](https://github.com/qsn-7274-ywn/SightSync) | AGPL-3.0 | AccessibilityService, voice/TTS, screen extraction, constrained actions, local validation, privacy disclosure dan AI proxy. | Arsitektur keselamatan dan auditability saja; jangan copy kode kecuali proyek menerima kewajiban AGPL. Prototype awal, bukan production-ready. |

Lisensi repository tidak otomatis melisensikan model, dataset, voice pack, atau binary submodule. Setiap aset perlu pemeriksaan tersendiri.


## Tambahan kandidat agen layar dan otomasi Android — 16 November 2026

| Repository | Lisensi | Sistem/fungsi yang diteliti | Keputusan integrasi |
|---|---|---|---|
| [JMAN730/opendroid](https://github.com/JMAN730/opendroid) | LICENSE berisi Apache-2.0 (metadata GitHub NOASSERTION) | Agen Android mandiri, perencanaan tugas berurutan, evaluasi hasil, re-planning, disambiguasi kontak, guard untuk aksi gabungan, provider lokal/online. | Prioritas tinggi untuk desain observe-plan-act-verify dan guard aksi; audit modul/dependency sebelum porting karena repo besar dan KOBRA sudah punya runtime/service sendiri. |
| [beingDurgesh/OnScreen-Agent](https://github.com/beingDurgesh/OnScreen-Agent) | MIT | Kotlin + Rust, agen multimodal, voice/STT/VLM/TTS provider slot, Play/Stop, tangkapan layar dan aksi accessibility. | Prioritas riset, bukan drop-in: README mensyaratkan Android 15+ sementara KOBRA mendukung lebih luas; hindari menambah service kedua. |
| [alzin/lucy-screen-agent](https://github.com/alzin/lucy-screen-agent) | MIT | Asisten suara sadar layar, multimodal, taps/types/swipes, proses multi-langkah, dokumentasi privacy dan eksperimen. | Referensi UX dan state machine; implementasi Flutter dan ketergantungan Gemini/BYOK tidak cocok disalin langsung ke native Java/Kotlin atau dijadikan fitur gratis otomatis. |
| [Bilal140202/MobileAgent](https://github.com/Bilal140202/MobileAgent) | Tidak ditemukan LICENSE | Kotlin AccessibilityService, parsing pohon layar, aksi terstruktur, voice, guard konfirmasi aksi destruktif, kontrol Telegram. | Referensi desain saja; tidak menyalin kode sebelum lisensi jelas. Remote control Telegram tidak dijadikan default karena risiko privasi. |
| [j0taaa/android-use](https://github.com/j0taaa/android-use) | Tidak ditemukan LICENSE pada root | Agen lokal di ponsel, provider API, snapshot layar, semantic taps, gestures, pause/stop, history terenkripsi. | Referensi UX stop/pause dan riwayat; jangan copy kode tanpa lisensi. Provider API BYOK bukan jalur gratis bawaan. |

### Prinsip seleksi untuk seluruh repository

- **Integrasi langsung** hanya bila lisensi kode, model, dataset, aset, dan dependency sesuai; API cocok; dan fungsi dapat diuji.
- **Adaptasi arsitektur** bila idenya kuat tetapi stack/SDK tidak cocok.
- **Referensi saja** untuk repository tanpa lisensi yang jelas atau proyek eksperimental.
- **Tolak** jika memerlukan akses root/ADB tersembunyi, menambah pemilik kedua kamera/mikrofon/AccessibilityService, atau mengirim data sensitif tanpa persetujuan yang jelas.

## Tambahan hasil pencarian lanjutan — 16 November 2026

| Repository | Lisensi | Fitur/arsitektur yang ditemukan | Keputusan |
|---|---|---|---|
| [NightMean/OlliteRT](https://github.com/NightMean/OlliteRT) | Apache-2.0 | Ponsel Android menjadi server inferensi LLM lokal dengan API kompatibel OpenAI; katalog model, endpoint lokal, dan integrasi klien. | Referensi arsitektur/API untuk opsi model lokal LAN. Tidak menjalankan server kedua di dalam KOBRA tanpa benchmark baterai, suhu, memori, dan kontrol lifecycle. |
| [DaedalusApps/daedalus-echo](https://github.com/DaedalusApps/daedalus-echo) | MIT | Rekaman lokal, Whisper/sherpa-onnx transcription, Gemma 3 1B, ringkasan, mind-map, dan pencarian semantik atas rekaman. | Kandidat audit komponen memori audio; adaptasi selektif, jangan membuka pemilik mikrofon kedua atau menyalin model tanpa lisensi model terpisah. |
| [NosytLabs/openclaw-droid](https://github.com/NosytLabs/openclaw-droid) | MIT | Installer OpenClaw melalui Termux + Ubuntu proot; local agent gateway. | Bukan modul Android native. Referensi eksperimen opsional; tidak dipasang otomatis karena runtime Termux/proot, konsumsi daya dan keamanan. |
| [wink-wink-wink555/blind_navigation](https://github.com/wink-wink-wink555/blind_navigation) | MIT | ARIADNE: analisis video, navigasi suara, multi-agent, peta, pengaturan dan pesan melalui agent; backend Python/Flask. | Referensi desain multi-agent dan alur navigasi. Audit endpoint, prompt injection, autentikasi, dan ketergantungan cloud sebelum adaptasi. |
| [microsoft/accessibility-insights-for-android-service](https://github.com/microsoft/accessibility-insights-for-android-service) | MIT | Scanner/inspector untuk evaluasi aksesibilitas aplikasi Android. | Hanya alat QA dan referensi pengujian. Proyek dihentikan dukungannya pada 2023; bukan dependency runtime. |
| [X-SLAYER/flutter_accessibility_service](https://github.com/X-SLAYER/flutter_accessibility_service) | MIT | Plugin Flutter untuk event dan aksi AccessibilityService. | Referensi API saja; KOBRA native Java/Kotlin sehingga tidak perlu menambah Flutter bridge. |
| [Ahmednaser30/blind-guider](https://github.com/Ahmednaser30/blind-guider) | Tidak ada lisensi terverifikasi | Gesture navigation audio-first, TFLite SSD object detection, GPU delegate, Bluetooth band. | Referensi fitur; jangan salin kode/model tanpa lisensi dan audit usia model. |
| [aminbaybon/blind-vision-assistant](https://github.com/aminbaybon/blind-vision-assistant) | Tidak ada lisensi terverifikasi | YOLO26n, MiDaS small, Piper TTS offline; peringatan hambatan berbasis kedalaman relatif. README menyatakan tidak mengukur meter dan tidak menjamin jalur aman. | Referensi penting untuk komunikasi risiko yang jujur dan evaluasi depth relatif; tidak reuse kode/model sebelum lisensi diperjelas. |
| [renjithsd/SmartBlindAssistant](https://github.com/renjithsd/SmartBlindAssistant) | Tidak ada lisensi terverifikasi | Kotlin, CameraX, ML Kit OCR/object detection, pengenalan mata uang, GPS, emergency SMS. | Referensi daftar fungsi; tidak copy tanpa lisensi dan belum terbukti implementasi matang. |
| [gobinda789/object_navigation_for_blind_person_app](https://github.com/gobinda789/object_navigation_for_blind_person_app) | Tidak ada lisensi terverifikasi | Deteksi objek dan depth estimation untuk panduan rintangan. | Referensi konsep saja; klaim free for personal/educational use bukan lisensi open-source yang cukup untuk reuse komersial. |
| [codingaslu/Voice-Vision-Assistant-for-Blind](https://github.com/codingaslu/Voice-Vision-Assistant-for-Blind) | Tidak ada lisensi terverifikasi | Voice + vision + NLP untuk pertanyaan tentang lingkungan dan pengenalan objek. | Referensi arsitektur saja sampai lisensi, model, dan implementasi diverifikasi. |
| [lee-b/kobold_assistant](https://github.com/lee-b/kobold_assistant) | AGPL-3.0 | Percakapan suara offline dengan Whisper dan TTS lokal, namun upstream menyatakan proyek lama/outdated. | Tidak reuse code karena AGPL dan status usang; konsep turn-taking dan konfirmasi ucapan saja. |
| [tim-gromeyer/VoiceAssistant](https://github.com/tim-gromeyer/VoiceAssistant) | GPL-3.0 | Asisten suara offline yang dapat dikustomisasi; proyek masih tahap awal. | Referensi arsitektur saja karena GPL dan status belum matang. |
| [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) | GPL-3.0 | Kotlin engine, OpenCV/YOLOv8, RAG chatbot on-device. | Tidak relevan sebagai fitur produk langsung; referensi teknik computer vision/RAG saja dan tidak reuse GPL. |

### Prioritas integrasi setelah audit

1. **P0 — audio Indonesia dan turn-taking:** uji sherpa-onnx yang sudah ada di katalog dibanding speech-android (Apache-2.0) dan komponen audio Daedalus Echo (MIT). Kriteria: model bahasa Indonesia, streaming latency, VAD, TTS chunking, pembatalan, pemakaian RAM/baterai.
2. **P1 — QA aksesibilitas:** gunakan pola scanner dari Microsoft Accessibility Insights hanya sebagai test harness/daftar pemeriksaan; jangan memasukkannya ke APK produksi.
3. **P1 — navigasi aman:** pelajari ARIADNE dan Blind Vision Assistant untuk depth relatif, peringatan konservatif, dan keterbatasan eksplisit. Tidak boleh menyebut jarak meter atau jalur aman tanpa sensor/model terkalibrasi.
4. **P2 — local inference server:** OlliteRT hanya opsi terpisah untuk perangkat kedua/LAN; jangan mengganggu satu runtime LiteRT-LM KOBRA.
5. **Ditolak untuk copy saat ini:** semua repository tanpa lisensi, AGPL/GPL, proyek usang, atau sistem yang memerlukan service kamera/mikrofon kedua.

## Catatan audit kualitas repository

Banyak hasil pencarian GitHub berupa proyek demo, tugas kuliah, fork, prototype server-based, atau repositori tanpa lisensi. Jumlah repository bukan ukuran kualitas. KOBRA hanya mengadopsi implementasi setelah source code, tests, commit upstream, lisensi dependency, model weights, ABI, minSdk, ukuran, performa, privasi, dan lifecycle diverifikasi.

## Tambahan riset audio, navigasi, dan assistive sensing — 16 November 2026

| Repository | Lisensi kode | Sistem/fungsi | Penilaian untuk KOBRA |
|---|---|---|---|
| [R-Dilipkumar/LiveSub-Offline-Translator](https://github.com/R-Dilipkumar/LiveSub-Offline-Translator) | MIT | Streaming ASR offline, terjemahan on-device, VAD; README mencantumkan model multilingual yang termasuk Bahasa Indonesia. Android 10+, Sherpa-ONNX dan ML Kit. | Kandidat untuk menguji ASR Indonesia offline. Ambil adapter/model hanya setelah verifikasi lisensi masing-masing model, ukuran, latency dan penggunaan mic. Jangan ambil overlay subtitle karena KOBRA hanya mempertahankan orb mic sebagai overlay persisten. |
| [LucT3/Indoor-Navigation-Assistant-App](https://github.com/LucT3/Indoor-Navigation-Assistant-App) | GPL-3.0 | Navigasi indoor melalui BLE beacon dan QR code. | Referensi fitur indoor; memerlukan infrastruktur beacon/QR yang dipasang di lokasi. Jangan copy kode kecuali proyek menerima kewajiban GPL. |
| [Fox-Islam/android-agent](https://github.com/Fox-Islam/android-agent) | Tidak ditemukan LICENSE | Agen ponsel via AccessibilityService, semantic screen actions, checklist, MCP tools, pertanyaan saat macet, confirmation-before-irreversible. README menyebut keterbatasan FLAG_SECURE, lock screen dan runtime permission dialogs. | Referensi untuk pause/stop, risk policy, dan pengungkapan keterbatasan. Tidak menyalin kode tanpa lisensi. |
| [SuyashVMane/NaviSense](https://github.com/SuyashVMane/NaviSense) | Tidak ditemukan LICENSE | CameraX/TFLite vehicle detection, prioritas haptic untuk hazard, directional vibration. README menyebut navigation simulation/demo mode. | Ide haptic urgency saja; bukan sumber implementasi navigasi nyata dan tidak boleh dianggap sudah tervalidasi. Tidak menyalin kode. |
| [Amir-Nafissi/CueMate](https://github.com/Amir-Nafissi/CueMate) | MIT | MediaPipe face/gesture recognition, local inference, speech dan directional haptics. | Kandidat riset aksesibilitas sosial opsional. Tidak menjadi prioritas P0; deteksi ekspresi bukan fakta pasti tentang emosi/niat seseorang dan harus diberi batasan ketidakpastian. |
| [michael2505byte/SeeForMe](https://github.com/michael2505byte/SeeForMe) | Tidak ditemukan LICENSE | YOLO, OCR, Vosk offline, TTS multi-bahasa dan auto-capture. | Referensi fitur; jangan menyalin kode atau model sebelum lisensi ditemukan. |
| [ssharmapavitra/Shravan](https://github.com/ssharmapavitra/Shravan) | Tidak ditemukan LICENSE pada repository upstream | TFLite object detection, OCR reader, camera overlay. | Repo upstream belum memuat lisensi; jangan copy. Fork VisionAid menyebut MIT di README tetapi tidak menyediakan file LICENSE, jadi lisensi belum dianggap terverifikasi. |

### Catatan Bahasa Indonesia

- `LiveSub` mencantumkan model streaming multilingual yang menyertakan Bahasa Indonesia, tetapi lisensi model, ukuran, dan performa harus diverifikasi satu per satu.
- `soniqo/speech-android` belum menjadi kandidat ASR Indonesia karena README utamanya menyebut 25 bahasa Eropa.
- `sherpa-onnx` tetap menjadi framework utama yang layak diuji untuk model Indonesia; framework Apache-2.0 tidak otomatis melisensikan setiap model yang diunduh.


## Tambahan riset target resolution dan mobile GUI agents — 16 November 2026

| Repository | Lisensi | Temuan teknis | Keputusan KOBRA |
|---|---|---|---|
| [NITISH-R-G/ScreenSaathi](https://github.com/NITISH-R-G/ScreenSaathi) | MIT | Kotlin native, Accessibility API, target resolver dengan skor exact/normalized/word-prefix, ambiguitas berbasis margin dan overlap, policy guard, eval/test suite. UI highlight hanya menunjuk; pengguna yang mengetuk. Min SDK 26. | Prioritas tinggi. Pola target ranking dan refusal diadaptasi menjadi `TargetLabelMatcher` KOBRA. Tidak menyalin overlay cursor/ring penuh karena KOBRA mempertahankan mic-only overlay dan batas fokus kata yang tipis. Sarvam voice stack tidak diambil karena fokus bahasa dan layanan berbeda. |
| [GiggleWang/MobileAgent-Android](https://github.com/GiggleWang/MobileAgent-Android) | MIT | Android-native VLM agent dengan Manager/Executor/Reflector/Notetaker, accessibility tree, screenshot, before/after reflection dan execution log. | Referensi arsitektur refleksi; tidak menambah status overlay persisten. KOBRA memakai agen navigasi yang lebih terbatas dan model gratis terverifikasi. |
| [Core-Mate/OpenGUI](https://github.com/Core-Mate/OpenGUI) | Business Source License 1.1 (Additional Use Grant: None) | Multi-role Android GUI agent, real screenshots, app operations, testing and task workflows. | Referensi konsep saja; BSL bukan lisensi open-source permisif untuk menyalin kode ke KOBRA. |
| [MadeAgents/mobile-use](https://github.com/MadeAgents/mobile-use) | MIT | ADB-driven multi-agent GUI automation, hierarchical reflection, proactive exploration, AndroidWorld/AndroidLab evaluation. | Benchmark dan evaluasi saja; tidak cocok menjadi runtime on-device karena bergantung pada ADB/host. |
| [appwiz/droidrun](https://github.com/appwiz/droidrun) | MIT | Python framework untuk Android/iOS, tools UI-native, screenshots, tap/swipe/type, planning dan tracing. | Referensi evaluasi/automation harness; bukan library native Android siap-tempel. |
| [orailnoor/private-agent](https://github.com/orailnoor/private-agent) | Tidak ditemukan LICENSE | Flutter + AccessibilityService, DeepSeek/OpenRouter free model, voice, Telegram remote control, multi-step automation. | Referensi saja; tidak menyalin kode tanpa lisensi, dan Telegram remote control tidak menjadi fitur default. |

### Dampak implementasi ScreenSaathi

KOBRA sekarang memakai resolver murni yang:
- Mengutamakan exact text, lalu exact resource ID, normalized match, word-prefix, dan word-boundary match.
- Mengutamakan elemen actionable dan area yang lebih kecil ketika skor setara.
- Menolak dua target berbeda yang skornya hampir sama; tidak memilih berdasarkan urutan traversal.
- Menggunakan batas huruf/angka Unicode, agar kata seperti `call` tidak cocok di dalam `Recall`.
- Tetap menggunakan accessibility node aktual; tidak membentuk hitbox buatan per kata dari teks OCR.

Implementasi berada di `voiceaccess/TargetLabelMatcher.kt`, dipanggil oleh `NodeIndexer.findByLabel()`, dengan unit test untuk exact match, ambiguitas, overlap, resource ID dan batas kata Unicode.
