# Audit Integrasi Open-Source Kobra OS

Tanggal audit: 15 November 2026 (pembaruan lanjutan)  
Repository target: https://github.com/adebojel/Asisten-bantu  
Cabang kerja: main

Dokumen ini membedakan kode yang benar-benar diubah pada Kobra, pola teknis yang diadaptasi, dan proyek yang hanya dijadikan referensi. Tidak ada klaim bahwa seluruh repositori eksternal telah disalin.

## Hasil audit repositori

| Repository | Lisensi yang terdeteksi | Kemampuan relevan | Keputusan integrasi |
|---|---|---|---|
| [google/talkback](https://github.com/google/talkback) | Apache-2.0 | Screen reader Android, navigasi fokus, granularitas teks, braille, pembacaan kontrol | Referensi arsitektur utama. Tidak menggabungkan seluruh APK/service karena TalkBack adalah screen reader mandiri yang akan berkonflik bila dijadikan service kedua di dalam Kobra. Komponen yang dipilih harus ditinjau dan diberi atribusi/lisensi sebelum disalin. |
| [katoki-dev/android-voice-navigation](https://github.com/katoki-dev/android-voice-navigation) | MIT | SpeechRecognizer, AccessibilityService, pemetaan koordinat 10×10, tap dan swipe suara | Pola pemetaan koordinat A1–J10 diimplementasikan ulang secara native pada VoiceAccessEngine. Tidak menjalankan service mikrofon kedua karena Kobra sudah memiliki satu pemilik mikrofon. |
| [rhengtl/textsnip](https://github.com/rhengtl/textsnip) | MIT | Floating bubble, MediaProjection, OCR ML Kit on-device, pembersihan hasil tangkapan | Referensi alur OCR. Kode utama proyek berbasis Flutter, sehingga tidak dicampur langsung ke aplikasi native Kotlin. Kobra sudah memakai ML Kit; jalur OCR lintas aplikasi harus tetap meminta izin capture Android. |
| [judejulius/jarvis](https://github.com/judejulius/jarvis) | MIT | Wake word offline, streaming speech recognition, TTS bertahap, tool calling, fallback model | Referensi arsitektur voice-agent dan penanganan percakapan. Tidak menambahkan recognizer paralel yang merebut mikrofon Kobra. |
| [abdallahnatsheh/AssistiveDotApp](https://github.com/abdallahnatsheh/AssistiveDotApp) | Apache-2.0 | Tombol mengambang kecil, Back/Home/Lock, drag, ukuran minimal | Prinsip tampilan minimal diadaptasi: kontrol mengambang Kobra diperkecil menjadi ikon mikrofon 44dp. |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache-2.0 | Speech-to-text offline Android | Kandidat engine offline. Belum ditambahkan sebagai runtime kedua karena model bahasa, ukuran, konsumsi RAM, dan lifecycle harus diuji sebelum dipaketkan. |
| [Aniketsingh2104/NOVA](https://github.com/Aniketsingh2104/NOVA) | Tidak ada lisensi yang terdeteksi pada metadata repository yang diperiksa | Voice assistant offline dan kontrol perangkat | Referensi desain saja. Tidak menyalin kode sampai lisensi yang mengizinkan penggunaan ulang tersedia. |
| [NITISH-R-G/ScreenSaathi](https://github.com/NITISH-R-G/ScreenSaathi) | MIT | Resolusi target natural-language, deteksi ambiguitas, overlay penunjuk, policy gate tindakan | Pola ranking target dan penolakan ambiguitas diadaptasi ke NodeIndexer. Kebijakan keselamatan dipakai sebagai acuan; tidak menyalin service/overlay layar penuh. |
| [tech-master33/baosp](https://github.com/tech-master33/baosp) | NOASSERTION (metadata tidak mengonfirmasi lisensi) | Screen reader, TTS, launcher aksesibel berbasis AOSP | Referensi desain sistem tingkat OS. Tidak menggabungkan ROM, launcher, atau service terpisah ke aplikasi biasa. |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | ASR streaming, keyword spotting/wake word, VAD, TTS offline Android | Kandidat runtime audio offline dan wake-word. Belum dipaketkan karena memerlukan model bahasa, ABI/native library, dan pengukuran RAM/baterai sebelum mengganti recognizer yang ada. |
| [serrebidev/BlindPilot-Android](https://github.com/serrebidev/BlindPilot-Android) | MIT | Screen snapshot, action approval, stale-target checks, cancel/stop, device actions | Referensi pengamanan agent. Prinsip snapshot yang kedaluwarsa dan verifikasi setelah tindakan akan menjadi standar pada pengembangan agent Kobra. |
| [Tensorlong/screen-trans](https://github.com/Tensorlong/screen-trans) | Tidak ada lisensi terdeteksi pada metadata/tree yang diperiksa | OCR kata-per-kata, pengelompokan kata/baris, MediaProjection, overlay terjemahan | Referensi teknis untuk OCR word-box. Tidak menyalin kode karena lisensi tidak ditemukan pada pemeriksaan ini; Kobra memakai API koordinat karakter Android bila tersedia. |
| [takeoutfm/takeout_assistant](https://github.com/takeoutfm/takeout_assistant) | Tidak ada lisensi terdeteksi pada metadata yang diperiksa | Vosk offline, wake word, perintah suara lokal | Referensi saja. Tidak menyalin kode tanpa lisensi yang mengizinkan. |
| [rounma/mobile-agent](https://github.com/rounma/mobile-agent) | Tidak ada lisensi terdeteksi pada metadata yang diperiksa | Qwen3-ASR, Kokoro TTS, sherpa-onnx | Referensi saja. Tidak menyalin kode tanpa lisensi yang mengizinkan. |
| [MRDarshan7/OffHand](https://github.com/MRDarshan7/OffHand) | Tidak ada lisensi terdeteksi pada metadata yang diperiksa | Vosk, Qwen, ML Kit OCR, action outbox dan konfirmasi pengguna | Referensi arsitektur saja. Tidak menyalin kode tanpa lisensi yang mengizinkan. |
| [DrophouseLtd/SightBuddy](https://github.com/DrophouseLtd/SightBuddy) | MIT | Whisper offline via sherpa-onnx, VAD, perekaman push-to-talk, object discovery/find, OCR dan pembacaan teks yang dapat di-seek | Diperiksa pada file RecordingGate, ObjectCommandResolver dan DetectionRules. Kandidat untuk modul pencarian objek dan speech model; belum disalin karena membutuhkan model, lisensi model terpisah, ABI native dan uji perangkat. |
| [oiv-an/VoiceBridge](https://github.com/oiv-an/VoiceBridge) | Apache-2.0 | Snapshot teks aksesibilitas, retry saat tree sementara kosong, pemilihan engine suara lokal, overlay terjemahan | Referensi untuk snapshot immutable dan retry yang memiliki deadline. Belum disalin; Kobra harus menghindari overlay terjemahan layar penuh. |
| [SHRIMONESH/IRISAUTOAPP](https://github.com/SHRIMONESH/IRISAUTOAPP) | MIT | Action executor, workflow multi-langkah, state history, retry dan voice feedback | Kode ActionExecutor dan ScreenStateManager diperiksa. Belum disalin karena perlu adaptasi ke satu AccessibilityService Kobra dan penambahan policy/konfirmasi sebelum tindakan sensitif. |
| [niki914/zafiro](https://github.com/niki914/zafiro) | MIT | Driver aksesibilitas stateless, displayId eksplisit, gesture/screenshot, pruning tree dan node semantics | AccessibilityDriver dan PruningRules diperiksa. Kandidat desain untuk multi-display dan ekstraksi node; belum disalin agar tidak membawa dependency dan arsitektur agent yang besar. |
| [qpst4/XGesture](https://github.com/qpst4/XGesture) | AGPL-3.0 | Gesture engine, OCR engine service, one-hand/edge navigation, clipboard and overlay tooling | Tidak disalin. AGPL-3.0 membawa kewajiban copyleft yang perlu keputusan lisensi produk secara sadar; dipakai hanya sebagai referensi fitur dan desain. |
| [SHWETHAA-US/VisionAssist_Android](https://github.com/SHWETHAA-US/VisionAssist_Android) | MIT | ML Kit OCR, object detection, voice control, haptic feedback | Kandidat perbandingan; Kobra sudah memiliki jalur ML Kit, jadi tidak menambahkan implementasi duplikat. |
| [michael2505byte/SeeForMe](https://github.com/michael2505byte/SeeForMe) | Tidak ada lisensi pada metadata repo | YOLO, OCR, Vosk, multilingual TTS | Referensi fitur saja. Tidak menyalin kode sampai lisensi yang mengizinkan penggunaan ulang tersedia. |
| [Cambric-software/c-around](https://github.com/Cambric-software/c-around) | NOASSERTION / lisensi tidak terverifikasi | Deteksi objek/bahaya, klasifikasi suara YAMNet, scene description, face recognition offline | Proyek relatif baru; audit lisensi dan ketergantungan model belum memadai. Tidak menyalin kode. |
| [NTDuck/uet-2627i-int3011e-3-visionaid](https://github.com/NTDuck/uet-2627i-int3011e-3-visionaid) | Metadata API tidak mendeteksi lisensi; README mengklaim MIT | OCR offline, YOLO, find-object dengan haptic pulse, voice onboarding | Status lisensi belum konsisten antara metadata dan README; tidak menyalin sampai file LICENSE dan hak upstream diverifikasi. |
| [niki914/zafiro](https://github.com/niki914/zafiro) | MIT | Agent Android, Shizuku/root optional, skills/MCP, local shell and Python | Referensi untuk kemampuan agent lanjutan. Bukan integrasi langsung: Shizuku/root bukan hak yang dapat diasumsikan oleh aplikasi biasa dan setiap aksi sistem perlu izin eksplisit. |

| [wangzan101/Mika](https://github.com/wangzan101/Mika) | Apache-2.0 | Agent Android on-device, operasi aplikasi/web, tugas panjang, memori preferensi, skills, background-screen, pengujian perangkat | Prioritas audit tertinggi. Adaptasi task lifecycle, interupsi/lanjutkan, screen accuracy tests, dan laporan tugas. Tidak menyalin seluruh aplikasi; beberapa kontrol layar memerlukan setup tambahan. |
| [SunZhi-Will/OpenRing](https://github.com/SunZhi-Will/OpenRing) | MIT | ReAct coordinator, tool schemas/dispatcher, UI tree compaction, local GGUF, memory vector, script/workflow, WorkManager scheduler, cancellation, risk governance | Prioritas integrasi arsitektur. ToolRiskClassifier, ActionExecutor, UiTreeCompact dan governance diperiksa; belum disalin karena Kobra belum memiliki tool-loop kompatibel penuh. |
| [cAN586/openring](https://github.com/cAN586/openring) | MIT | Varian OpenRing, workflow lokal, skills dan agent | Referensi pembanding; bukan sumber salinan kedua dari kode yang sama. |
| [Bilal140202/MobileAgent](https://github.com/Bilal140202/MobileAgent) | Tidak ada lisensi terdeteksi | Agent Kotlin native, AccessibilityService, screen capture, LLM client, loop aksi, voice input | Referensi arsitektur saja sampai lisensi penggunaan ulang diverifikasi. |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | Streaming ASR, VAD, keyword spotting/wake word, TTS, speaker diarization, enhancement, source separation Android | Prioritas tinggi untuk offline voice stack. Belum dipaketkan; perlu model Indonesia, ABI, ukuran, RAM dan baterai. |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache-2.0 | Offline speech recognition Android | Alternatif ASR offline; perlu benchmark Bahasa Indonesia dan ukuran model. |
| [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT | Inferensi GGUF, CPU/GPU, backend lokal dan streaming | Kandidat runtime tambahan hanya jika benchmark lebih baik daripada LiteRT-LM/MediaPipe yang sudah ada. |
| [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe) | Apache-2.0 | Vision tasks, audio/vision pipeline dan on-device inference | MediaPipe sudah dipakai untuk LLM Kobra; Tasks Vision perlu diuji terpisah dan kompatibilitas native diverifikasi. |
| [google/oboe](https://github.com/google/oboe) | Apache-2.0 | Audio C++ Android latensi rendah dan spatial audio | Kandidat untuk directional audio setelah sumber arah dan kalibrasi tersedia. |
| [maplibre/maplibre-native](https://github.com/maplibre/maplibre-native) | BSD-2-Clause | Peta vektor native Android | Kandidat peta lokal; perlu atribusi tile/style dan pengujian storage. |
| [graphhopper/graphhopper](https://github.com/graphhopper/graphhopper) | Apache-2.0 | Routing OpenStreetMap, profil pedestrian, routing offline | Kandidat engine routing; offline mobile perlu graph regional dan pengelolaan storage. |
| [opencv/opencv](https://github.com/opencv/opencv) | Apache-2.0 | Image processing, tracking, geometri dan transformasi kamera | Tambahkan hanya untuk tugas yang belum ditangani CameraX/ML Kit. |
| [rhengtl/textsnip](https://github.com/rhengtl/textsnip) | MIT | Floating OCR capture dengan ML Kit, copy/share | Referensi OCR seleksi area; Kobra tidak menambah bubble kedua. |
| [krishivg22/Lumen_Codebase](https://github.com/krishivg22/Lumen_Codebase) | Tidak ada lisensi terdeteksi | EfficientDet, OCR, document QA MobileBERT dan scene captioning offline | Referensi riset; kode/model tidak disalin sebelum lisensi jelas. |
| [nrai18/GuideLens-Android-App](https://github.com/nrai18/GuideLens-Android-App) | GPL-3.0 | On-device vision, path planner, object detection dan identifikasi obat | Tidak disalin tanpa keputusan lisensi/copy-left yang disengaja. |
| [Subhashnaidudulla/Cogniscape-Android](https://github.com/Subhashnaidudulla/Cogniscape-Android) | Lisensi belum terverifikasi; README menyebut copyright | OSM pedestrian routing, GraphHopper, MapLibre, GPS/kompas, spatial audio Oboe dan haptic | Referensi arsitektur saja; tidak menyalin kode tanpa izin eksplisit. Beberapa fitur obstacle detection ditandai planned. |
| [Srushtit-2005/smart-vision-assistance](https://github.com/Srushtit-2005/smart-vision-assistance) | Tidak ada lisensi terdeteksi | OCR, object detection, currency, QR, navigation dan voice | Referensi fitur saja sampai lisensi diverifikasi. |
| [edgeaitechnologies/offline-voice-ai](https://github.com/edgeaitechnologies/offline-voice-ai) | Tidak ada lisensi terdeteksi | Facade Vosk STT + Sherpa-ONNX VITS TTS, streaming dan silence stop | Referensi API; tidak menambah dependency sampai lisensi dan model Indonesia diverifikasi. |

## Perubahan yang sudah masuk ke Kobra

1. NodeIndexer.kt
   - Menggunakan ACTION_NEXT_AT_MOVEMENT_GRANULARITY dan ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY untuk navigasi kata.
   - Meminta koordinat karakter dari AccessibilityNodeInfo.refreshWithExtraData(EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, ...).
   - Menggabungkan kotak karakter menjadi batas kata yang sedang dipilih.
   - Mengembalikan null bila aplikasi tidak menyediakan geometri teks; tidak membuat koordinat palsu.
   - Batas kata hanya boleh muncul bila provider memberikan rentang seleksi kata yang valid; tidak memakai kursor nol atau kotak kontrol sebagai pengganti.
   - Pencocokan target suara memakai ranking exact/normalized/word-boundary dan menolak kandidat berbeda yang skornya hampir sama.

2. FocusOutlineOverlay.kt
   - Overlay sekarang hanya sebesar area kata/elemen yang sedang difokuskan, bukan jendela layar penuh.
   - Menggunakan TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_TOUCHABLE, dan FLAG_NOT_FOCUSABLE.
   - Tidak memiliki lapisan gelap, fill, glow, atau panel.
   - Garis batas sangat tipis dan transparan.

3. VoiceAccessEngine.kt
   - Menambahkan perintah kata berikutnya, kata sebelumnya, baca kata fokus, dan klik kata ini.
   - Nomor dan grid visual tidak lagi ditampilkan; indeks elemen dibacakan melalui suara.
   - Menambahkan koordinat suara 10×10, contoh ketuk B3 dan geser A1 ke C5.
   - Resolusi target berbasis ranking dan ambiguitas diadaptasi dari desain ScreenSaathi MIT.
   - Memperbarui fokus kata ketika event seleksi atau teks berubah.

4. FloatingVoiceOrbManager.kt
   - Gelembung mikrofon diperkecil dari 62dp menjadi 44dp.
   - Tab tepi dan auto-hide dimatikan agar tidak ada kontrol lain selain mikrofon yang mengambang.
   - Double-tap tetap memanggil indeks elemen suara, bukan menampilkan badge visual.

## Batas teknis yang harus dipertahankan

- Android API character-location hanya menyediakan geometri karakter untuk view yang mendukung permintaan extra data. Provider aplikasi, WebView, Compose, canvas, konten aman, dan beberapa komponen custom dapat tidak menyediakannya.
- Kobra hanya menggambar batas kata bila provider menyediakan rentang seleksi kata dan koordinat karakter yang valid. Jika tidak, batas disembunyikan; tidak menggambar kotak kontrol, baris, paragraf, atau koordinat tebakan sebagai pengganti kata.
- Screenshot OCR lintas aplikasi memerlukan persetujuan MediaProjection dan indikator sistem. OCR tidak boleh berjalan diam-diam di layar aman.
- AccessibilityService tidak menjamin kendali atas seluruh aplikasi, dialog sistem, layar pembayaran, atau konten yang sengaja tidak diekspos.
- Hanya satu komponen Kobra yang boleh menjadi pemilik mikrofon pada satu waktu. Jangan menggabungkan service contoh yang membuat SpeechRecognizer kedua.
- Fokus Kobra adalah overlay milik Kobra. Tampilan outline internal milik TalkBack tidak dapat diubah oleh aplikasi pihak ketiga; pengguna tetap dapat mengatur TalkBack melalui pengaturan Android.
- Perubahan kode belum dianggap lulus sampai workflow Gradle selesai dan pengujian pada perangkat nyata dilakukan.

## Perintah suara untuk pengujian

- Kobra kata berikutnya
- Kobra kata sebelumnya
- Kobra baca kata fokus
- Kobra klik kata ini
- Kobra ketuk B3
- Kobra geser A1 ke C5
- Kobra baca layar
