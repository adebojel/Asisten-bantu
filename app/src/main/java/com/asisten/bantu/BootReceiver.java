package com.asisten.bantu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (c == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            RoutineAutomationManager.restoreAfterBoot(c);
            KobraForegroundService.start(c);
        } else if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            RoutineAutomationManager.restoreAfterBoot(c);
        }
    }
}
