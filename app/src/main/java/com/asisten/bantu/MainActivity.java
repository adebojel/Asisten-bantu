
package com.asisten.bantu;
import android.Manifest; import android.content.Intent; import android.net.Uri; import android.os.Build; import android.os.Bundle; import android.os.PowerManager; import android.provider.Settings; import android.widget.TextView; import android.widget.Toast; import androidx.appcompat.app.AppCompatActivity; import androidx.core.app.ActivityCompat;
public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); tvStatus = findViewById(R.id.tvStatus);
        findViewById(R.id.btnOverlay).setOnClickListener(v -> { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); else toast("Overlay aktif"); });
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); toast("Cari Kobra OS V6 lalu AKTIFKAN"); });
        findViewById(R.id.btnPermissions).setOnClickListener(v -> { ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.QUERY_ALL_PACKAGES }, 100); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); });
        findViewById(R.id.btnBattery).setOnClickListener(v -> { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(getPackageName())) startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); Intent s = new Intent(this, KobraForegroundService.class); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(s); else startService(s); toast("Kobra V6 tetap hidup + Tongkat + AI"); });
        findViewById(R.id.btnLauncher).setOnClickListener(v -> { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); toast("Pilih Kobra OS V6 sebagai Home Utama"); });
        findViewById(R.id.btnSos).setOnClickListener(v -> startActivity(new Intent(this, SosSetupActivity.class)));
    }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); tvStatus.setText("Status: "+s); }
}

