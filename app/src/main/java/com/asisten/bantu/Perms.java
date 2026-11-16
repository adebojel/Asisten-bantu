package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime permissions grouped by the feature that needs them.
 *
 * The legacy required() method is retained for compatibility with older callers,
 * but SettingsActivity now requests one user-selected capability group at a time.
 */
public final class Perms {
    private Perms() {}

    public static final int GROUP_MICROPHONE = 0;
    public static final int GROUP_CAMERA = 1;
    public static final int GROUP_LOCATION = 2;
    public static final int GROUP_CONTACTS = 3;
    public static final int GROUP_CALLS = 4;
    public static final int GROUP_SMS = 5;
    public static final int GROUP_CALL_HISTORY = 6;
    public static final int GROUP_CALENDAR = 7;
    public static final int GROUP_BLUETOOTH = 8;
    public static final int GROUP_NOTIFICATIONS = 9;
    public static final int GROUP_ACTIVITY = 10;
    public static final int GROUP_MEDIA = 11;
    public static final int GROUP_NEARBY_WIFI = 12;
    public static final int GROUP_SMS_READ = 13;
    public static final int GROUP_ANSWER_CALLS = 14;

    public static String[] groupLabels() {
        return new String[] {
            "Mikrofon — perintah dan kontrol suara",
            "Kamera — pembaca objek dan teks",
            "Lokasi — navigasi dan posisi",
            "Kontak — mencari nomor kontak",
            "Telepon — melakukan/menjawab panggilan",
            "SMS — mengirim pesan",
            "Riwayat panggilan — membaca log telepon",
            "Kalender — membaca agenda",
            "Bluetooth — perangkat terdekat",
            "Notifikasi — status dan pengingat",
            "Aktivitas fisik — penghitung langkah",
            "Audio — memilih berkas suara",
            "Wi-Fi sekitar — pemindaian perangkat/jaringan",
            "Baca SMS — mengakses pesan masuk",
            "Angkat atau akhiri panggilan — kontrol panggilan masuk"
        };
    }

    /** Returns only the permissions for the capability the user selected. */
    public static String[] forGroup(int group) {
        List<String> permissions = new ArrayList<>();
        switch (group) {
            case GROUP_MICROPHONE:
                permissions.add(Manifest.permission.RECORD_AUDIO);
                break;
            case GROUP_CAMERA:
                permissions.add(Manifest.permission.CAMERA);
                break;
            case GROUP_LOCATION:
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                break;
            case GROUP_CONTACTS:
                permissions.add(Manifest.permission.READ_CONTACTS);
                break;
            case GROUP_CALLS:
                permissions.add(Manifest.permission.CALL_PHONE);
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS);
                permissions.add(Manifest.permission.READ_PHONE_STATE);
                break;
            case GROUP_SMS:
                permissions.add(Manifest.permission.SEND_SMS);
                break;
            case GROUP_CALL_HISTORY:
                permissions.add(Manifest.permission.READ_CALL_LOG);
                break;
            case GROUP_CALENDAR:
                permissions.add(Manifest.permission.READ_CALENDAR);
                break;
            case GROUP_BLUETOOTH:
                if (Build.VERSION.SDK_INT >= 31) {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
                    permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
                }
                break;
            case GROUP_NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= 33) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                }
                break;
            case GROUP_ACTIVITY:
                if (Build.VERSION.SDK_INT >= 29) {
                    permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
                }
                break;
            case GROUP_MEDIA:
                if (Build.VERSION.SDK_INT >= 33) {
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
                } else {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                break;
            case GROUP_NEARBY_WIFI:
                if (Build.VERSION.SDK_INT >= 33) {
                    permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                }
                break;
            case GROUP_SMS_READ:
                permissions.add(Manifest.permission.READ_SMS);
                break;
            case GROUP_ANSWER_CALLS:
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS);
                break;
            default:
                return new String[0];
        }
        return permissions.toArray(new String[0]);
    }

    /**
     * Minimal baseline permission set for new callers.
     * Optional capabilities must use forGroup() after the user selects that feature.
     */
    @Deprecated
    public static String[] required() {
        return forGroup(GROUP_MICROPHONE);
    }

    public static boolean has(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAll(Context c, String... permissions) {
        for (String p : permissions) if (!has(c, p)) return false;
        return true;
    }
}
