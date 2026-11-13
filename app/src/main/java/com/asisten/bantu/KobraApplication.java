package com.asisten.bantu;

import android.app.Application;
import android.content.Intent;
import android.os.Process;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Titik masuk aplikasi Kobra OS dengan Global Uncaught Exception Handler.
 * Menangkap error tak terduga di main thread maupun background thread,
 * menyimpannya secara sinkron ke KobraConfig (last_crash_log), dan membuka layar pemulihan.
 */
public class KobraApplication extends Application {

    private static final String TAG = "KobraCrashShield";
    private static volatile boolean handlingCrash = false;
    private Thread.UncaughtExceptionHandler defaultHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (handlingCrash) {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
                return;
            }
            handlingCrash = true;

            String threadName = thread != null ? thread.getName() : "unknown";
            String summary = buildCrashSummary(threadName, throwable);
            String fullLog = buildFullCrashLog(threadName, summary, throwable);

            try {
                new KobraConfig(getApplicationContext()).saveLastCrashLog(fullLog);
            } catch (Throwable ignored) {}

            try {
                Log.e(TAG, fullLog);
            } catch (Throwable ignored) {}

            try {
                Intent recovery = new Intent(getApplicationContext(), CrashRecoveryActivity.class);
                recovery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                recovery.putExtra(CrashRecoveryActivity.EXTRA_CRASH_SUMMARY, summary);
                recovery.putExtra(CrashRecoveryActivity.EXTRA_CRASH_LOG, fullLog);
                startActivity(recovery);
            } catch (Throwable launchErr) {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                    return;
                }
            }

            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {}

            Process.killProcess(Process.myPid());
            System.exit(10);
        });
    }

    static String buildCrashSummary(String threadName, Throwable throwable) {
        if (throwable == null) {
            return "Terjadi kesalahan tidak dikenal pada thread " + threadName + ".";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String type = root.getClass().getSimpleName();
        String msg = root.getMessage() != null ? root.getMessage() : "(tanpa pesan)";

        StackTraceElement appFrame = findRelevantFrame(root);
        if (appFrame == null) {
            appFrame = findRelevantFrame(throwable);
        }
        String location = appFrame != null
                ? appFrame.getClassName() + "." + appFrame.getMethodName()
                    + "(" + appFrame.getFileName() + ":" + appFrame.getLineNumber() + ")"
                : "lokasi baris tidak tersedia";

        return "Jenis Error: " + type
                + "\nPesan: " + msg
                + "\nLokasi: " + location
                + "\nThread: " + threadName;
    }

    private static StackTraceElement findRelevantFrame(Throwable t) {
        if (t == null) return null;
        StackTraceElement[] stack = t.getStackTrace();
        if (stack == null || stack.length == 0) return null;
        for (StackTraceElement el : stack) {
            if (el != null && el.getClassName().startsWith("com.asisten.bantu")) {
                return el;
            }
        }
        return stack[0];
    }

    private static String buildFullCrashLog(String threadName, String summary, Throwable throwable) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String trace = throwable != null ? Log.getStackTraceString(throwable) : "";
        return "=== KOBRA OS CRASH REPORT ===\n"
                + "Waktu: " + time + "\n"
                + "Versi: " + BuildConfig.VERSION_NAME + "\n"
                + summary + "\n\n"
                + "--- STACKTRACE ---\n"
                + trace;
    }
}
