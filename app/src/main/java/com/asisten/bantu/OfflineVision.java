package com.asisten.bantu;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fitur yang berjalan sepenuhnya di HP tanpa internet: warna, hitung wajah, dan kenali benda besar. */
public final class OfflineVision {

    public interface Result { void onResult(String text); }

    private static FaceDetector faceDetector;
    private static ObjectDetector objectDetector;

    private OfflineVision() {}

    /** Mengambil warna rata-rata dari area tengah gambar dan memberi nama warna dalam Bahasa Indonesia. */
    public static String colorName(Bitmap bmp) {
        if (bmp == null) return "tidak diketahui";
        int w = bmp.getWidth(), h = bmp.getHeight();
        int x0 = w / 3, x1 = w - w / 3, y0 = h / 3, y1 = h - h / 3;
        long r = 0, g = 0, b = 0;
        int count = 0;
        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                count++;
            }
        }
        if (count == 0) return "tidak diketahui";
        return classify((int) (r / count), (int) (g / count), (int) (b / count));
    }

    static String classify(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBToHSV(r, g, b, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (v < 0.18f) return "hitam";
        if (s < 0.12f) return v > 0.85f ? "putih" : "abu-abu";
        if (h < 15 || h >= 345) return "merah";
        if (h < 45) return v < 0.7f ? "cokelat" : "oranye";
        if (h < 70) return "kuning";
        if (h < 170) return "hijau";
        if (h < 260) return "biru";
        if (h < 300) return "ungu";
        return "merah muda";
    }

    /** Menghitung wajah di gambar (offline, model bawaan ML Kit). */
    public static void countFaces(Bitmap bmp, Result cb) {
        if (bmp == null) { cb.onResult("Foto tidak bisa dibaca."); return; }
        if (faceDetector == null) {
            faceDetector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build());
        }
        faceDetector.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(faces -> {
                    int n = faces.size();
                    if (n == 0) cb.onResult("Tidak ada wajah yang terlihat.");
                    else if (n == 1) cb.onResult("Ada satu wajah di depan kamera.");
                    else cb.onResult("Ada " + n + " wajah di depan kamera.");
                })
                .addOnFailureListener(e -> cb.onResult("Gagal menghitung wajah."));
    }

    /** Mengenali kategori benda besar di gambar (offline). Hasilnya kategori umum, bukan nama pasti. */
    public static void describe(Bitmap bmp, Result cb) {
        if (bmp == null) { cb.onResult("Foto tidak bisa dibaca."); return; }
        if (objectDetector == null) {
            objectDetector = ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableClassification()
                    .build());
        }
        objectDetector.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(objs -> {
                    Set<String> names = new LinkedHashSet<>();
                    for (DetectedObject o : objs) {
                        if (!o.getLabels().isEmpty()) names.add(translate(o.getLabels().get(0).getText()));
                    }
                    if (names.isEmpty()) {
                        cb.onResult("Saya belum mengenali benda di depan kamera. Coba arahkan lebih dekat.");
                    } else {
                        cb.onResult("Di depan kamera saya melihat: " + String.join(", ", names) + ".");
                    }
                })
                .addOnFailureListener(e -> cb.onResult("Gagal mengenali benda di depan kamera."));
    }

    private static String translate(String label) {
        switch (label) {
            case "Home goods": return "barang rumah tangga";
            case "Fashion goods": return "pakaian atau aksesoris";
            case "Food": return "makanan";
            case "Places": return "suatu tempat";
            case "Plants": return "tanaman";
            default: return "sebuah benda";
        }
    }
}
