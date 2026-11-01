# 🐍 KOBRA OS V6 - OTAK SUPER + TONGKAT PINTAR + AI OFFLINE

**V6 adalah versi TERLENGKAP dari NOL sampai V6 - Semua fitur V1-V5 ada di dalamnya + 3 fitur baru dewa**

## FITUR BARU V6

### 1. 🪪 BACA KTP / KK / DOKUMEN (OCR KTP)
- Perintah: "Kobra baca KTP" / "Kobra baca KK" / "Kobra baca dokumen"
- Buka kamera, arahkan ke KTP, tekan SCAN
- ML Kit OCR baca NIK, Nama, TTL, Alamat, deteksi apakah KTP atau KK
- Bicara: "Dokumen terdeteksi KTP. NIK 123456... Nama Budi..."
- Penting buat tunanetra verifikasi dokumen sendiri tanpa bantuan orang!

### 2. 🦯 NAVIGASI TONGKAT PINTAR (Deteksi Halangan + Getar)
- Perintah: "Kobra mode tongkat" / "Kobra tongkat pintar" / "Kobra deteksi halangan"
- Buka kamera belakang terus menerus (stream mode)
- ML Kit Object Detection deteksi objek di depan
- Logika jarak: area bounding box besar = dekat, kecil = jauh
- Feedback:
  - Dekat banget (>150k area): ⛔ BAHAYA DEKAT! Getar panjang + suara "Berhenti!"
  - Sedang (70k): ⚠️ Ada halangan 2 meter, getar sedang
  - Jauh (20k): Ada objek jauh, getar pendek
  - Kosong: ✅ Aman, jalan kosong, background ijo
- Getar pakai Vibrator + VibrationEffect waveform
- Tunanetra bisa jalan pakai HP sebagai tongkat!

### 3. 🧠 TEMAN NGOBROL AI OFFLINE (Tanpa Internet!)
- Perintah: "Kobra teman ngobrol" / "Kobra ngobrol" / "Kobra curhat"
- Buka AiChatActivity, ada chat history
- Offline knowledge base: motivasi, doa, sholat, cara pakai Kobra, lelucon, cerita, curhat, terima kasih, siapa kamu, dll
- Bisa STT: tekan 🎤 NGOBROL, ngomong, AI jawab pakai TTS
- Contoh:
  - Anda: "Kobra saya sedih" -> AI: "Saya mengerti sedih... Anda tidak sendiri..."
  - Anda: "Kobra ceritakan lelucon" -> AI cerita joke
  - Anda: "Kobra cara baca uang" -> AI kasih tutorial
- Semua 100% offline, tanpa internet, tanpa API!

## FITUR LAMA TETAP ADA (V1-V5 Komplit)

- Launcher utama OS pertama, selalu standby, auto restart
- Baca layar total, baca WhatsApp: buka WA, baca layar, buka chat Abof, baca pesan, play VN konfirmasi
- Kontrol HP: WiFi, Data, Bluetooth, Senter, GPS, Mode Pesawat, Volume, Kecerahan
- Vision V5: Baca uang Rupiah, warna baju, deteksi orang, SOS Kobra Tolong kirim SMS lokasi

## CARA PAKAI V6

1. Setup SOS dulu
2. Jadikan Home Utama
3. Perintah baru V6:
   - "Kobra baca KTP" -> scan KTP/KK
   - "Kobra mode tongkat" -> jalan dengan deteksi halangan getar
   - "Kobra teman ngobrol" -> curhat AI offline
   - Tetap: "Kobra baca uang", "Kobra warna apa ini", "Kobra ada orang", "Kobra tolong", "Kobra baca layar", "Kobra buka chat Ibu"

Repo: github.com/adebojel/Asisten-bantu - V6 Final

