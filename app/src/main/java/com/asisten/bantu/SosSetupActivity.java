
package com.asisten.bantu;
import android.content.SharedPreferences; import android.os.Bundle; import android.widget.*; import androidx.appcompat.app.AppCompatActivity;
public class SosSetupActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_sos);
        EditText etName1 = findViewById(R.id.etName1); EditText etPhone1 = findViewById(R.id.etPhone1); EditText etName2 = findViewById(R.id.etName2); EditText etPhone2 = findViewById(R.id.etPhone2); TextView tvStatus = findViewById(R.id.tvSosStatus); Button btnSave = findViewById(R.id.btnSaveSos);
        SharedPreferences pref = getSharedPreferences("kobra_sos", MODE_PRIVATE);
        etName1.setText(pref.getString("name1","")); etPhone1.setText(pref.getString("phone1","")); etName2.setText(pref.getString("name2","")); etPhone2.setText(pref.getString("phone2",""));
        btnSave.setOnClickListener(v -> { pref.edit().putString("name1", etName1.getText().toString()).putString("phone1", etPhone1.getText().toString()).putString("name2", etName2.getText().toString()).putString("phone2", etPhone2.getText().toString()).apply(); tvStatus.setText("✅ Kontak darurat disimpan!"); Toast.makeText(this, "Kontak SOS disimpan", Toast.LENGTH_LONG).show(); });
    }
}

