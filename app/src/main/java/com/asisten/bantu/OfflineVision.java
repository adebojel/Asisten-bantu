package com.asisten.bantu;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fitur Mata Kedua offline: warna & kecocokan padu padan pakaian, hitung wajah,
 * serta deteksi benda dengan panduan arah kiri/tengah/kanan dan kedekatan.
 */
public final class OfflineVision {

    public interface Result { void onResult(String text); }

    public static final class ObjectGuidance {
        public final String label;
        public final String direction;
        public final double closeness;
        public final String spokenText;

        ObjectGuidance(String label, String direction, double closeness, String spokenText) {
            this.label = label;
            this.direction = direction;
            this.closeness = closeness;
            this.spokenText = spokenText;
        }
    }

    private static FaceDetector faceDetector;
    private static ObjectDetector objectDetector;

    private OfflineVision() {}

    /** Mengambil warna rata-rata dari area tengah gambar dan memberi nama warna dalam Bahasa Indonesia. */
    public static String colorName(Bitmap bmp) {
        if (bmp == null) return "tidak diketahui";
        int w = bmp.getWidth(), h = bmp.getHeight();
        return sampleRegionColor(bmp, w / 3, w - w / 3, h / 3, h - h / 3, false);
    }

    /** Mengambil nama warna yang lebih rinci (termasuk biru tua, krem, marun, zaitun) untuk pakaian. */
    public static String detailedColorName(Bitmap bmp) {
        if (bmp == null) return "tidak diketahui";
        int w = bmp.getWidth(), h = bmp.getHeight();
        return sampleRegionColor(bmp, w / 3, w - w / 3, h / 3, h - h / 3, true);
    }

    /** Menilai kecocokan padu padan warna atasan dan bawahan dari satu foto pakaian penuh. */
    public static String analyzeOutfitHarmony(Bitmap bmp) {
        if (bmp == null) return "Foto pakaian tidak dapat dibaca.";
        int w = bmp.getWidth(), h = bmp.getHeight();
        String top = sampleRegionColor(bmp, w / 3, w - w / 3, (int) (h * 0.16), (int) (h * 0.42), true);
        String bottom = sampleRegionColor(bmp, w / 3, w - w / 3, (int) (h * 0.56), (int) (h * 0.84), true);
        return "Atasan terdeteksi berwarna " + top + ", bawahan berwarna " + bottom + ". "
                + evaluateOutfitMatch(top, bottom);
    }

    /** Menilai kecocokan padu padan dua warna pakaian yang diberikan. */
    public static String evaluateOutfitMatch(String colorA, String colorB) {
        if (colorA == null || colorB == null) return "Warna belum lengkap untuk dibandingkan.";
        String a = colorA.toLowerCase();
        String b = colorB.toLowerCase();
        if (a.equals(b)) {
            return "Keduanya berwarna " + a + " senada, tampak rapi dan aman dipadukan.";
        }
        if (isNeutral(a) || isNeutral(b)) {
            return "Padu padan ini sangat cocok dan serasi karena warna netral mudah dipadukan dengan warna apa saja.";
        }
        if ((a.contains("biru") && b.contains("cokelat")) || (b.contains("biru") && a.contains("cokelat"))
                || (a.contains("hijau") && b.contains("cokelat")) || (b.contains("hijau") && a.contains("cokelat"))
                || (a.contains("marun") && b.contains("krem")) || (b.contains("marun") && a.contains("krem"))
                || (a.contains("biru") && b.contains("merah muda")) || (b.contains("biru") && a.contains("merah muda"))) {
            return "Kombinasi warna ini cukup serasi dan enak dipandang.";
        }
        if ((a.contains("merah") && b.contains("hijau")) || (b.contains("merah") && a.contains("hijau"))
                || (a.contains("oranye") && b.contains("ungu")) || (b.contains("oranye") && a.contains("ungu"))
                || (a.contains("kuning") && b.contains("merah muda")) || (b.contains("kuning") && a.contains("merah muda"))) {
            return "Perhatian, kedua warna ini cukup bertabrakan dan mencolok. Disarankan memadukan salah satunya dengan warna netral seperti hitam, putih, abu-abu, atau krem.";
        }
        return "Kedua warna ini cukup kontras. Jika ingin tampil lebih kalem dan pasti cocok, padukan salah satunya dengan celana atau baju warna hitam, putih, abu-abu, atau biru tua.";
    }

    private static boolean isNeutral(String c) {
        return c.equals("hitam") || c.equals("putih") || c.equals("abu-abu")
                || c.equals("krem") || c.equals("biru tua") || c.equals("cokelat");
    }

    private static String sampleRegionColor(Bitmap bmp, int x0, int x1, int y0, int y1, boolean detailed) {
        long r = 0, g = 0, b = 0;
        int count = 0;
        for (int y = Math.max(0, y0); y < Math.min(bmp.getHeight(), y1); y += 4) {
            for (int x = Math.max(0, x0); x < Math.min(bmp.getWidth(), x1); x += 4) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                count++;
            }
        }
        if (count == 0) return "tidak diketahui";
        int rr = (int) (r / count), gg = (int) (g / count), bb = (int) (b / count);
        return detailed ? classifyDetailed(rr, gg, bb) : classify(rr, gg, bb);
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

    static String classifyDetailed(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBToHSV(r, g, b, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (v < 0.16f) return "hitam";
        if (s < 0.14f) return v > 0.84f ? "putih" : "abu-abu";
        if (h >= 25 && h < 55 && s < 0.40f && v > 0.68f) return "krem";
        if (h < 15 || h >= 345) return v < 0.45f ? "merah marun" : "merah";
        if (h < 45) return v < 0.65f ? "cokelat" : "oranye";
        if (h < 70) return "kuning";
        if (h < 170) return v < 0.42f ? "hijau zaitun" : "hijau";
        if (h < 260) return v < 0.42f ? "biru tua" : "biru";
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

    /** Mengenali kategori benda besar di gambar (offline). */
    public static void describe(Bitmap bmp, Result cb) {
        if (bmp == null) { cb.onResult("Foto tidak bisa dibaca."); return; }
        if (objectDetector == null) {
            objectDetector = ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
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

    /** Menentukan benda terdekat beserta arah posisi (kiri/tengah/kanan) untuk panduan bunyi bip kamera. */
    public static ObjectGuidance findNearestObject(List<DetectedObject> objs, int imgWidth, int imgHeight) {
        if (objs == null || objs.isEmpty() || imgWidth <= 0 || imgHeight <= 0) return null;
        DetectedObject best = null;
        double bestRatio = 0.0;
        for (DetectedObject o : objs) {
            Rect box = o.getBoundingBox();
            double areaRatio = (double) (box.width() * box.height()) / (double) (imgWidth * imgHeight);
            if (areaRatio > bestRatio) {
                bestRatio = areaRatio;
                best = o;
            }
        }
        if (best == null) return null;
        Rect box = best.getBoundingBox();
        double centerX = (box.left + box.right) / 2.0 / imgWidth;
        String dir = centerX < 0.36 ? "sebelah kiri" : (centerX > 0.64 ? "sebelah kanan" : "lurus di tengah");
        String label = best.getLabels().isEmpty()
                ? "sebuah benda"
                : translateForSearch(best.getLabels().get(0).getText());
        String dist = bestRatio >= 0.25 ? "sangat dekat" : (bestRatio >= 0.10 ? "jarak sedang" : "agak jauh");
        String spoken = label + " terdeteksi di " + dir + ", " + dist + ".";
        return new ObjectGuidance(label, dir, Math.min(1.0, bestRatio * 2.5), spoken);
    }

    static String translate(String label) {
        switch (label) {
            case "Home goods": return "barang rumah tangga atau perabot";
            case "Fashion goods": return "pakaian atau aksesoris";
            case "Food": return "makanan atau minuman";
            case "Places": return "area ruangan atau pintu";
            case "Plants": return "tanaman";
            default: return "sebuah benda";
        }
    }

    private static String translateForSearch(String label) {
        switch (label) {
            case "Home goods": return "Perabot atau kursi/meja";
            case "Fashion goods": return "Pakaian atau tas";
            case "Food": return "Makanan atau minuman";
            case "Places": return "Area pintu atau lorong";
            case "Plants": return "Tanaman";
            default: return "Benda";
        }
    }
}
