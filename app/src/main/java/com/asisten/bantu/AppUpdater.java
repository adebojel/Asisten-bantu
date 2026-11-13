package com.asisten.bantu;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pemeriksa dan pengunduh pembaruan aplikasi mandiri (In-App OTA Updater)
 * melalui GitHub Releases API publik repositori Kobra OS.
 */
public final class AppUpdater {

    public static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/adebojel/Asisten-bantu/releases/latest";

    public interface CheckCallback {
        void onUpdateAvailable(ReleaseInfo info);
        void onUpToDate(String currentVersion, String latestTag);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onStarted(long downloadId, String fileName);
        void onCompleted(Uri apkUri, File apkFile);
        void onError(String message);
    }

    public static final class ReleaseInfo {
        public final String tagName;
        public final String title;
        public final String releaseNotesForSpeech;
        public final String apkDownloadUrl;
        public final String apkFileName;
        public final String htmlUrl;

        ReleaseInfo(String tagName, String title, String releaseNotesForSpeech,
                    String apkDownloadUrl, String apkFileName, String htmlUrl) {
            this.tagName = tagName;
            this.title = title;
            this.releaseNotesForSpeech = releaseNotesForSpeech;
            this.apkDownloadUrl = apkDownloadUrl;
            this.apkFileName = apkFileName;
            this.htmlUrl = htmlUrl;
        }
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver activeReceiver;
    private long activeDownloadId = -1L;

    public AppUpdater(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void checkLatestRelease(CheckCallback callback) {
        if (!NetworkState.isOnline(appContext)) {
            mainHandler.post(() -> callback.onError("Tidak ada koneksi internet. Sambungkan Wi-Fi atau data seluler untuk cek pembaruan."));
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(LATEST_RELEASE_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "KobraOS-Android");

                int code = conn.getResponseCode();
                if (code == 404) {
                    mainHandler.post(() -> callback.onError("Belum ada rilis resmi yang dipublikasikan di repositori GitHub."));
                    return;
                }
                if (code < 200 || code >= 300) {
                    mainHandler.post(() -> callback.onError("Server GitHub mengembalikan kode " + code + "."));
                    return;
                }

                StringBuilder raw = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        raw.append(line).append("\n");
                    }
                }

                JSONObject json = new JSONObject(raw.toString());
                String tagName = json.optString("tag_name", "").trim();
                String title = json.optString("name", tagName).trim();
                String body = cleanReleaseNotes(json.optString("body", ""));
                String htmlUrl = json.optString("html_url", "");

                String apkUrl = "";
                String apkName = "";
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        String n = asset.optString("name", "");
                        if (n.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            apkName = n;
                            break;
                        }
                    }
                }

                String currentVer = BuildConfig.VERSION_NAME;
                if (isNewerVersion(tagName, currentVer)) {
                    ReleaseInfo info = new ReleaseInfo(tagName, title, body, apkUrl, apkName, htmlUrl);
                    mainHandler.post(() -> callback.onUpdateAvailable(info));
                } else {
                    mainHandler.post(() -> callback.onUpToDate(currentVer, tagName));
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "kesalahan jaringan";
                mainHandler.post(() -> callback.onError("Gagal memeriksa pembaruan: " + msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void downloadAndInstall(ReleaseInfo info, DownloadCallback callback) {
        if (info == null || info.apkDownloadUrl == null || info.apkDownloadUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("Rilis " + (info != null ? info.tagName : "") + " belum melampirkan berkas APK."));
            return;
        }
        DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            mainHandler.post(() -> callback.onError("DownloadManager tidak tersedia di perangkat ini."));
            return;
        }

        String safeFileName = (info.apkFileName != null && !info.apkFileName.isEmpty())
                ? info.apkFileName.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "KobraOS-" + info.tagName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".apk";

        File dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            mainHandler.post(() -> callback.onError("Direktori unduhan perangkat tidak tersedia."));
            return;
        }
        File old = new File(dir, safeFileName);
        if (old.exists()) old.delete();
        final File targetFile = new File(dir, safeFileName);

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(info.apkDownloadUrl))
                .setTitle("Pembaruan Kobra OS " + info.tagName)
                .setDescription("Mengunduh paket pembaruan Kobra OS...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, safeFileName);

        unregisterReceiver();
        activeDownloadId = dm.enqueue(req);
        final long expectedId = activeDownloadId;

        activeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id != expectedId) return;
                unregisterReceiver();

                DownloadManager.Query q = new DownloadManager.Query().setFilterById(expectedId);
                try (Cursor c = dm.query(q)) {
                    if (c != null && c.moveToFirst()) {
                        int statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = statusIdx >= 0 ? c.getInt(statusIdx) : -1;
                        if (status == DownloadManager.STATUS_SUCCESSFUL && targetFile.exists()) {
                            try {
                                Uri contentUri = FileProvider.getUriForFile(
                                        appContext,
                                        appContext.getPackageName() + ".fileprovider",
                                        targetFile);
                                mainHandler.post(() -> callback.onCompleted(contentUri, targetFile));
                            } catch (Exception e) {
                                mainHandler.post(() -> callback.onError("Gagal menyiapkan installer FileProvider: " + e.getMessage()));
                            }
                            return;
                        }
                    }
                } catch (Exception ignored) {}
                mainHandler.post(() -> callback.onError("Unduhan APK gagal atau dibatalkan."));
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(appContext, activeReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        mainHandler.post(() -> callback.onStarted(expectedId, safeFileName));
    }

    public static boolean launchPackageInstaller(Context context, Uri apkContentUri) {
        if (context == null) return false;
        try {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                KobraScreenHelper.safeStartActivity(context, permIntent, null,
                        "Tidak dapat membuka izin instal aplikasi pada HP ini.");
                return false;
            }
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkContentUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            return KobraScreenHelper.safeStartActivity(context, install, null,
                    "Tidak dapat membuka pemasang paket APK.");
        } catch (Throwable t) {
            return false;
        }
    }

    public void unregisterReceiver() {
        if (activeReceiver != null) {
            try {
                appContext.unregisterReceiver(activeReceiver);
            } catch (Exception ignored) {}
            activeReceiver = null;
        }
    }

    public static boolean isNewerVersion(String candidateTag, String currentVersion) {
        List<Integer> cand = extractVersionParts(candidateTag);
        List<Integer> curr = extractVersionParts(currentVersion);
        int max = Math.max(cand.size(), curr.size());
        for (int i = 0; i < max; i++) {
            int c = i < cand.size() ? cand.get(i) : 0;
            int k = i < curr.size() ? curr.get(i) : 0;
            if (c > k) return true;
            if (c < k) return false;
        }
        return false;
    }

    private static List<Integer> extractVersionParts(String raw) {
        List<Integer> parts = new ArrayList<>();
        if (raw == null) return parts;
        String[] tokens = raw.replaceAll("[^0-9.]+", ".").split("\\.");
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            try {
                parts.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {}
        }
        return parts;
    }

    private static String cleanReleaseNotes(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "Pembaruan sistem dan peningkatan stabilitas Kobra OS.";
        }
        String clean = body.replaceAll("[#*`>_~]", " ")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        return clean.length() > 350 ? clean.substring(0, 350) + "..." : clean;
    }
}
