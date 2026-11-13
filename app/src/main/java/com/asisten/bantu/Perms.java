package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public final class Perms {
    private Perms() {}

    public static String[] required() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        p.add(Manifest.permission.CAMERA);
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.READ_CONTACTS);
        p.add(Manifest.permission.CALL_PHONE);
        p.add(Manifest.permission.SEND_SMS);
        p.add(Manifest.permission.READ_SMS);
        p.add(Manifest.permission.ANSWER_PHONE_CALLS);
        p.add(Manifest.permission.READ_PHONE_STATE);
        p.add(Manifest.permission.READ_CALL_LOG);
        p.add(Manifest.permission.READ_CALENDAR);
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
            p.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            p.add(Manifest.permission.POST_NOTIFICATIONS);
            p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        return p.toArray(new String[0]);
    }

    public static boolean has(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAll(Context c, String... permissions) {
        for (String p : permissions) if (!has(c, p)) return false;
        return true;
    }
}
