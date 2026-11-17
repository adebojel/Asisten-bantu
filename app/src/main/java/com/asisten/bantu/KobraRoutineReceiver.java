package com.asisten.bantu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Delivers one daily routine reminder, then schedules its next local occurrence. */
public final class KobraRoutineReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int id = intent.getIntExtra(PlannerAlarmReceiver.EXTRA_ID, -1);
        int hour = intent.getIntExtra(PlannerAlarmReceiver.EXTRA_HOUR, -1);
        int minute = intent.getIntExtra(PlannerAlarmReceiver.EXTRA_MINUTE, -1);
        String title = intent.getStringExtra(PlannerAlarmReceiver.EXTRA_TITLE);
        if (id <= 0 || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || title == null || title.trim().isEmpty()
                || !RoutineAutomationManager.isActive(context, id)) return;

        Intent reminder = new Intent(context, PlannerAlarmReceiver.class)
                .putExtra(PlannerAlarmReceiver.EXTRA_ID, id)
                .putExtra(PlannerAlarmReceiver.EXTRA_TITLE, title)
                .putExtra(PlannerAlarmReceiver.EXTRA_HOUR, hour)
                .putExtra(PlannerAlarmReceiver.EXTRA_MINUTE, minute);
        new PlannerAlarmReceiver().onReceive(context, reminder);
        RoutineAutomationManager.scheduleNext(context, id, hour, minute, title);
    }
}
