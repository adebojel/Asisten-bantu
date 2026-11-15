# Peta Ekspansi Open-Source Kobra OS

Audit lanjutan: 15 November 2026  
Target: `adebojel/Asisten-bantu`, branch `main`

Dokumen ini adalah inventaris teknis dan rencana integrasi, bukan klaim bahwa semua kode/model pihak ketiga telah digabung. Kode hanya boleh diambil jika lisensi, dependensi, kompatibilitas Android, model weights, atribusi, dan biaya runtime telah diperiksa. README yang menyebut suatu fitur tidak membuktikan fitur itu telah diuji pada perangkat Kobra.

## Prinsip integrasi Kobra

1. Satu `AsistenAccessibilityService` sebagai pintu aksesibilitas; jangan memasang service kedua dari proyek lain.
2. Satu pemilik sesi mikrofon dan satu jalur TTS yang terkoordinasi. Wake-word/VAD harus masuk ke audio pipeline yang sama.
3. UI latar belakang tidak boleh menutupi layar pengguna. Satu-satunya overlay persisten adalah tombol mikrofon kecil. Tampilan debug/izin/konfirmasi hanya muncul atas permintaan atau ketika Android mewajibkan.
4. Satu pipeline kamera; jangan membuka dua CameraX session sekaligus. Model vision berbagi frame yang sama dan ditutup ketika sesi berakhir.
5. Setiap aksi AI adalah structured action dengan validasi schema, target yang masih aktual, batas langkah, timeout, pembatalan, verifikasi hasil, dan audit log.
6. Tindakan berisiko tinggi (kirim pesan, panggilan, pembayaran, penghapusan, pemasangan aplikasi, pengiriman data, perubahan keamanan) wajib meminta konfirmasi manusia.
7. Fitur inti aksesibilitas tetap bekerja offline. Cloud AI, model, dan peta adalah tambahan yang status koneksi/biayanya harus jelas.
8. Model yang diunduh harus memakai URL resmi, ukuran minimum/maksimum, SHA-256 yang dipatok, file sementara + atomic rename, dan penghapusan file rusak.
9. Semua model memiliki manifest lisensi sendiri. Lisensi kode aplikasi tidak otomatis mencakup lisensi bobot model, dataset, suara, atau aset media.
10. Tidak ada klaim jarak, kedalaman, arah, atau keselamatan rintangan dari image-label classification tanpa sensor/model yang memang mengukur properti tersebut.

## Repositori yang diperiksa

### Agent, otak, tindakan, dan otomasi

| Repository | Lisensi repo | Sistem/fungsi yang relevan | Keputusan Kobra |
|---|---|---|---|
| [wangzan101/Mika](https://github.com/wangzan101/Mika) | Apache-2.0 | Agent Android aktif, kontrol aplikasi dan web, tugas panjang, memori preferensi, skills, background-screen operation, model/provider pilihan, test perangkat yang luas | Prioritas audit tertinggi. Adaptasi task lifecycle, interupsi/lanjutkan, screen accuracy tests, dan laporan tugas. Jangan menyalin seluruh app; beberapa mode kontrol memerlukan setup tambahan seperti wireless debugging dan bukan hak Android biasa. |
| [SunZhi-Will/OpenRing](https://github.com/SunZhi-Will/OpenRing) | MIT | ReAct coordinator, tool schemas/dispatcher, UI tree compaction, local GGUF, memory vector, script/workflow, WorkManager scheduler, cancellation, risk governance, screen/audio capture | Prioritas integrasi arsitektur. Kode `ToolRiskClassifier`, `ActionExecutor`, `UiTreeCompact`, dan governance telah diperiksa; belum disalin karena Kobra belum memiliki tool-loop yang kompatibel penuh. |
| [cAN586/openring](https://github.com/cAN586/openring) | MIT | Fork/varian OpenRing, workflow lokal, skills, model dan agent | Referensi pembanding; bukan sumber salinan kedua dari kode yang sama. |
| [Bilal140202/MobileAgent](https://github.com/Bilal140202/MobileAgent) | Tidak ada lisensi terdeteksi | Agent Kotlin native, AccessibilityService, screen capture, LLM client, loop aksi, voice input | Referensi arsitektur saja sampai lisensi penggunaan ulang diverifikasi. |
| [niki914/zafiro](https://github.com/niki914/zafiro) | MIT | Driver aksesibilitas stateless, displayId, screenshot/gesture, pruning tree, agent/skills | Adaptasi terpilih setelah pengujian multi-display; jangan bawa dependency besar atau akses Shizuku/root secara asumtif. |
| [SHRIMONESH/IRISAUTOAPP](https://github.com/SHRIMONESH/IRISAUTOAPP) | MIT | Action executor, workflow multi-langkah, state history, retry dan voice feedback | Referensi recovery dan workflow; butuh policy Kobra dan pengujian target stale sebelum kode diambil. |
| [youichi-uda/droidpilot](https://github.com/youichi-uda/droidpilot) | Belum diverifikasi | Eksperimen agent Android | Perlu audit isi dan lisensi sebelum dipertimbangkan. |

### Penglihatan, OCR, dan bantuan tunanetra

| Repository | Lisensi repo | Sistem/fungsi yang relevan | Keputusan Kobra |
|---|---|---|---|
| [DrophouseLtd/SightBuddy](https://github.com/DrophouseLtd/SightBuddy) | MIT | EfficientDet-Lite0 MediaPipe object detection dengan bounding box, konfirmasi deteksi lintas frame, find-object, OCR halaman, live text, Whisper offline, light/color scan, checksum model | Adaptasi algoritma konfirmasi temporal sudah ditambahkan pada `ObjectDetectionStability.java` dan dipakai `VisionActivity`. Model detector/binary dan modul lain belum disalin. |
| [SHWETHAA-US/VisionAssist_Android](https://github.com/SHWETHAA-US/VisionAssist_Android) | MIT | ML Kit OCR/object detection, TTS, voice commands, haptic, offline-first | Kobra telah memiliki OCR/ML Kit/TTS; jangan menggandakan pipeline. Bandingkan tes haptic dan aksesibilitas. |
| [michael2505byte/SeeForMe](https://github.com/michael2505byte/SeeForMe) | Tidak ada lisensi terdeteksi | YOLO, OCR, Vosk, multilingual TTS, CameraX | Fitur layak dibandingkan, kode tidak disalin sampai lisensi jelas. |
| [krishivg22/Lumen_Codebase](https://github.com/krishivg22/Lumen_Codebase) | Tidak ada lisensi terdeteksi | EfficientDet, OCR, document QA MobileBERT, scene captioning offline | Referensi riset; lisensi kode/model harus diverifikasi sebelum reuse. |
| [nrai18/GuideLens-Android-App](https://github.com/nrai18/GuideLens-Android-App) | GPL-3.0 | On-device vision, path planner, object detection, medicine identification | Tidak disalin ke Kobra tanpa keputusan lisensi/copy-left yang disengaja. |
| [NTDuck/uet-2627i-int3011e-3-visionaid](https://github.com/NTDuck/uet-2627i-int3011e-3-visionaid) | Metadata tidak mendeteksi lisensi; README menyebut MIT | OCR, object detection, find-object, haptic, audio onboarding | Perlu menyelesaikan ketidaksesuaian lisensi dan memeriksa file LICENSE sebelum reuse. |
| [Srushtit-2005/smart-vision-assistance](https://github.com/Srushtit-2005/smart-vision-assistance) | Tidak ada lisensi terdeteksi | OCR, object detection, currency, QR, navigation, voice | Referensi fitur saja sampai lisensi diverifikasi. |
| [rhengtl/textsnip](https://github.com/rhengtl/textsnip) | MIT | Floating OCR capture dengan ML Kit, copy/share | Referensi untuk OCR seleksi area. Kobra tidak akan menambah bubble kedua; gunakan kontrol mic yang sudah ada dan izin screen capture Android. |

### Pendengaran, suara, dan model lokal

| Repository | Lisensi repo | Sistem/fungsi yang relevan | Keputusan Kobra |
|---|---|---|---|
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | Streaming ASR, VAD, keyword spotting, TTS, speaker diarization, enhancement, source separation, Android AAR | Prioritas tinggi untuk offline voice stack. Tambahkan satu backend adapter, bukan recognizer kedua yang berjalan paralel. Perlu model Indonesia, ABI, ukuran APK, RAM dan baterai. |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache-2.0 | Offline speech recognition Android dan model bahasa | Alternatif ASR offline ringan; bandingkan akurasi Bahasa Indonesia dan ukuran model dengan sherpa. |
| [edgeaitechnologies/offline-voice-ai](https://github.com/edgeaitechnologies/offline-voice-ai) | Tidak ada lisensi terdeteksi | Facade Vosk STT + Sherpa-ONNX VITS TTS, streaming, silence stop | Referensi API; jangan menambahkan dependency sampai lisensi dan dukungan model Indonesia diverifikasi. |
| [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT | Inferensi GGUF, CPU/GPU, backend lokal dan streaming | Kandidat runtime tambahan hanya bila benchmark membuktikan lebih cocok daripada LiteRT-LM/MediaPipe yang sudah ada. |
| [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe) | Apache-2.0 | Vision tasks, audio/vision pipelines, on-device inference | Sudah dipakai Kobra untuk LLM; MediaPipe Tasks Vision perlu diuji terpisah dan kompatibilitas native harus dipastikan. |
| [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp) | Metadata lisensi tidak terkonfirmasi pada audit ini | Whisper inference lokal C/C++ | Perlu verifikasi lisensi repo dan model; sherpa-onnx sudah menyediakan jalur Whisper di proyek SightBuddy. |
| [google/oboe](https://github.com/google/oboe) | Apache-2.0 | Low-latency audio C++ Android | Kandidat bila Kobra benar-benar membutuhkan audio routing/spatial audio latensi rendah. |

### Navigasi, peta, dan persepsi ruang

| Repository | Lisensi repo | Sistem/fungsi yang relevan | Keputusan Kobra |
|---|---|---|---|
| [Subhashnaidudulla/Cogniscape-Android](https://github.com/Subhashnaidudulla/Cogniscape-Android) | Metadata tidak menunjukkan lisensi; README menyebut copyright © 2024 | OSM pedestrian routing, GraphHopper, MapLibre, GPS/kompas, spatial audio Oboe, haptic, offline route | Referensi arsitektur saja. Jangan menyalin kode tanpa izin/lisensi eksplisit. Beberapa fitur obstacle detection pada README masih ditandai planned. |
| [maplibre/maplibre-native](https://github.com/maplibre/maplibre-native) | BSD-2-Clause | Peta vektor native Android | Kandidat peta lokal; cek atribusi tile/style dan kompatibilitas. |
| [graphhopper/graphhopper](https://github.com/graphhopper/graphhopper) | Apache-2.0 | Routing OSM, profil pedestrian, offline routing server/library | Kandidat engine routing; offline mobile perlu ekstrak graph regional dan pengelolaan storage. |
| [google/oboe](https://github.com/google/oboe) | Apache-2.0 | Spatial audio / low-latency audio foundation | Dapat dipakai untuk directional audio setelah data arah dan kalibrasi perangkat tersedia. |

### Fondasi aksesibilitas dan computer vision

| Repository | Lisensi repo | Sistem/fungsi yang relevan | Keputusan Kobra |
|---|---|---|---|
| [google/talkback](https://github.com/google/talkback) | Apache-2.0 | Screen reader, navigasi fokus, granularity kata/karakter, braille | Referensi perilaku aksesibilitas. Jangan memasang TalkBack kedua; aplikasi pihak ketiga tidak dapat mengubah outline internal TalkBack. |
| [opencv/opencv](https://github.com/opencv/opencv) | Apache-2.0 | Image processing, tracking, geometry, camera transforms | Tambahkan hanya untuk tugas yang belum ditangani CameraX/ML Kit; native size/performance perlu diukur. |

## Implementasi baru yang sudah masuk

### Konfirmasi deteksi objek lintas frame

Sumber pola: SightBuddy MIT, `DetectionRules` dan `DiscoverConfirm`.

Berkas Kobra:
- `app/src/main/java/com/asisten/bantu/ObjectDetectionStability.java`
- `app/src/main/java/com/asisten/bantu/VisionActivity.java`
- `app/src/test/java/com/asisten/bantu/ObjectDetectionStabilityTest.java`

Perilaku:
- Satu label harus muncul pada tiga frame kamera berturut-turut sebelum diumumkan.
- Label hewan yang rawan false-positive memakai confidence minimum 0.60; label umum 0.55.
- Frame kosong atau label yang hilang memutus streak; sighting lama tidak dijumlahkan.
- Duplikasi label pada satu frame dihitung satu kali.
- Satu-satunya hasil yang diumumkan adalah label yang telah stabil.
- Kobra tetap menyatakan bahwa classifier label yang digunakan saat ini tidak mengukur jarak/arah dan bukan pengganti alat bantu mobilitas.

Tes unit baru:
- tiga frame berturut-turut;
- reset setelah frame kosong;
- ambang confidence lebih tinggi untuk label hewan;
- duplikasi label dalam satu frame.

## Prioritas integrasi bertahap

### P0 — Stabilitas dan keselamatan (sebelum menambah banyak model)
- Luluskan seluruh unit test dan build APK.
- Audit lifecycle kamera, bitmap, recognizer, coroutine, service, executor dan release model.
- Tambahkan structured action result: success/failure/unsupported/permission required/stale target.
- Snapshot UI diberi timestamp/fingerprint; sebelum klik, cocokkan target dengan tree terbaru.
- Risk gate dan konfirmasi pengguna untuk aksi sensitif.
- Global stop/cancel yang membatalkan agent, speech, gesture, download dan camera analysis.

### P1 — Vision engine yang benar-benar memiliki bounding box
- Evaluasi MediaPipe Tasks Vision + EfficientDet-Lite0 dari SightBuddy MIT.
- Tambahkan asset model hanya jika lisensi model, ukuran, SHA-256, dan redistribusi jelas.
- Gabungkan hasil OCR, bounding box objek dan arah relatif dalam satu frame pipeline.
- Untuk find-object, gunakan target label yang dikonfirmasi beberapa frame; gunakan bounding box untuk posisi kiri/tengah/kanan, bukan label-only.
- Jarak/depth hanya boleh diumumkan jika sensor atau model depth tervalidasi menyediakan pengukuran tersebut.

### P2 — Audio offline Bahasa Indonesia
- Evaluasi sherpa-onnx ASR/VAD/KWS/TTS dan Vosk sebagai pembanding.
- Satu AudioSessionCoordinator mengelola capture, VAD, wake word, ASR, barge-in dan TTS.
- Unduh model on-demand, verifikasi hash, tampilkan ukuran/storage, fallback ke Android recognizer.
- Tidak ada always-listening tersembunyi: indikator mikrofon, kontrol stop, privacy mode dan izin tetap terlihat/tersedia.

### P3 — Agent tindakan Android
- Adaptasi arsitektur OpenRing/Mika: ReAct coordinator, tool registry, compact UI tree, action executor, state machine, memory retrieval, workflow, scheduler.
- Gunakan satu service Kobra, satu eksekutor, dan satu audit trail.
- Rencana agent berupa JSON tervalidasi dengan schema ketat; tolak tool/parameter yang tidak dikenal.
- Setiap langkah memeriksa hasil dan memperbarui snapshot UI; stop bila target berubah atau confidence rendah.
- Aksi mengirim/menyebarkan data, transaksi, menghapus atau mengubah keamanan perlu konfirmasi eksplisit.

### P4 — Navigasi ruang dan luar ruangan
- Evaluasi MapLibre + OSM + GraphHopper untuk rute pedestrian dan peta offline.
- Tambahkan kompas, geofence, off-route detection, reroute, haptic direction cues, dan audio spasial.
- Jangan mengklaim navigasi indoor atau obstacle avoidance sebelum data peta/sensor tersedia dan pengujian lapangan dilakukan.

### P5 — Integrasi skill dan plugin lokal
- Skill manifest dengan ID canonical, versi, capability declaration, permission scope, sandbox, timeout dan signature/hash.
- QuickJS hanya untuk helper deterministik; tidak memberi akses file/network/device secara bebas.
- Skill pihak ketiga dinonaktifkan secara default dan harus mendapat persetujuan.

## Batas Android yang harus dihormati

- Aplikasi biasa tidak dapat diam-diam mengaktifkan/menonaktifkan Wi-Fi, mobile data, mengubah izin aplikasi lain, membaca layar aman, atau mengendalikan semua hardware. Sebagian aksi memerlukan Settings panel, role khusus, Device Owner, Companion Device, root/Shizuku, atau persetujuan pengguna.
- Screen capture menggunakan MediaProjection memerlukan consent sistem dan notifikasi/foreground service sesuai versi Android.
- Kamera/mikrofon latar belakang dibatasi OS dan harus mengikuti aturan foreground service, indikator privasi, serta izin.
- Overlay Kobra tidak boleh menutupi layar. Aplikasi pihak ketiga tidak bisa mengubah outline internal TalkBack.
- Fitur keselamatan tunanetra tidak boleh menggantikan tongkat putih, anjing pemandu, atau navigasi manusia yang terlatih; peringatan model dapat salah atau terlambat.

## Hasil build dan verifikasi

Perubahan `ObjectDetectionStability` memicu workflow baru. Status harus dibaca dari GitHub Actions pada commit terbaru. Lulus CI tidak sama dengan uji kamera nyata, kualitas model, atau validasi aksesibilitas di perangkat.
