package com.asisten.bantu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Private trampoline for the home-screen widget voice action.
 * The exported AppWidgetProvider no longer exposes a custom voice broadcast.
 */
public final class WidgetVoiceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AsistenAccessibilityService.isRunning()) {
            AsistenAccessibilityService.requestListen();
        } else {
            Intent launch = new Intent(this, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            launch.putExtra("auto_listen", true);
            startActivity(launch);
        }
        finish();
    }
}
