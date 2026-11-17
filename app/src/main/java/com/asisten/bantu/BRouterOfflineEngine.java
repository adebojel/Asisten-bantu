package com.asisten.bantu;

import android.content.Context;
import android.os.StatFs;
import android.content.res.AssetManager;
import btools.router.FormatJson;
import btools.router.OsmNodeNamed;
import btools.router.OsmTrack;
import btools.router.RoutingContext;
import btools.router.RoutingEngine;
import btools.router.RoutingParamCollector;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Embedded BRouter offline pedestrian engine.
 *
 * Call downloadSegmentsForBounds() while connected, then route() works from
 * the locally cached RD5 graph files without a network connection.
 * All blocking methods must run on a worker thread.
 */
public final class BRouterOfflineEngine {
    private static final String SEGMENT_BASE_URL = "https://brouter.de/brouter/segments4/";
    private static final long ROUTE_TIMEOUT_MS = 120_000L;
    private static final int MAX_SEGMENTS_PER_DOWNLOAD = 4;
    private static final long MAX_SEGMENT_BYTES = 150L * 1024L * 1024L;
    private static final long MAX_TOTAL_SEGMENT_BYTES = 250L * 1024L * 1024L;

    private final Context context;
    private final File baseDirectory;
    private final File profileDirectory;
    private final File segmentDirectory;
    private final File modesDirectory;

    public BRouterOfflineEngine(Context context) {
        this.context = context.getApplicationContext();
        this.baseDirectory = new File(this.context.getFilesDir(), "brouter");
        this.profileDirectory = new File(baseDirectory, "profiles2");
        this.segmentDirectory = new File(baseDirectory, "segments4");
        this.modesDirectory = new File(baseDirectory, "modes");
    }

    /** BRouter stores 5x5-degree RD5 segments named by their south-west corner. */
    public static String segmentFileName(double latitude, double longitude) {
        validateCoordinate(latitude, longitude);
        int west = (int) Math.floor(longitude / 5.0) * 5;
        int south = (int) Math.floor(latitude / 5.0) * 5;
        return (west < 0 ? "W" : "E") + Math.abs(west)
                + "_" + (south < 0 ? "S" : "N") + Math.abs(south) + ".rd5";
    }

    public static List<String> segmentNamesForBounds(
            double south, double west, double north, double east) {
        if (Double.isNaN(south) || Double.isNaN(west) || Double.isNaN(north) || Double.isNaN(east)
                || south < -90 || north > 90 || west < -180 || east > 180
                || south > north || west > east) {
            throw new IllegalArgumentException("Invalid geographic bounds");
        }
        int firstWest = (int) Math.floor(west / 5.0) * 5;
        int lastWest = (int) Math.floor(east / 5.0) * 5;
        int firstSouth = (int) Math.floor(south / 5.0) * 5;
        int lastSouth = (int) Math.floor(north / 5.0) * 5;
        long count = ((lastWest - firstWest) / 5L + 1L) * ((lastSouth - firstSouth) / 5L + 1L);
        if (count > MAX_SEGMENTS_PER_DOWNLOAD) {
            throw new IllegalArgumentException("Area too large; download at most "
                    + MAX_SEGMENTS_PER_DOWNLOAD + " BRouter segments at a time");
        }
        List<String> names = new ArrayList<>();
        for (int lat = firstSouth; lat <= lastSouth; lat += 5) {
            for (int lon = firstWest; lon <= lastWest; lon += 5) {
                names.add((lon < 0 ? "W" : "E") + Math.abs(lon)
                        + "_" + (lat < 0 ? "S" : "N") + Math.abs(lat) + ".rd5");
            }
        }
        return Collections.unmodifiableList(names);
    }

    /** Downloads and locally SHA-256-verifies all 5x5-degree OSM routing segments for a region. */
    public List<String> downloadSegmentsForBounds(
            double south, double west, double north, double east) throws IOException {
        ensureDirectories();
        List<String> names = segmentNamesForBounds(south, west, north, east);
        long cachedBytes = currentSegmentBytes();
        for (String name : names) {
            File target = new File(segmentDirectory, name);
            if (isSegmentCacheValid(target)) continue;
            long previousLength = target.isFile() ? target.length() : 0L;
            long remainingBudget = MAX_TOTAL_SEGMENT_BYTES - cachedBytes + previousLength;
            if (remainingBudget <= 0) {
                throw new IOException("Batas penyimpanan data rute offline 250 MB tercapai");
            }
            downloadSegment(name, target, Math.min(MAX_SEGMENT_BYTES, remainingBudget));
            cachedBytes = cachedBytes - previousLength + target.length();
        }
        return names;
    }

    /**
     * Returns required route segments that are not present or fail their local SHA-256 check.
     * This method performs no network requests and is safe to call before offline routing.
     */
    public List<String> getMissingSegmentsForBounds(
            double south, double west, double north, double east) throws IOException {
        ensureDirectories();
        List<String> missing = new ArrayList<>();
        for (String name : segmentNamesForBounds(south, west, north, east)) {
            if (!isSegmentCacheValid(new File(segmentDirectory, name))) missing.add(name);
        }
        return Collections.unmodifiableList(missing);
    }

    /** Returns BRouter GeoJSON. Required RD5 segments must already exist locally. */
    public String route(double startLatitude, double startLongitude,
                        double endLatitude, double endLongitude) throws IOException {
        validateCoordinate(startLatitude, startLongitude);
        validateCoordinate(endLatitude, endLongitude);
        ensureDirectories();
        ensureProfileAssets();

        double south = Math.min(startLatitude, endLatitude);
        double north = Math.max(startLatitude, endLatitude);
        double west = Math.min(startLongitude, endLongitude);
        double east = Math.max(startLongitude, endLongitude);
        List<String> required = segmentNamesForBounds(south, west, north, east);
        for (String name : required) {
            File segment = new File(segmentDirectory, name);
            if (!isSegmentCacheValid(segment)) {
                throw new IOException("Offline map segment belum tersedia: " + name
                        + ". Unduh wilayah saat internet tersedia terlebih dahulu.");
            }
        }

        File profile = new File(profileDirectory, "trekking.brf");
        File rawTrack = new File(modesDirectory, "trekking_rawtrack.dat");
        File rawArea = new File(baseDirectory, "rawAreaInfo.dat");

        RoutingContext routingContext = new RoutingContext();
        routingContext.localFunction = profile.getAbsolutePath();
        routingContext.profileTimestamp = profile.lastModified();
        routingContext.rawTrackPath = rawTrack.getAbsolutePath();
        routingContext.rawAreaPath = rawArea.getAbsolutePath();
        routingContext.outputFormat = "json";
        routingContext.memoryclass = 256;
        routingContext.turnInstructionMode = 1;
        routingContext.turnInstructionRoundabouts = true;
        routingContext.turnInstructionCatchingRange = 50.0;

        RoutingParamCollector collector = new RoutingParamCollector();
        List<OsmNodeNamed> waypoints = collector.readPositions(
                new double[]{startLongitude, endLongitude},
                new double[]{startLatitude, endLatitude});
        Map<String, String> params = new HashMap<>();
        params.put("profile", profile.getAbsolutePath());
        params.put("format", "json");
        collector.setParams(routingContext, waypoints, params);

        RoutingEngine engine = new RoutingEngine(
                null, null, segmentDirectory, waypoints, routingContext);
        engine.quite = true;
        engine.doRun(ROUTE_TIMEOUT_MS);

        if (engine.getErrorMessage() != null) {
            throw new IOException("BRouter: " + engine.getErrorMessage());
        }
        OsmTrack track = engine.getFoundTrack();
        if (track == null) throw new IOException("BRouter tidak menghasilkan rute pedestrian.");
        track.exportWaypoints = routingContext.exportWaypoints;
        track.exportCorrectedWaypoints = routingContext.exportCorrectedWaypoints;
        return new FormatJson(routingContext).format(track);
    }

    private void ensureDirectories() throws IOException {
        if (!profileDirectory.isDirectory() && !profileDirectory.mkdirs()) {
            throw new IOException("Tidak dapat membuat direktori profil BRouter");
        }
        if (!segmentDirectory.isDirectory() && !segmentDirectory.mkdirs()) {
            throw new IOException("Tidak dapat membuat direktori segmen BRouter");
        }
        if (!modesDirectory.isDirectory() && !modesDirectory.mkdirs()) {
            throw new IOException("Tidak dapat membuat direktori mode BRouter");
        }
    }

    private void ensureProfileAssets() throws IOException {
        copyAssetIfMissing("trekking.brf");
        copyAssetIfMissing("lookups.dat");
    }

    private void copyAssetIfMissing(String name) throws IOException {
        File target = new File(profileDirectory, name);
        if (target.isFile() && target.length() > 0) return;
        AssetManager assets = context.getAssets();
        File temporary = new File(profileDirectory, name + ".tmp");
        try (InputStream input = assets.open(name);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        } catch (IOException error) {
            temporary.delete();
            throw new IOException("Profil BRouter " + name + " tidak tersedia di APK", error);
        }
        if (target.exists() && !target.delete()) {
            temporary.delete();
            throw new IOException("Tidak dapat mengganti profil BRouter " + name);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Tidak dapat memasang profil BRouter " + name);
        }
    }

    private void downloadSegment(String name, File target, long maxAllowedBytes) throws IOException {
        File temporary = new File(segmentDirectory, name + ".download");
        HttpURLConnection connection = null;
        temporary.delete();
        try {
            URL url = new URL(SEGMENT_BASE_URL + name);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(180_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "KobraOS-OfflinePedestrianRouter");
            int response = connection.getResponseCode();
            if (response != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unduhan segmen " + name + " gagal: HTTP " + response);
            }
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("Unduhan segmen dialihkan keluar dari HTTPS");
            }
            long expectedLength = connection.getContentLengthLong();
            long allowedBytes = Math.min(MAX_SEGMENT_BYTES, maxAllowedBytes);
            if (expectedLength > allowedBytes) {
                throw new IOException("Segmen " + name + " melebihi batas ukuran per file");
            }
            StatFs storage = new StatFs(segmentDirectory.getAbsolutePath());
            long minimumFreeBytes = (expectedLength > 0 ? expectedLength : 16L * 1024L * 1024L)
                    + 16L * 1024L * 1024L;
            if (storage.getAvailableBytes() < minimumFreeBytes) {
                throw new IOException("Ruang penyimpanan tidak cukup untuk segmen " + name);
            }
            long written = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[256 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    written += count;
                    if (written > allowedBytes) throw new IOException("Segmen " + name + " melewati batas ukuran");
                    output.write(buffer, 0, count);
                }
            }
            if (written < 1024 || (expectedLength > 0 && written != expectedLength)) {
                throw new IOException("Ukuran segmen " + name + " tidak valid");
            }
            String digest = sha256(temporary);
            if (target.exists() && !target.delete()) throw new IOException("Tidak dapat mengganti segmen lama");
            if (!temporary.renameTo(target)) throw new IOException("Tidak dapat menyimpan segmen " + name);
            writeDigestFile(target, digest);
        } finally {
            if (connection != null) connection.disconnect();
            temporary.delete();
        }
    }

    private long currentSegmentBytes() {
        File[] files = segmentDirectory.listFiles((dir, name) -> name.endsWith(".rd5"));
        long total = 0L;
        if (files != null) for (File file : files) total += file.length();
        return total;
    }

    private boolean isSegmentCacheValid(File segment) throws IOException {
        if (!segment.isFile() || segment.length() < 1024) return false;
        File digestFile = new File(segment.getParentFile(), segment.getName() + ".sha256");
        if (!digestFile.isFile()) return false;
        String expected;
        try (FileInputStream input = new FileInputStream(digestFile)) {
            byte[] bytes = new byte[(int) Math.min(256, digestFile.length())];
            int count = input.read(bytes);
            expected = count <= 0 ? "" : new String(bytes, 0, count, java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        return expected.equalsIgnoreCase(sha256(segment));
    }

    private static void writeDigestFile(File segment, String digest) throws IOException {
        File digestFile = new File(segment.getParentFile(), segment.getName() + ".sha256");
        try (FileOutputStream output = new FileOutputStream(digestFile)) {
            output.write(digest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[256 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void validateCoordinate(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid latitude/longitude");
        }
    }
}
