package com.asisten.bantu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Map;

/**
 * Small, local-only routine scheduler. Stage 4 supports recurring daily
 * announcement routines; it does not execute arbitrary background UI actions.
 */
public final class RoutineAutomationManager {
    private static final String PREFS = "kobra_local_routines";
    private static final String PREFIX = "daily_";
    private static final int MAX_TITLE_LENGTH = 120;

    private RoutineAutomationManager() {}

    public static boolean scheduleDailyReminder(
            Context context, int id, int hour, int minute, String title) {
        if (context == null || id <= 0 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }
        String cleanTitle = sanitizeTitle(title);
        if (cleanTitle.isEmpty()) return false;
        try {
            JSONObject routine = new JSONObject();
            routine.put("id", id);
            routine.put("hour", hour);
            routine.put("minute", minute);
            routine.put("title", cleanTitle);
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(PREFIX + id, routine.toString()).apply();
            boolean scheduled = scheduleNext(context.getApplicationContext(), id, hour, minute, cleanTitle);
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
                scheduleNext(context, routine.getInt("id"), routine.getInt("hour"),
                        routine.getInt("minute"), sanitizeTitle(routine.getString("title")));
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

    static boolean isActive(Context context, int id) {
        if (context == null || id <= 0) return false;
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(PREFIX + id);
    }

    static boolean scheduleNext(Context context, int id, int hour, int minute, String title) {
        if (context == null || id <= 0 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }
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
                .putExtra(PlannerAlarmReceiver.EXTRA_MINUTE, minute);
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

    private static String sanitizeTitle(String title) {
        if (title == null) return "";
        String clean = title.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_TITLE_LENGTH ? clean.substring(0, MAX_TITLE_LENGTH) : clean;
    }
}
