package com.asisten.bantu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Map;

/**
 * Local-only daily routine scheduler. The allowed action is a spoken reminder
 * plus local notification. Conditions are limited to battery threshold or charging.
 */
public final class RoutineAutomationManager {
    static final String EXTRA_CONDITION = "routine_condition";
    static final String EXTRA_THRESHOLD = "routine_threshold";
    private static final String PREFS = "kobra_local_routines";
    private static final String PREFIX = "daily_";
    private static final int MAX_TITLE_LENGTH = 120;

    private RoutineAutomationManager() {}

    public static boolean scheduleDailyReminder(
            Context context, int id, int hour, int minute, String title) {
        return scheduleDailyReminder(context, id, hour, minute, title, "always", -1);
    }

    public static boolean scheduleDailyReminder(
            Context context, int id, int hour, int minute, String title,
            String condition, int threshold) {
        if (context == null || id <= 0 || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || !isValidCondition(condition, threshold)) return false;
        String cleanTitle = sanitizeTitle(title);
        if (cleanTitle.isEmpty()) return false;
        try {
            JSONObject routine = new JSONObject();
            routine.put("id", id);
            routine.put("hour", hour);
            routine.put("minute", minute);
            routine.put("title", cleanTitle);
            routine.put("condition", condition);
            routine.put("threshold", threshold);
            routine.put("run_count", 0);
            routine.put("last_result", "belum dijalankan");
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(PREFIX + id, routine.toString()).apply();
            boolean scheduled = scheduleNext(context.getApplicationContext(), id, hour, minute,
                    cleanTitle, condition, threshold);
            if (!scheduled) prefs.edit().remove(PREFIX + id).apply();
            return scheduled;
        } catch (Exception error) {
            return false;
        }
    }

    public static void restoreAfterBoot(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX) || !(entry.getValue() instanceof String)) continue;
            try {
                JSONObject routine = new JSONObject((String) entry.getValue());
                String condition = routine.optString("condition", "always");
                int threshold = routine.optInt("threshold", -1);
                if (!isValidCondition(condition, threshold)) {
                    prefs.edit().remove(entry.getKey()).apply();
                    continue;
                }
                scheduleNext(context, routine.getInt("id"), routine.getInt("hour"),
                        routine.getInt("minute"), sanitizeTitle(routine.getString("title")),
                        condition, threshold);
            } catch (Exception ignored) {
                prefs.edit().remove(entry.getKey()).apply();
            }
        }
    }

    public static int cancelAll(Context context) {
        if (context == null) return 0;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = 0;
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith(PREFIX)) continue;
            try {
                int id = Integer.parseInt(key.substring(PREFIX.length()));
                if (alarms != null) {
                    PendingIntent pending = PendingIntent.getBroadcast(
                            app, id, new Intent(app, KobraRoutineReceiver.class),
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (pending != null) {
                        alarms.cancel(pending);
                        pending.cancel();
                    }
                }
                count++;
            } catch (Exception ignored) {}
        }
        prefs.edit().clear().apply();
        return count;
    }

    public static String describe(Context context) {
        if (context == null) return "Penyimpanan rutinitas tidak tersedia.";
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX) || !(entry.getValue() instanceof String)) continue;
            try {
                JSONObject routine = new JSONObject((String) entry.getValue());
                if (count > 0) out.append(" ");
                out.append("Rutinitas ").append(count + 1).append(": pukul ")
                        .append(String.format(java.util.Locale.ROOT, "%02d:%02d",
                                routine.getInt("hour"), routine.getInt("minute")))
                        .append(", ").append(sanitizeTitle(routine.optString("title", "Pengingat")));
                String condition = routine.optString("condition", "always");
                if ("battery_below".equals(condition)) {
                    out.append(", hanya jika baterai di bawah ")
                            .append(routine.optInt("threshold", 20)).append(" persen");
                } else if ("charging".equals(condition)) {
                    out.append(", hanya ketika sedang diisi daya");
                }
                out.append(". Terakhir: ").append(routine.optString("last_result", "belum dijalankan")).append(".");
                count++;
            } catch (Exception ignored) {}
        }
        return count == 0 ? "Belum ada rutinitas harian lokal yang aktif."
                : "Ada " + count + " rutinitas harian lokal. " + out;
    }

    static boolean isActive(Context context, int id) {
        if (context == null || id <= 0) return false;
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(PREFIX + id);
    }

    static boolean isConditionSatisfied(Context context, String condition, int threshold) {
        if ("always".equals(condition)) return true;
        if (context == null) return false;
        try {
            BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (battery == null) return false;
            if ("charging".equals(condition)) return battery.isCharging();
            if ("battery_below".equals(condition) && threshold >= 1 && threshold <= 100) {
                int percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                return percent >= 0 && percent < threshold;
            }
        } catch (Exception ignored) {}
        return false;
    }

    static void recordExecution(Context context, int id, String result) {
        if (!isActive(context, id)) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = PREFIX + id;
        try {
            JSONObject routine = new JSONObject(prefs.getString(key, "{}"));
            routine.put("run_count", routine.optInt("run_count", 0) + 1);
            routine.put("last_result", result);
            routine.put("last_run", System.currentTimeMillis());
            prefs.edit().putString(key, routine.toString()).apply();
        } catch (Exception ignored) {}
    }

    static boolean scheduleNext(Context context, int id, int hour, int minute,
                                String title, String condition, int threshold) {
        if (context == null || id <= 0 || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || !isValidCondition(condition, threshold)) return false;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return false;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis() + 1_000L) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        Intent intent = new Intent(context, KobraRoutineReceiver.class)
                .putExtra(PlannerAlarmReceiver.EXTRA_ID, id)
                .putExtra(PlannerAlarmReceiver.EXTRA_TITLE, sanitizeTitle(title))
                .putExtra(PlannerAlarmReceiver.EXTRA_HOUR, hour)
                .putExtra(PlannerAlarmReceiver.EXTRA_MINUTE, minute)
                .putExtra(EXTRA_CONDITION, condition)
                .putExtra(EXTRA_THRESHOLD, threshold);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
            }
            return true;
        } catch (SecurityException denied) {
            try {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean isValidCondition(String condition, int threshold) {
        return "always".equals(condition) || "charging".equals(condition)
                || ("battery_below".equals(condition) && threshold >= 1 && threshold <= 100);
    }

    private static String sanitizeTitle(String title) {
        if (title == null) return "";
        String clean = title.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_TITLE_LENGTH ? clean.substring(0, MAX_TITLE_LENGTH) : clean;
    }
}
