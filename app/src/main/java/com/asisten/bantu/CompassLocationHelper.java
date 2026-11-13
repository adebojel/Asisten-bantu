package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.Locale;

/**
 * Pembantu kompas suara offline (Sensor.TYPE_ROTATION_VECTOR), penentuan alamat GPS (Geocoder),
 * serta panduan jarak dan arah jarum jam menuju lokasi favorit (Rumah, Masjid, dll.).
 */
public final class CompassLocationHelper {

    public interface HeadingCallback {
        void onHeading(int degrees, String cardinalName);
        void onUnavailable();
    }

    public interface LocationCallback {
        void onLocation(Location location, String addressText);
        void onError(String message);
    }

    private CompassLocationHelper() {}

    public static String cardinalDirection(int deg) {
        int d = ((deg % 360) + 360) % 360;
        if (d >= 338 || d < 23) return "Utara";
        if (d < 68) return "Timur Laut";
        if (d < 113) return "Timur";
        if (d < 158) return "Tenggara";
        if (d < 203) return "Selatan";
        if (d < 248) return "Barat Daya";
        if (d < 293) return "Barat";
        return "Barat Laut";
    }

    /** Mengonversi sudut relatif (-180..180 atau 0..360) menjadi arah jarum jam 1..12. */
    public static int toClockHour(float relativeBearingDeg) {
        float norm = ((relativeBearingDeg % 360f) + 360f) % 360f;
        int hour = Math.round(norm / 30f);
        if (hour == 0 || hour == 12) return 12;
        return hour;
    }

    /** Membaca kompas sekali lewat Sensor.TYPE_ROTATION_VECTOR secara offline. */
    public static void readHeadingOnce(Context context, HeadingCallback callback) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) {
            callback.onUnavailable();
            return;
        }
        Sensor rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rot == null) {
            callback.onUnavailable();
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] delivered = {false};
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                if (delivered[0] || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
                delivered[0] = true;
                sm.unregisterListener(this);
                int deg = rotationVectorToDegrees(event.values);
                callback.onHeading(deg, cardinalDirection(deg));
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(listener, rot, SensorManager.SENSOR_DELAY_UI);
        main.postDelayed(() -> {
            if (!delivered[0]) {
                delivered[0] = true;
                sm.unregisterListener(listener);
                callback.onUnavailable();
            }
        }, 1800L);
    }

    public static int rotationVectorToDegrees(float[] values) {
        float[] rotationMatrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values);
        SensorManager.getOrientation(rotationMatrix, orientation);
        int deg = (int) Math.round(Math.toDegrees(orientation[0]));
        return ((deg % 360) + 360) % 360;
    }

    /** Mengambil lokasi terbaik (atau permintaan GPS aktif) dan menerjemahkan ke alamat via Geocoder. */
    public static void fetchCurrentLocation(Context context, LocationCallback callback) {
        if (!Perms.has(context, Manifest.permission.ACCESS_FINE_LOCATION)
                && !Perms.has(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            callback.onError("Izin lokasi belum aktif. Buka Kobra, lalu tekan Beri Izin.");
            return;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            callback.onError("Layanan lokasi tidak tersedia di HP ini.");
            return;
        }

        Location best = getBestLastKnown(lm);
        if (best != null && (System.currentTimeMillis() - best.getTime() < 90_000L)) {
            resolveAddressAsync(context, best, callback);
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        LocationListener oneShot = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (done[0]) return;
                done[0] = true;
                try { lm.removeUpdates(this); } catch (SecurityException ignored) {}
                resolveAddressAsync(context, location, callback);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        boolean requested = false;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, oneShot, Looper.getMainLooper());
                requested = true;
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, oneShot, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException ignored) {}

        if (!requested) {
            if (best != null) resolveAddressAsync(context, best, callback);
            else callback.onError("GPS atau lokasi HP belum dinyalakan.");
            return;
        }

        main.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            try { lm.removeUpdates(oneShot); } catch (SecurityException ignored) {}
            Location fallback = getBestLastKnown(lm);
            if (fallback != null) resolveAddressAsync(context, fallback, callback);
            else callback.onError("Sinyal GPS belum ditemukan. Coba di area yang lebih terbuka.");
        }, 5000L);
    }

    public static Location getBestLastKnown(LocationManager lm) {
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) {}
        }
        return best;
    }

    private static void resolveAddressAsync(Context context, Location loc, LocationCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String addrText = "";
            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(context.getApplicationContext(), new Locale("id", "ID"));
                    List<Address> list = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                    if (list != null && !list.isEmpty()) {
                        Address a = list.get(0);
                        StringBuilder sb = new StringBuilder();
                        if (a.getThoroughfare() != null) sb.append(a.getThoroughfare()).append(", ");
                        if (a.getSubLocality() != null) sb.append(a.getSubLocality()).append(", ");
                        if (a.getLocality() != null) sb.append(a.getLocality()).append(", ");
                        if (a.getSubAdminArea() != null) sb.append(a.getSubAdminArea());
                        addrText = sb.toString().replaceAll(", $", "").trim();
                        if (addrText.isEmpty() && a.getAddressLine(0) != null) {
                            addrText = a.getAddressLine(0);
                        }
                    }
                } catch (Exception ignored) {}
            }
            final String finalAddr = addrText;
            main.post(() -> callback.onLocation(loc, finalAddr));
        }).start();
    }

    /** Menghitung panduan jarak meter dan arah jarum jam menuju koordinat tujuan. */
    public static String buildRouteGuidance(String placeLabel, Location current, double targetLat, double targetLon, int currentHeadingDeg) {
        Location dest = new Location("kobra_fav");
        dest.setLatitude(targetLat);
        dest.setLongitude(targetLon);

        long meters = Math.round(current.distanceTo(dest));
        float targetBearing = current.bearingTo(dest); // -180..180 from North
        int targetDeg = Math.round(((targetBearing % 360f) + 360f) % 360f);
        String cardinal = cardinalDirection(targetDeg);

        if (meters <= 15) {
            return "Anda sudah sangat dekat dengan " + placeLabel + ", berjarak sekitar " + meters + " meter.";
        }

        if (currentHeadingDeg >= 0) {
            float rel = targetBearing - currentHeadingDeg;
            int clockHour = toClockHour(rel);
            return placeLabel + " berjarak " + meters + " meter, di arah jam " + clockHour
                    + " Anda (arah " + cardinal + ").";
        }
        return placeLabel + " berjarak " + meters + " meter ke arah " + cardinal + " (" + targetDeg + " derajat).";
    }
}
