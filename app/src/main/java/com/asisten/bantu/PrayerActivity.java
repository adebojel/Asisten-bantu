package com.asisten.bantu;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.util.Locale;

/**
 * Layar Jadwal Sholat 5 Waktu & Kompas Kiblat 100% Offline dengan isyarat nada audio
 * ketika arah hadap ponsel sejajar dengan Kiblat.
 */
public class PrayerActivity extends AppCompatActivity implements SensorEventListener {

    private static final double DEFAULT_LAT = -6.2088;   // Fallback Jakarta bila GPS belum pernah didapat
    private static final double DEFAULT_LON = 106.8456;

    private KobraConfig config;
    private Speaker speaker;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private ToneGenerator toneGenerator;

    private TextView tvCoords, tvPrayerTimes, tvQiblaInfo;
    private SwitchCompat swQiblaBeep;

    private double currentLat = DEFAULT_LAT;
    private double currentLon = DEFAULT_LON;
    private boolean usingFallback = true;
    private int currentHeading = -1;
    private int qiblaDeg = 295;
    private PrayerTimesHelper.PrayerTimes todayTimes;
    private long lastBeepAt = 0L;
    private boolean alignedAnnounced = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prayer);

        config = new KobraConfig(this);
        speaker = Speaker.get(this);
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
        } catch (Throwable t) {
            rotationSensor = null;
        }
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Throwable ignored) {}

        tvCoords = findViewById(R.id.tvPrayerCoords);
        tvPrayerTimes = findViewById(R.id.tvPrayerTimes);
        tvQiblaInfo = findViewById(R.id.tvQiblaInfo);
        swQiblaBeep = findViewById(R.id.swQiblaBeep);

        double savedLat = config.getSavedLat();
        double savedLon = config.getSavedLon();
        if (!Double.isNaN(savedLat) && !Double.isNaN(savedLon)) {
            currentLat = savedLat;
            currentLon = savedLon;
            usingFallback = false;
        }
        recomputePrayerAndQibla();

        findViewById(R.id.btnRefreshPrayerGps).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            fetchGpsAndUpdate(true);
        });

        findViewById(R.id.btnSpeakPrayer).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            if (todayTimes != null) {
                speaker.speak(todayTimes.toSpeechString());
            }
        });

        findViewById(R.id.btnSpeakQibla).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            speakQiblaGuidance();
        });

        findViewById(R.id.btnBackPrayer).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            finish();
        });

        fetchGpsAndUpdate(false);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screenHelper.handleKeyEvent(this, event, speaker)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (screenHelper.handleTouchEvent(this, ev, speaker)) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            if (sensorManager != null && rotationSensor != null) {
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
            }
        } catch (Throwable ignored) {}
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        } catch (Throwable ignored) {}
    }

    @Override protected void onDestroy() {
        if (toneGenerator != null) {
            try { toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
        super.onDestroy();
    }

    private void fetchGpsAndUpdate(boolean speakResult) {
        CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
            @Override public void onLocation(Location loc, String addressText) {
                currentLat = loc.getLatitude();
                currentLon = loc.getLongitude();
                usingFallback = false;
                config.setSavedLocation(currentLat, currentLon);
                recomputePrayerAndQibla();
                if (!addressText.isEmpty()) {
                    tvCoords.setText("Lokasi GPS: " + addressText);
                }
                if (speakResult) {
                    HapticFeedback.vibrateSuccess(PrayerActivity.this);
                    speaker.speak("Lokasi diperbarui. " + (todayTimes != null ? todayTimes.toSpeechString() : ""));
                }
            }

            @Override public void onError(String message) {
                if (speakResult) {
                    speaker.speak(message + " Menggunakan koordinat terakhir yang tersimpan.");
                }
            }
        });
    }

    private void recomputePrayerAndQibla() {
        todayTimes = PrayerTimesHelper.calculateToday(currentLat, currentLon);
        qiblaDeg = PrayerTimesHelper.computeQiblaBearing(currentLat, currentLon);
        tvCoords.setText(String.format(Locale.US,
                "%s: %.4f, %.4f | Sudut Kiblat: %d°",
                usingFallback ? "Koordinat default (Jakarta)" : "Koordinat GPS aktif",
                currentLat, currentLon, qiblaDeg));
        tvPrayerTimes.setText(
                "Subuh   : " + todayTimes.subuh + "\n"
                        + "Terbit  : " + todayTimes.terbit + "\n"
                        + "Dzuhur  : " + todayTimes.dzuhur + "\n"
                        + "Ashar   : " + todayTimes.ashar + "\n"
                        + "Maghrib : " + todayTimes.maghrib + "\n"
                        + "Isya    : " + todayTimes.isya);
        updateQiblaHeadingUi();
    }

    private void updateQiblaHeadingUi() {
        if (rotationSensor == null) {
            tvQiblaInfo.setText("Sensor kompas tidak tersedia.\nArah Kiblat: " + qiblaDeg + "° dari Utara.");
            return;
        }
        if (currentHeading < 0) {
            tvQiblaInfo.setText("Target Kiblat: " + qiblaDeg + "° (" + CompassLocationHelper.cardinalDirection(qiblaDeg) + ")");
            return;
        }
        int diff = signedAngleDiff(qiblaDeg, currentHeading);
        int absDiff = Math.abs(diff);
        if (absDiff <= 10) {
            tvQiblaInfo.setTextColor(0xFF00E676);
            tvQiblaInfo.setText("✅ TEPAT MENGHADAP KIBLAT\nHadap: " + currentHeading + "° | Kiblat: " + qiblaDeg + "°");
            if (!alignedAnnounced) {
                alignedAnnounced = true;
                HapticFeedback.vibrateSuccess(this);
            }
            maybePlayQiblaTone(true);
        } else {
            alignedAnnounced = false;
            tvQiblaInfo.setTextColor(0xFFFFFF00);
            String turn = diff > 0 ? "Putar ke kanan " + absDiff + "°" : "Putar ke kiri " + absDiff + "°";
            tvQiblaInfo.setText(turn + "\nHadap: " + currentHeading + "° | Kiblat: " + qiblaDeg + "°");
            if (absDiff <= 35) {
                maybePlayQiblaTone(false);
            }
        }
    }

    private void maybePlayQiblaTone(boolean exactAligned) {
        if (!swQiblaBeep.isChecked() || toneGenerator == null) return;
        long now = System.currentTimeMillis();
        long interval = exactAligned ? 320L : 800L;
        if (now - lastBeepAt >= interval) {
            lastBeepAt = now;
            try {
                toneGenerator.startTone(
                        exactAligned ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_BEEP,
                        exactAligned ? 140 : 80);
            } catch (Exception ignored) {}
        }
    }

    private void speakQiblaGuidance() {
        String targetText = "Arah kiblat dari lokasi Anda adalah " + qiblaDeg + " derajat, "
                + CompassLocationHelper.cardinalDirection(qiblaDeg) + ". ";
        if (currentHeading < 0) {
            speaker.speak(targetText + "Sensor kompas sedang mengkalibrasi atau tidak tersedia.");
            return;
        }
        int diff = signedAngleDiff(qiblaDeg, currentHeading);
        int absDiff = Math.abs(diff);
        if (absDiff <= 10) {
            speaker.speak(targetText + "Anda sudah tepat menghadap kiblat.");
        } else {
            String dir = diff > 0 ? "putar ke kanan " : "putar ke kiri ";
            speaker.speak(targetText + "Saat ini Anda menghadap " + currentHeading + " derajat. Silakan "
                    + dir + absDiff + " derajat.");
        }
    }

    private static int signedAngleDiff(int targetDeg, int currentDeg) {
        return ((targetDeg - currentDeg + 540) % 360) - 180;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        try {
            if (event == null || event.sensor == null
                    || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR
                    || event.values == null || event.values.length < 3) {
                return;
            }
            currentHeading = CompassLocationHelper.rotationVectorToDegrees(event.values);
            updateQiblaHeadingUi();
        } catch (Throwable ignored) {}
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
