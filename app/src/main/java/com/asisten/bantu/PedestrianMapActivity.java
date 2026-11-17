package com.asisten.bantu;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PedestrianMapActivity extends AppCompatActivity implements LocationListener {
    private static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    private static final int LOCATION_PERMISSION_REQUEST = 781;
    private static final long MAX_OFFLINE_TILE_COUNT = 3_000L;
    private static final long MAX_OFFLINE_RESOURCE_BYTES = 50L * 1024L * 1024L;

    private MapView mapView;
    private MapLibreMap map;
    private TextView tvStatus;
    private Button btnLocation, btnDownload, btnRoute, btnStop;
    private LocationManager locationManager;
    private Location currentLocation;
    private LatLng destination;
    private Marker destinationMarker;
    private Polyline routePolyline;
    private OfflineRegion activeOfflineRegion;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private BRouterOfflineEngine router;
    private Speaker speaker;
    private final List<RouteInstruction> routeInstructions = new ArrayList<>();
    private int nextInstructionIndex;
    private boolean routeActive;
    private boolean rerouting;
    private int routeGeneration;
    private double[] activeRouteLatitudes;
    private double[] activeRouteLongitudes;
    private final PedestrianRouteProgress.OffRouteTracker offRouteTracker =
            new PedestrianRouteProgress.OffRouteTracker();
    private boolean centeredOnLocation;
    private boolean destroyed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(getApplicationContext());
        setContentView(R.layout.activity_pedestrian_map);
        mapView = findViewById(R.id.pedestrianMapView);
        tvStatus = findViewById(R.id.tvPedestrianMapStatus);
        btnLocation = findViewById(R.id.btnPedestrianLocation);
        btnDownload = findViewById(R.id.btnPedestrianDownload);
        btnRoute = findViewById(R.id.btnPedestrianRoute);
        btnStop = findViewById(R.id.btnPedestrianStop);
        router = new BRouterOfflineEngine(this);
        speaker = new Speaker(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mapView.onCreate(savedInstanceState);

        btnLocation.setOnClickListener(v -> refreshLocation(true));
        btnDownload.setOnClickListener(v -> downloadOfflineArea());
        btnRoute.setOnClickListener(v -> createRoute());
        btnStop.setOnClickListener(v -> stopRoute());

        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(MAX_OFFLINE_RESOURCE_BYTES, null);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.setStyle(new Style.Builder().fromUri(STYLE_URL), style -> {
                map.addOnMapClickListener(point -> {
                    if (routeActive) stopRoute();
                    destination = point;
                    if (destinationMarker != null) map.removeMarker(destinationMarker);
                    destinationMarker = map.addMarker(new MarkerOptions()
                            .position(point).title("Tujuan jalan kaki"));
                    tvStatus.setText(String.format(Locale.US,
                            "Tujuan dipilih: %.5f, %.5f. Tekan Buat Rute.",
                            point.getLatitude(), point.getLongitude()));
                    return true;
                });
                map.setCameraPosition(new CameraPosition.Builder()
                        .target(new LatLng(-2.5, 118.0)).zoom(4.0).build());
                refreshLocation(false);
            });
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshLocation(boolean centerCamera) {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            tvStatus.setText("Izin lokasi diperlukan untuk memulai rute jalan kaki.");
            return;
        }
        try {
            Location best = lastKnownLocation();
            if (best != null) onLocationChanged(best);
            String provider = bestProvider();
            if (provider != null) {
                locationManager.requestSingleUpdate(provider, this, Looper.getMainLooper());
                if (centerCamera && currentLocation != null) centerOnCurrentLocation();
            } else {
                tvStatus.setText("GPS/lokasi belum tersedia. Aktifkan layanan lokasi.");
            }
        } catch (SecurityException error) {
            tvStatus.setText("Izin lokasi belum diberikan.");
        }
    }

    private Location lastKnownLocation() {
        Location best = null;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null) best = gps;
            if (network != null && (best == null || network.getTime() > best.getTime())) best = network;
        } catch (SecurityException ignored) { }
        return best;
    }

    private String bestProvider() {
        Criteria criteria = new Criteria();
        boolean preciseLocationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        // Do not request a fine-accuracy provider when the user chose approximate location.
        criteria.setAccuracy(preciseLocationGranted
                ? Criteria.ACCURACY_FINE : Criteria.ACCURACY_COARSE);
        return locationManager.getBestProvider(criteria, true);
    }

    private void centerOnCurrentLocation() {
        if (map == null || currentLocation == null) return;
        LatLng point = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        map.animateCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder().target(point).zoom(16.0).build()), 500);
        centeredOnLocation = true;
    }

    private void downloadOfflineArea() {
        if (map == null || !hasLocationPermission() || currentLocation == null) {
            tvStatus.setText("Tunggu lokasi GPS tersedia dan peta siap sebelum mengunduh area.");
            return;
        }
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        double south = bounds.getLatSouth();
        double west = bounds.getLonWest();
        double north = bounds.getLatNorth();
        double east = bounds.getLonEast();
        tvStatus.setText("Mengunduh data rute OSM dan peta offline. Tetap terhubung ke internet.");

        worker.execute(() -> {
            try {
                List<String> segments = router.downloadSegmentsForBounds(south, west, north, east);
                runOnUiThread(() -> {
                    if (!destroyed) tvStatus.setText("Data rute OSM tersimpan: "
                            + String.join(", ", segments) + ". Peta sedang diunduh.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!destroyed) tvStatus.setText("Unduhan data rute gagal: " + safeMessage(error));
                });
            }
        });
        downloadMapTiles(bounds);
    }

    private void downloadMapTiles(LatLngBounds bounds) {
        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                STYLE_URL, bounds, 12.0, 16.0,
                getResources().getDisplayMetrics().density);
        byte[] metadata = ("{\"name\":\"Kobra pedestrian area\",\"created\":"
                + System.currentTimeMillis() + "}").getBytes(StandardCharsets.UTF_8);
        OfflineManager.getInstance(this).createOfflineRegion(
                definition, metadata, new OfflineManager.CreateOfflineRegionCallback() {
                    @Override public void onCreate(OfflineRegion region) {
                        activeOfflineRegion = region;
                        region.setObserver(new OfflineRegion.OfflineRegionObserver() {
                            @Override public void onStatusChanged(OfflineRegionStatus status) {
                                long tiles = status.getCompletedTileCount();
                                long bytes = status.getCompletedResourceSize();
                                if (tiles > MAX_OFFLINE_TILE_COUNT || bytes > MAX_OFFLINE_RESOURCE_BYTES) {
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                                    runOnUiThread(() -> {
                                        if (!destroyed) tvStatus.setText("Batas area offline tercapai (maksimum "
                                                + MAX_OFFLINE_TILE_COUNT + " tile atau 50 MB). Perkecil area lalu unduh lagi.");
                                    });
                                    return;
                                }
                                if (status.isComplete()) {
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                                    runOnUiThread(() -> {
                                        if (!destroyed) tvStatus.setText("Peta offline siap. Data rute OSM disimpan terpisah.");
                                    });
                                } else {
                                    runOnUiThread(() -> {
                                        if (!destroyed) tvStatus.setText("Mengunduh peta offline: "
                                                + tiles + " tile, " + (bytes / (1024 * 1024)) + " MB.");
                                    });
                                }
                            }

                            @Override public void onError(OfflineRegionError error) {
                                runOnUiThread(() -> {
                                    if (!destroyed) tvStatus.setText("Unduhan peta offline gagal: " + error.getMessage());
                                });
                            }

                            @Override public void mapboxTileCountLimitExceeded(long limit) {
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                                runOnUiThread(() -> {
                                    if (!destroyed) tvStatus.setText("Batas tile penyedia peta tercapai: " + limit);
                                });
                            }
                        });
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE);
                    }

                    @Override public void onError(String error) {
                        tvStatus.setText("Tidak dapat membuat area peta offline: " + error);
                    }
                });
    }

    private boolean hasValidatedInternet() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network active = manager != null ? manager.getActiveNetwork() : null;
            NetworkCapabilities capabilities = manager != null && active != null
                    ? manager.getNetworkCapabilities(active) : null;
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureRouteSegmentsAvailable(Location start, LatLng end) throws Exception {
        List<String> missing = router.getMissingSegmentsForBounds(
                Math.min(start.getLatitude(), end.getLatitude()),
                Math.min(start.getLongitude(), end.getLongitude()),
                Math.max(start.getLatitude(), end.getLatitude()),
                Math.max(start.getLongitude(), end.getLongitude()));
        if (missing.isEmpty()) return;
        if (!hasValidatedInternet()) {
            throw new java.io.IOException("Segmen rute offline belum tersimpan: "
                    + String.join(", ", missing)
                    + ". Hubungkan internet sekali untuk mengunduh wilayah ini, lalu rute dapat dihitung offline.");
        }
        router.downloadSegmentsForBounds(
                Math.min(start.getLatitude(), end.getLatitude()),
                Math.min(start.getLongitude(), end.getLongitude()),
                Math.max(start.getLatitude(), end.getLatitude()),
                Math.max(start.getLongitude(), end.getLongitude()));
    }

    private void createRoute() {
        if (currentLocation == null) {
            tvStatus.setText("Lokasi awal belum tersedia. Tekan Lokasi Saya.");
            refreshLocation(false);
            return;
        }
        if (destination == null) {
            tvStatus.setText("Sentuh peta untuk memilih titik tujuan terlebih dahulu.");
            return;
        }
        final Location start = new Location(currentLocation);
        final LatLng end = destination;
        final int requestGeneration = ++routeGeneration;
        rerouting = false;
        tvStatus.setText("Menyiapkan segmen OSM offline dan menghitung rute...");
        worker.execute(() -> {
            try {
                ensureRouteSegmentsAvailable(start, end);
                String geoJson = router.route(start.getLatitude(), start.getLongitude(),
                        end.getLatitude(), end.getLongitude());
                RouteData data = parseRoute(geoJson);
                runOnUiThread(() -> {
                    if (destroyed || map == null || requestGeneration != routeGeneration) return;
                    installRouteData(data);
                    tvStatus.setText("Rute offline siap: " + data.distanceText
                            + " m. Estimasi " + data.durationText + " detik. Data OSM dapat berubah; tetap waspada.");
                    speaker.speak("Rute jalan kaki offline siap. Jarak sekitar "
                            + data.distanceText + " meter. Estimasi " + data.durationText
                            + " detik. Tetap perhatikan kondisi nyata dan gunakan alat bantu keselamatan.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!destroyed && requestGeneration == routeGeneration) {
                        rerouting = false;
                        tvStatus.setText("Rute offline belum tersedia: " + safeMessage(error)
                                + " Unduh area ketika internet tersedia.");
                    }
                });
            }
        });
    }

    private void installRouteData(RouteData data) {
        if (routePolyline != null) map.removePolyline(routePolyline);
        routePolyline = map.addPolyline(new PolylineOptions()
                .addAll(data.points).color(Color.CYAN).width(5f));
        activeRouteLatitudes = new double[data.points.size()];
        activeRouteLongitudes = new double[data.points.size()];
        for (int i = 0; i < data.points.size(); i++) {
            activeRouteLatitudes[i] = data.points.get(i).getLatitude();
            activeRouteLongitudes[i] = data.points.get(i).getLongitude();
        }
        routeInstructions.clear();
        routeInstructions.addAll(data.instructions);
        nextInstructionIndex = 0;
        offRouteTracker.reset();
        rerouting = false;
        routeActive = true;
        startLocationUpdates();
    }

    private void requestAutomaticReroute(Location location) {
        if (!routeActive || rerouting || destroyed || destination == null || location == null) return;
        rerouting = true;
        final int requestGeneration = ++routeGeneration;
        final Location start = new Location(location);
        final LatLng end = destination;
        tvStatus.setText("Lokasi menyimpang dari garis rute. Menghitung ulang rute offline...");
        speaker.speak("Anda tampaknya menyimpang dari rute. Saya sedang menghitung ulang rute offline.");
        worker.execute(() -> {
            try {
                ensureRouteSegmentsAvailable(start, end);
                RouteData data = parseRoute(router.route(start.getLatitude(), start.getLongitude(),
                        end.getLatitude(), end.getLongitude()));
                runOnUiThread(() -> {
                    if (destroyed || map == null || requestGeneration != routeGeneration) return;
                    installRouteData(data);
                    tvStatus.setText("Rute pedestrian diperbarui dari posisi terakhir. Tetap periksa kondisi sekitar.");
                    speaker.speak("Rute baru siap. Panduan dilanjutkan dari lokasi Anda sekarang. Tetap waspada.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed || requestGeneration != routeGeneration) return;
                    rerouting = false;
                    routeActive = false;
                    activeRouteLatitudes = null;
                    activeRouteLongitudes = null;
                    routeInstructions.clear();
                    stopLocationUpdates();
                    if (routePolyline != null) {
                        map.removePolyline(routePolyline);
                        routePolyline = null;
                    }
                    tvStatus.setText("Rute ulang gagal: " + safeMessage(error)
                            + ". Panduan dihentikan; pilih hitung rute baru saat data tersedia.");
                    speaker.speak("Rute ulang gagal. Panduan dihentikan agar tidak memberi arah yang keliru.");
                });
            }
        });
    }

    private RouteData parseRoute(String geoJson) throws Exception {
        JSONObject root = new JSONObject(geoJson);
        JSONArray features = root.getJSONArray("features");
        if (features.length() == 0) throw new IllegalStateException("GeoJSON rute kosong");
        JSONObject feature = features.getJSONObject(0);
        JSONObject geometry = feature.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray coordinate = coordinates.getJSONArray(i);
            points.add(new LatLng(coordinate.getDouble(1), coordinate.getDouble(0)));
        }
        if (points.size() < 2) throw new IllegalStateException("Geometri rute tidak cukup");
        JSONObject properties = feature.optJSONObject("properties");
        String distance = properties == null ? "?" : properties.optString("track-length", "?");
        String duration = properties == null ? "?" : properties.optString("total-time", "?");
        List<RouteInstruction> instructions = new ArrayList<>();
        JSONArray hints = properties == null ? null : properties.optJSONArray("voicehints");
        if (hints != null) {
            for (int i = 0; i < hints.length(); i++) {
                JSONArray hint = hints.optJSONArray(i);
                if (hint == null || hint.length() < 3) continue;
                int pointIndex = hint.optInt(0, -1);
                int command = hint.optInt(1, -1);
                int exitNumber = hint.optInt(2, 0);
                String text = BRouterVoiceInstruction.toIndonesian(command, exitNumber);
                if (text != null && pointIndex >= 0 && pointIndex < points.size()) {
                    instructions.add(new RouteInstruction(points.get(pointIndex), text));
                }
            }
        }
        return new RouteData(points, instructions, distance, duration);
    }

    private void updateTurnGuidance(Location location) {
        if (!routeActive || rerouting) return;
        // Do not announce turns or arrival from a fix with missing/poor accuracy.
        if (!PedestrianRouteProgress.hasReliableLocationAccuracy(
                location.hasAccuracy(), location.hasAccuracy() ? location.getAccuracy() : Float.NaN)) return;
        if (location.hasAccuracy() && activeRouteLatitudes != null && activeRouteLongitudes != null) {
            float routeDistance = PedestrianRouteProgress.distanceToPolylineMeters(
                    location.getLatitude(), location.getLongitude(),
                    activeRouteLatitudes, activeRouteLongitudes);
            if (offRouteTracker.shouldTriggerReroute(
                    routeDistance, location.getAccuracy(), SystemClock.elapsedRealtime())) {
                requestAutomaticReroute(location);
                return;
            }
        }
        while (nextInstructionIndex < routeInstructions.size()) {
            RouteInstruction instruction = routeInstructions.get(nextInstructionIndex);
            float[] result = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    instruction.point.getLatitude(), instruction.point.getLongitude(), result);
            float distance = result[0];
            if (distance <= 25f) {
                if (!instruction.nowAnnounced) {
                    speaker.speak("Sekarang " + instruction.text);
                    instruction.nowAnnounced = true;
                }
                nextInstructionIndex++;
            } else {
                if (distance <= 100f && !instruction.approachAnnounced) {
                    speaker.speak("Dalam sekitar " + (Math.round(distance / 10f) * 10)
                            + " meter, " + instruction.text);
                    instruction.approachAnnounced = true;
                }
                break;
            }
        }
        if (destination != null) {
            float[] result = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    destination.getLatitude(), destination.getLongitude(), result);
            if (PedestrianRouteProgress.isDestinationReached(result[0])) {
                speaker.speak("Anda sudah dekat dengan tujuan.");
                routeActive = false;
                stopLocationUpdates();
            }
        }
    }

    private void stopRoute() {
        routeGeneration++;
        rerouting = false;
        routeActive = false;
        activeRouteLatitudes = null;
        activeRouteLongitudes = null;
        offRouteTracker.reset();
        routeInstructions.clear();
        nextInstructionIndex = 0;
        stopLocationUpdates();
        if (map != null && routePolyline != null) {
            map.removePolyline(routePolyline);
            routePolyline = null;
        }
        if (speaker != null) speaker.speak("Panduan rute dihentikan.");
        tvStatus.setText("Panduan rute dihentikan.");
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;
        try {
            String provider = bestProvider();
            if (provider != null) locationManager.requestLocationUpdates(
                    provider, 2_000L, 3f, this, Looper.getMainLooper());
        } catch (SecurityException ignored) { }
    }

    private void stopLocationUpdates() {
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    @Override public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;
        if (map != null && !centeredOnLocation) centerOnCurrentLocation();
        updateTurnGuidance(location);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(@NonNull String provider) { refreshLocation(false); }
    @Override public void onProviderDisabled(@NonNull String provider) {
        tvStatus.setText("Penyedia lokasi dinonaktifkan: " + provider);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (hasLocationPermission()) refreshLocation(true);
            else tvStatus.setText("Izin lokasi ditolak. Rute tidak dapat dimulai.");
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static final class RouteInstruction {
        final LatLng point;
        final String text;
        boolean approachAnnounced;
        boolean nowAnnounced;
        RouteInstruction(LatLng point, String text) { this.point = point; this.text = text; }
    }

    private static final class RouteData {
        final List<LatLng> points;
        final List<RouteInstruction> instructions;
        final String distanceText;
        final String durationText;
        RouteData(List<LatLng> points, List<RouteInstruction> instructions,
                  String distanceText, String durationText) {
            this.points = points;
            this.instructions = instructions;
            this.distanceText = distanceText;
            this.durationText = durationText;
        }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (routeActive) startLocationUpdates();
    }
    @Override protected void onPause() {
        stopLocationUpdates();
        mapView.onPause();
        super.onPause();
    }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        routeGeneration++;
        rerouting = false;
        stopLocationUpdates();
        if (activeOfflineRegion != null) activeOfflineRegion.setObserver(null);
        worker.shutdownNow();
        if (speaker != null) speaker.shutdown();
        mapView.onDestroy();
        super.onDestroy();
    }
}
