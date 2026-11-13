package com.asisten.bantu;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pengaturan API key OpenRouter (gratis), model AI, dan impor model offline .task. */
public class SettingsActivity extends AppCompatActivity {

    private EditText etKey, etModel, etN8n;
    private TextView tvStatus, tvModelStatus, tvImportProgress;
    private Button btnImportModel;
    private ProgressBar pbImportModel;
    private KobraConfig config;
    private Speaker speaker;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> modelPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    processSelectedModelFile(uri);
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        config = new KobraConfig(this);
        speaker = Speaker.get(this);

        etKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        etN8n = findViewById(R.id.etN8nUrl);
        tvStatus = findViewById(R.id.tvSettingsStatus);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        tvImportProgress = findViewById(R.id.tvImportProgress);
        btnImportModel = findViewById(R.id.btnImportModel);
        pbImportModel = findViewById(R.id.pbImportModel);

        etKey.setText(config.getApiKey());
        etModel.setText(config.getModel());
        etN8n.setText(config.getN8nUrl());

        Button save = findViewById(R.id.btnSaveSettings);
        Button test = findViewById(R.id.btnTestAi);
        Button back = findViewById(R.id.btnBackSettings);

        save.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Pengaturan tersimpan.");
            speaker.speak("Pengaturan AI tersimpan.");
        });

        test.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Menguji koneksi AI...");
            new AiClient(config).chat(AiClient.SYSTEM_TUNANETRA,
                    "Perkenalkan dirimu dalam satu kalimat singkat.", new AiClient.Callback() {
                        @Override public void onResult(String text) {
                            tvStatus.setText("AI menjawab: " + text);
                            speaker.speak(text);
                        }
                        @Override public void onError(String message) {
                            tvStatus.setText("Gagal: " + message);
                        }
                    });
        });

        btnImportModel.setOnClickListener(v -> {
            // Membuka pemilih berkas SAF Android dengan tipe */* agar berkas .task tidak terblokir
            speaker.speak("Membuka pengelola berkas. Pilih berkas model berakhiran titik task.");
            try {
                modelPickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                tvImportProgress.setVisibility(View.VISIBLE);
                tvImportProgress.setText("Gagal membuka pemilih berkas: " + e.getMessage());
                speaker.speak("Gagal membuka pemilih berkas di HP ini.");
            }
        });

        back.setOnClickListener(v -> finish());
    }

    @Override protected void onResume() {
        super.onResume();
        updateModelStatus();
    }

    @Override protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void updateModelStatus() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        File modelFile = engine.getModelFile();
        if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
            long mb = modelFile.length() / (1024 * 1024);
            tvModelStatus.setText("Status model: Terpasang (" + mb + " MB, " + LocalLlmEngine.MODEL_FILENAME + ")");
            tvModelStatus.setTextColor(0xFF00E676);
        } else {
            tvModelStatus.setText("Status model: Belum terpasang (Pilih berkas .task dari HP)");
            tvModelStatus.setTextColor(0xFFFFEB3B);
        }
    }

    private void processSelectedModelFile(Uri uri) {
        String fileName = queryFileName(uri);

        // Validasi ekstensi .task
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".task")) {
            speaker.speak("Berkas yang dipilih bukan berkas model titik task. Silakan pilih berkas yang berakhiran titik task.");
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFF5252);
            tvImportProgress.setText("Gagal: Berkas bukan bertipe .task (" + (!fileName.isEmpty() ? fileName : "format tidak dikenal") + ").");
            return;
        }

        long fileSize = queryFileSize(uri);
        final long finalFileSize = fileSize;

        speaker.speak("Memulai penyalinan model titik task dari penyimpanan HP. Harap tunggu sebentar.");
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setProgress(0);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFFF00);
        tvImportProgress.setText("Menyiapkan penyalinan: " + fileName);

        executor.execute(() -> {
            File filesDir = getFilesDir();
            File destFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME);
            File tempFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME + ".tmp");

            if (tempFile.exists()) {
                tempFile.delete();
            }

            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                if (is == null) {
                    throw new IOException("Tidak dapat membaca data berkas yang dipilih.");
                }

                byte[] buffer = new byte[65536]; // Buffer 64 KB untuk penyalinan cepat & hemat memori
                int read;
                long totalRead = 0;
                long lastUpdateTime = 0;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    totalRead += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 350) {
                        lastUpdateTime = now;
                        final int percent = finalFileSize > 0 ? (int) ((totalRead * 100) / finalFileSize) : -1;
                        final long mb = totalRead / (1024 * 1024);
                        mainHandler.post(() -> {
                            if (percent >= 0) {
                                pbImportModel.setIndeterminate(false);
                                pbImportModel.setProgress(percent);
                                tvImportProgress.setText("Menyalin: " + percent + "% (" + mb + " MB)");
                            } else {
                                pbImportModel.setIndeterminate(true);
                                tvImportProgress.setText("Menyalin: " + mb + " MB...");
                            }
                        });
                    }
                }
                fos.flush();

                // Hapus berkas tujuan lama terlebih dahulu untuk mencegah penumpukan ruang penyimpanan
                if (destFile.exists()) {
                    destFile.delete();
                }

                // Ganti nama berkas sementara menjadi nama resmi yang dibaca LocalLlmEngine
                if (!tempFile.renameTo(destFile)) {
                    copyFile(tempFile, destFile);
                    tempFile.delete();
                }

                mainHandler.post(() -> {
                    pbImportModel.setIndeterminate(true);
                    tvImportProgress.setText("Penyalinan selesai. Menginisialisasi mesin bahasa offline...");
                    speaker.speak("Penyalinan selesai. Menginisialisasi mesin AI offline...");
                });

                // Inisialisasi ulang LocalLlmEngine di background thread
                LocalLlmEngine engine = LocalLlmEngine.getInstance(SettingsActivity.this);
                boolean ready = engine.reloadEngine();

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    updateModelStatus();

                    if (ready) {
                        tvImportProgress.setTextColor(0xFF00E676);
                        tvImportProgress.setText("Model offline aktif dan siap digunakan.");
                        speaker.speak("Model AI offline berhasil diimpor dan siap digunakan.");
                    } else {
                        long mb = destFile.length() / (1024 * 1024);
                        tvImportProgress.setTextColor(0xFFFFEB3B);
                        tvImportProgress.setText("Model tersimpan (" + mb + " MB), tetapi mesin belum berhasil dimuat.");
                        speaker.speak("Berkas model tersimpan, tetapi gagal dimuat oleh mesin offline. Pastikan berkas model kompatibel.");
                    }
                });

            } catch (Exception e) {
                if (tempFile.exists()) tempFile.delete();
                final String err = e.getMessage() != null ? e.getMessage() : "Kesalahan sistem";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    tvImportProgress.setTextColor(0xFFFF5252);
                    tvImportProgress.setText("Gagal mengimpor model: " + err);
                    speaker.speak("Gagal mengimpor model offline. " + err);
                });
            }
        });
    }

    private String queryFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) {
                        result = cursor.getString(col);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null && uri.getPath() != null) {
            int cut = uri.getPath().lastIndexOf('/');
            result = cut != -1 ? uri.getPath().substring(cut + 1) : uri.getPath();
        }
        return result != null ? result : "";
    }

    private long queryFileSize(Uri uri) {
        long size = -1;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (col >= 0) {
                        size = cursor.getLong(col);
                    }
                }
            } catch (Exception ignored) {}
        }
        return size;
    }

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void saveFields() {
        config.setApiKey(etKey.getText().toString());
        config.setModel(etModel.getText().toString());
        config.setN8nUrl(etN8n.getText().toString());
    }
}
