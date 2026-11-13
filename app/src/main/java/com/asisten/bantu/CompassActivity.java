package com.asisten.bantu;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

/**
 * Kompas Suara Offline (Sensor.TYPE_ROTATION_VECTOR), pembaca alamat GPS (LocationManager + Geocoder),
 * serta penyimpan dan pemandu arah jarum jam menuju lokasi favorit (Rumah, Masjid, dll.).
 */
public class CompassActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private TextView tvHeading, tvLocationResult, tvSavedPlaces;
    private EditText etPlaceLabel;
    private KobraConfig config;
    private Speaker speaker;
    private int currentHeading = -1;
    private String currentCardinal = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);

        config = new KobraConfig(this);
        speaker = Speaker.get(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        tvHeading = findViewById(R.id.tvCompassHeading);
        tvLocationResult = findViewById(R.id.tvLocationResult);
        tvSavedPlaces = findViewById(R.id.tvSavedPlaces);
        etPlaceLabel = findViewById(R.id.etPlaceLabel);

        if (rotationSensor == null) {
            tvHeading.setText("Sensor kompas tidak tersedia di HP ini.");
        }

        findViewById(R.id.btnSpeakCompass).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            if (currentHeading >= 0) {
                speaker.speak("Anda menghadap " + currentCardinal + ", " + currentHeading + " derajat.");
            } else {
                speaker.speak("Sensor kompas belum siap atau tidak tersedia di HP ini.");
            }
        });

        findViewById(R.id.btnWhereAmI).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            tvLocationResult.setText("Mencari koordinat GPS saat ini...");
            speaker.speak("Mencari lokasi Anda sekarang.");
            CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
                @Override public void onLocation(Location loc, String addressText) {
                    String msg;
                    if (!addressText.isEmpty()) {
                        msg = "Anda berada di sekitar " + addressText + ". Akurasi " + Math.round(loc.getAccuracy()) + " meter.";
                    } else {
                        msg = String.format(Locale.US, "Koordinat Anda: lintang %.5f, bujur %.5f.", loc.getLatitude(), loc.getLongitude());
                    }
                    tvLocationResult.setText(msg);
                    HapticFeedback.vibrateSuccess(CompassActivity.this);
                    speaker.speak(msg);
                }
                @Override public void onError(String message) {
                    tvLocationResult.setText(message);
                    HapticFeedback.vibrateWarning(CompassActivity.this);
                    speaker.speak(message);
                }
            });
        });

        findViewById(R.id.btnSavePlace).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String label = etPlaceLabel.getText().toString().trim();
            if (label.isEmpty()) label = "Rumah";
            final String placeName = label;
            tvLocationResult.setText("Menyimpan koordinat untuk " + placeName + "...");
            CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
                @Override public void onLocation(Location loc, String addressText) {
                    config.saveFavoritePlace(placeName, loc.getLatitude(), loc.getLongitude());
                    refreshSavedPlaces();
                    String msg = "Lokasi " + placeName + " berhasil disimpan."
                            + (addressText.isEmpty() ? "" : " Sekitar " + addressText + ".");
                    tvLocationResult.setText(msg);
                    HapticFeedback.vibrateSuccess(CompassActivity.this);
                    speaker.speak(msg);
                }
                @Override public void onError(String message) {
                    tvLocationResult.setText(message);
                    HapticFeedback.vibrateWarning(CompassActivity.this);
                    speaker.speak(message);
                }
            });
        });

        findViewById(R.id.btnGuidePlace).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String label = etPlaceLabel.getText().toString().trim();
            if (label.isEmpty()) label = "Rumah";
            double[] coords = config.getFavoritePlace(label);
            if (coords == null) {
                String msg = "Lokasi " + label + " belum disimpan. Simpan koordinat dulu di sini atau lewat suara.";
                tvLocationResult.setText(msg);
                speaker.speak(msg);
                return;
            }
            String resolvedLabel = config.getFavoritePlaceLabel(label);
            tvLocationResult.setText("Menghitung jarak dan arah ke " + resolvedLabel + "...");
            CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
                @Override public void onLocation(Location loc, String addressText) {
                    String guide = CompassLocationHelper.buildRouteGuidance(
                            resolvedLabel, loc, coords[0], coords[1], currentHeading);
                    tvLocationResult.setText(guide);
                    HapticFeedback.vibrateSuccess(CompassActivity.this);
                    speaker.speak(guide);
                }
                @Override public void onError(String message) {
                    tvLocationResult.setText(message);
                    HapticFeedback.vibrateWarning(CompassActivity.this);
                    speaker.speak(message);
                }
            });
        });

        findViewById(R.id.btnBackCompass).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            finish();
        });

        refreshSavedPlaces();
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        refreshSavedPlaces();
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        currentHeading = CompassLocationHelper.rotationVectorToDegrees(event.values);
        currentCardinal = CompassLocationHelper.cardinalDirection(currentHeading);
        tvHeading.setText(currentCardinal + " (" + currentHeading + "°)");
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void refreshSavedPlaces() {
        List<String> places = config.listFavoritePlaceLabels();
        if (places.isEmpty()) {
            tvSavedPlaces.setText("Lokasi tersimpan: belum ada");
        } else {
            tvSavedPlaces.setText("Lokasi tersimpan: " + String.join(", ", places));
        }
    }
}
