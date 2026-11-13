package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import java.util.Locale;

/**
 * Pemantau kecepatan dan jarak rute perjalanan GPS secara real-time untuk tunanetra.
 */
public final class TripMonitorHelper {

    public interface Callback {
        void onAnnouncement(String message);
    }

    private final Context context;
    private LocationManager locationManager;
    private Callback callback;
    private boolean active = false;
    private Location lastLocation;
    private double totalDistanceMeters = 0.0;
    private long startedAtMs = 0L;
    private float currentSpeedKmh = 0f;
    private double lastAnnouncedDistance = 0.0;
    private boolean warnedHighSpeed = false;

    public TripMonitorHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isActive() {
        return active;
    }

    public String start(Callback cb) {
        if (!Perms.has(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "Izin lokasi belum aktif. Buka Kobra, lalu tekan Beri Izin.";
        }
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return "Layanan GPS tidak tersedia di HP ini.";

        stop(false);
        this.callback = cb;
        this.active = true;
        this.totalDistanceMeters = 0.0;
        this.lastAnnouncedDistance = 0.0;
        this.currentSpeedKmh = 0f;
        this.warnedHighSpeed = false;
        this.lastLocation = CompassLocationHelper.getBestLastKnown(locationManager);
        this.startedAtMs = System.currentTimeMillis();

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, listener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 10f, listener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            active = false;
            return "Gagal memulai pemantau GPS karena izin lokasi ditolak.";
        }
        return "Pemantau perjalanan dan kecepatan GPS dimulai. Saya akan melaporkan jarak dan kecepatan Anda secara berkala.";
    }

    public String stop(boolean buildSummary) {
        active = false;
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (SecurityException ignored) {}
        }
        if (!buildSummary) return "";
        return "Pemantau perjalanan dihentikan. " + statusSummary();
    }

    public String statusSummary() {
        long minutes = Math.max(0, (System.currentTimeMillis() - startedAtMs) / 60000L);
        String distText = totalDistanceMeters >= 1000
                ? String.format(new Locale("id", "ID"), "%.2f kilometer", totalDistanceMeters / 1000.0)
                : Math.round(totalDistanceMeters) + " meter";
        return String.format(new Locale("id", "ID"),
                "Kecepatan saat ini sekitar %.0f kilometer per jam. Total jarak tempuh %s dalam waktu %d menit.",
                currentSpeedKmh, distText, minutes);
    }

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (!active || location == null) return;
            if (location.hasSpeed()) {
                currentSpeedKmh = location.getSpeed() * 3.6f;
            }
            if (lastLocation != null) {
                float delta = lastLocation.distanceTo(location);
                if (delta >= 3f && delta <= 500f) {
                    totalDistanceMeters += delta;
                    if (!location.hasSpeed()) {
                        long dtSec = Math.max(1L, (location.getTime() - lastLocation.getTime()) / 1000L);
                        currentSpeedKmh = (delta / dtSec) * 3.6f;
                    }
                }
            }
            lastLocation = location;

            if (callback != null) {
                if (currentSpeedKmh > 80f && !warnedHighSpeed) {
                    warnedHighSpeed = true;
                    callback.onAnnouncement(String.format(new Locale("id", "ID"),
                            "Kendaraan melaju kencang, kecepatan mencapai %.0f kilometer per jam.", currentSpeedKmh));
                } else if (currentSpeedKmh < 60f) {
                    warnedHighSpeed = false;
                }
                if (totalDistanceMeters - lastAnnouncedDistance >= 500.0) {
                    lastAnnouncedDistance = totalDistanceMeters;
                    callback.onAnnouncement(statusSummary());
                }
            }
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };
}
