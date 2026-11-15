# Audit Integrasi Open-Source Kobra OS

Tanggal audit: 15 November 2026  
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

## Perubahan yang sudah masuk ke Kobra

1. NodeIndexer.kt
   - Menggunakan ACTION_NEXT_AT_MOVEMENT_GRANULARITY dan ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY untuk navigasi kata.
   - Meminta koordinat karakter dari AccessibilityNodeInfo.refreshWithExtraData(EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, ...).
   - Menggabungkan kotak karakter menjadi batas kata yang sedang dipilih.
   - Mengembalikan null bila aplikasi tidak menyediakan geometri teks; tidak membuat koordinat palsu.

2. FocusOutlineOverlay.kt
   - Overlay sekarang hanya sebesar area kata/elemen yang sedang difokuskan, bukan jendela layar penuh.
   - Menggunakan TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_TOUCHABLE, dan FLAG_NOT_FOCUSABLE.
   - Tidak memiliki lapisan gelap, fill, glow, atau panel.
   - Garis batas sangat tipis dan transparan.

3. VoiceAccessEngine.kt
   - Menambahkan perintah kata berikutnya, kata sebelumnya, baca kata fokus, dan klik kata ini.
   - Nomor dan grid visual tidak lagi ditampilkan; indeks elemen dibacakan melalui suara.
   - Menambahkan koordinat suara 10×10, contoh ketuk B3 dan geser A1 ke C5.
   - Memperbarui fokus kata ketika event seleksi atau teks berubah.

4. FloatingVoiceOrbManager.kt
   - Gelembung mikrofon diperkecil dari 62dp menjadi 44dp.
   - Tab tepi diperkecil menjadi 8dp.
   - Double-tap tetap memanggil indeks elemen suara, bukan menampilkan badge visual.

## Batas teknis yang harus dipertahankan

- Android API character-location hanya menyediakan geometri karakter untuk view yang mendukung permintaan extra data. Provider aplikasi, WebView, Compose, canvas, konten aman, dan beberapa komponen custom dapat tidak menyediakannya.
- Kobra hanya menggambar batas kata bila koordinat karakter benar-benar tersedia. Jika tidak, Kobra memakai batas kontrol kecil yang valid atau menyembunyikan batas; tidak menggambar kotak besar sebagai pengganti kata.
- Screenshot OCR lintas aplikasi memerlukan persetujuan MediaProjection dan indikator sistem. OCR tidak boleh berjalan diam-diam di layar aman.
- AccessibilityService tidak menjamin kendali atas seluruh aplikasi, dialog sistem, layar pembayaran, atau konten yang sengaja tidak diekspos.
- Hanya satu komponen Kobra yang boleh menjadi pemilik mikrofon pada satu waktu. Jangan menggabungkan service contoh yang membuat SpeechRecognizer kedua.
- Perubahan kode belum dianggap lulus sampai workflow Gradle selesai dan pengujian pada perangkat nyata dilakukan.

## Perintah suara untuk pengujian

- Kobra kata berikutnya
- Kobra kata sebelumnya
- Kobra baca kata fokus
- Kobra klik kata ini
- Kobra ketuk B3
- Kobra geser A1 ke C5
- Kobra baca layar
