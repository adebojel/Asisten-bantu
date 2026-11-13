package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Pengelola produktivitas offline:
 * Memo suara cepat, pengingat lokasi barang rumah, pembukuan pengeluaran harian,
 * pengingat jadwal minum obat, pembaca tanggal Masehi/Hijriah, dan agenda CalendarContract.
 */
public final class ProductivityManager {

    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
            "Jumadil Awal", "Jumadil Akhir", "Rajab", "Syaban",
            "Ramadan", "Syawal", "Zulkaidah", "Zulhijah"
    };

    private final SharedPreferences prefs;

    public ProductivityManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(KobraConfig.PREFS_CONFIG, Context.MODE_PRIVATE);
    }

    // ----------------------------------------------------------------- Memo Suara Cepat

    public void addMemo(String text) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString("memos_json", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("text", text.trim());
            obj.put("at", System.currentTimeMillis());
            arr.put(obj);
            while (arr.length() > 50) arr.remove(0);
            prefs.edit().putString("memos_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String readMemos(int limit) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("memos_json", "[]"));
            if (arr.length() == 0) return "Belum ada memo suara yang tersimpan.";
            int n = Math.min(limit, arr.length());
            SimpleDateFormat fmt = new SimpleDateFormat("d MMM HH:mm", new Locale("id", "ID"));
            StringBuilder sb = new StringBuilder("Memo terbaru: ");
            for (int i = 0; i < n; i++) {
                JSONObject o = arr.getJSONObject(arr.length() - 1 - i);
                sb.append("Nomor ").append(i + 1).append(", ")
                        .append(fmt.format(new Date(o.optLong("at")))).append(", ")
                        .append(o.optString("text")).append(". ");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Memo tidak dapat dibaca.";
        }
    }

    public void clearMemos() {
        prefs.edit().putString("memos_json", "[]").apply();
    }

    // ----------------------------------------------------------------- Pengingat Lokasi Barang Rumah

    public void rememberItem(String item, String location) {
        if (item == null || item.trim().isEmpty() || location == null || location.trim().isEmpty()) return;
        String cleanItem = item.trim();
        String key = cleanItem.toLowerCase(Locale.ROOT);
        try {
            JSONObject map = new JSONObject(prefs.getString("items_json", "{}"));
            JSONObject entry = new JSONObject();
            entry.put("item", cleanItem);
            entry.put("loc", location.trim());
            entry.put("at", System.currentTimeMillis());
            map.put(key, entry);
            prefs.edit().putString("items_json", map.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String findItem(String query) {
        if (query == null || query.trim().isEmpty()) return listAllItems();
        String q = query.trim().toLowerCase(Locale.ROOT);
        try {
            JSONObject map = new JSONObject(prefs.getString("items_json", "{}"));
            if (map.has(q)) {
                JSONObject e = map.getJSONObject(q);
                return e.optString("item") + " terakhir ditaruh di " + e.optString("loc") + ".";
            }
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (q.contains(k) || k.contains(q)) {
                    JSONObject e = map.getJSONObject(k);
                    return e.optString("item") + " terakhir ditaruh di " + e.optString("loc") + ".";
                }
            }
            return "Lokasi barang " + query.trim() + " belum pernah dicatat.";
        } catch (Exception e) {
            return "Daftar barang tidak dapat dibaca.";
        }
    }

    public String listAllItems() {
        try {
            JSONObject map = new JSONObject(prefs.getString("items_json", "{}"));
            if (map.length() == 0) return "Belum ada lokasi barang yang dicatat.";
            StringBuilder sb = new StringBuilder("Daftar lokasi barang: ");
            Iterator<String> it = map.keys();
            while (it.hasNext()) {
                JSONObject e = map.getJSONObject(it.next());
                sb.append(e.optString("item")).append(" di ").append(e.optString("loc")).append(". ");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Daftar barang tidak dapat dibaca.";
        }
    }

    // ----------------------------------------------------------------- Pembukuan Pengeluaran Harian

    public void addExpense(long amount, String note) {
        if (amount <= 0) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString("expenses_json", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("amount", amount);
            obj.put("note", (note == null || note.trim().isEmpty()) ? "pengeluaran" : note.trim());
            obj.put("at", System.currentTimeMillis());
            arr.put(obj);
            while (arr.length() > 200) arr.remove(0);
            prefs.edit().putString("expenses_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String getExpenseSummary() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("expenses_json", "[]"));
            if (arr.length() == 0) return "Belum ada catatan pengeluaran.";
            SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
            SimpleDateFormat monthFmt = new SimpleDateFormat("yyyyMM", Locale.US);
            String today = dayFmt.format(new Date());
            String month = monthFmt.format(new Date());

            long todaySum = 0;
            long monthSum = 0;
            int todayCount = 0;
            StringBuilder recent = new StringBuilder();
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                long amt = o.optLong("amount", 0);
                Date dt = new Date(o.optLong("at", 0));
                if (today.equals(dayFmt.format(dt))) {
                    todaySum += amt;
                    todayCount++;
                    if (todayCount <= 3) {
                        recent.append(o.optString("note")).append(" ").append(amt).append(" rupiah. ");
                    }
                }
                if (month.equals(monthFmt.format(dt))) {
                    monthSum += amt;
                }
            }
            return "Total pengeluaran hari ini: " + todaySum + " rupiah dari " + todayCount
                    + " transaksi. Total bulan ini: " + monthSum + " rupiah. "
                    + (recent.length() > 0 ? "Terbaru hari ini: " + recent : "");
        } catch (Exception e) {
            return "Catatan pengeluaran tidak dapat dibaca.";
        }
    }

    public void clearExpenses() {
        prefs.edit().putString("expenses_json", "[]").apply();
    }

    // ----------------------------------------------------------------- Pengingat Jadwal Minum Obat

    public static final class MedSchedule {
        public final String id;
        public final String name;
        public final int hour;
        public final int minute;
        public final String lastTakenDate;

        public MedSchedule(String id, String name, int hour, int minute, String lastTakenDate) {
            this.id = id;
            this.name = name;
            this.hour = hour;
            this.minute = minute;
            this.lastTakenDate = lastTakenDate;
        }
    }

    public void addMedSchedule(String name, int hour, int minute) {
        if (name == null || name.trim().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString("med_schedules_json", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", "med_" + System.currentTimeMillis());
            o.put("name", name.trim());
            o.put("hour", Math.max(0, Math.min(23, hour)));
            o.put("minute", Math.max(0, Math.min(59, minute)));
            o.put("taken_date", "");
            arr.put(o);
            prefs.edit().putString("med_schedules_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public List<MedSchedule> getMedSchedules() {
        List<MedSchedule> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("med_schedules_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new MedSchedule(
                        o.optString("id"),
                        o.optString("name"),
                        o.optInt("hour"),
                        o.optInt("minute"),
                        o.optString("taken_date", "")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public String listMedSchedulesSpeech() {
        List<MedSchedule> list = getMedSchedules();
        if (list.isEmpty()) return "Belum ada jadwal minum obat yang diatur.";
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder("Jadwal minum obat: ");
        for (MedSchedule m : list) {
            boolean taken = today.equals(m.lastTakenDate);
            sb.append(m.name).append(" pukul ")
                    .append(String.format(Locale.US, "%02d:%02d", m.hour, m.minute))
                    .append(taken ? " (sudah diminum hari ini). " : " (belum diminum hari ini). ");
        }
        return sb.toString().trim();
    }

    public void markMedicationTaken(String medId) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        try {
            JSONArray arr = new JSONArray(prefs.getString("med_schedules_json", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (medId == null || medId.isEmpty() || medId.equals(o.optString("id"))) {
                    o.put("taken_date", today);
                }
            }
            prefs.edit().putString("med_schedules_json", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void removeMedSchedule(int index) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("med_schedules_json", "[]"));
            if (index >= 0 && index < arr.length()) {
                arr.remove(index);
                prefs.edit().putString("med_schedules_json", arr.toString()).apply();
            }
        } catch (Exception ignored) {}
    }

    /** Mengembalikan jadwal obat yang tepat waktu saat ini dan belum diminum hari ini. */
    public MedSchedule findDueMedicationNow() {
        Calendar cal = Calendar.getInstance();
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        for (MedSchedule ms : getMedSchedules()) {
            if (ms.hour == h && ms.minute == m && !today.equals(ms.lastTakenDate)) {
                return ms;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------- Kalender Masehi/Hijriah & CalendarContract

    public static String getGregorianAndHijriDate() {
        String masehi = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date());
        try {
            HijrahDate hijrah = HijrahDate.now();
            int day = hijrah.get(ChronoField.DAY_OF_MONTH);
            int monthIdx = hijrah.get(ChronoField.MONTH_OF_YEAR) - 1;
            int year = hijrah.get(ChronoField.YEAR_OF_ERA);
            String hMonth = (monthIdx >= 0 && monthIdx < HIJRI_MONTHS.length) ? HIJRI_MONTHS[monthIdx] : "";
            return "Hari ini " + masehi + ", bertepatan dengan tanggal " + day + " " + hMonth + " " + year + " Hijriah.";
        } catch (Throwable t) {
            return "Hari ini " + masehi + ".";
        }
    }

    public static String readTodayAgenda(Context context) {
        if (!Perms.has(context, Manifest.permission.READ_CALENDAR)) {
            return "Izin kalender belum aktif. Buka Kobra, lalu tekan Beri Izin.";
        }
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(builder, start.getTimeInMillis());
        android.content.ContentUris.appendId(builder, end.getTimeInMillis());

        StringBuilder sb = new StringBuilder();
        int count = 0;
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        try (Cursor c = context.getContentResolver().query(
                builder.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY},
                null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext() && count < 8) {
                String title = c.getString(0);
                long begin = c.getLong(1);
                boolean allDay = c.getInt(2) == 1;
                if (title == null || title.trim().isEmpty()) continue;
                count++;
                sb.append("Agenda ").append(count).append(": ").append(title.trim());
                if (allDay) sb.append(", sepanjang hari. ");
                else sb.append(", pukul ").append(tf.format(new Date(begin))).append(". ");
            }
        } catch (Exception e) {
            return "Agenda kalender tidak bisa dibaca di HP ini.";
        }
        return count == 0 ? "Tidak ada agenda kalender untuk hari ini." : sb.toString().trim();
    }
}
