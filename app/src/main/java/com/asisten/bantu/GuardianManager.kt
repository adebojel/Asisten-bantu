package com.asisten.bantu

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pengelola Guardian Check-In Berkala & Daily Morning Briefing untuk keamanan pengguna.
 * Jika check-in tidak dikonfirmasi "Saya Aman" dalam 60 detik, otomatis memicu EmergencyBeacon SOS.
 */
object GuardianManager {

    private const val TAG = "KobraGuardian"
    private const val CHANNEL_ID = "kobra_guardian_channel"
    private const val NOTIF_ID = 7709
    private const val REQ_CHECKIN = 7710
    private const val REQ_ESCALATE = 7711
    private const val ESCALATION_WAIT_MS = 60_000L

    const val ACTION_GUARDIAN_CHECK = "com.asisten.bantu.ACTION_GUARDIAN_CHECK"
    const val ACTION_GUARDIAN_ACK = "com.asisten.bantu.ACTION_GUARDIAN_ACK"
    const val ACTION_GUARDIAN_ESCALATE = "com.asisten.bantu.ACTION_GUARDIAN_ESCALATE"

    @JvmStatic
    fun scheduleCheckIn(context: Context, intervalMinutes: Int) {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        cfg.setGuardianIntervalMinutes(intervalMinutes)
        cancelPendingAlarms(ctx)

        if (intervalMinutes <= 0) {
            cfg.setGuardianAwaitingResponse(false)
            return
        }

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = System.currentTimeMillis() + intervalMinutes * 60_000L
        val pi = buildBroadcastPendingIntent(ctx, ACTION_GUARDIAN_CHECK, REQ_CHECKIN)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menjadwalkan Guardian Check-In", t)
        }
    }

    @JvmStatic
    fun triggerManualCheckInNow(context: Context) {
        handleCheckInPrompt(context.applicationContext)
    }

    @JvmStatic
    fun acknowledgeSafe(context: Context, speakConfirmation: Boolean = true) {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        cfg.setGuardianAwaitingResponse(false)
        cfg.setLastGuardianAckAt(System.currentTimeMillis())

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            am.cancel(buildBroadcastPendingIntent(ctx, ACTION_GUARDIAN_ESCALATE, REQ_ESCALATE))
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIF_ID)

        val interval = cfg.guardianIntervalMinutes
        if (interval > 0) {
            scheduleCheckIn(ctx, interval)
        }
        if (speakConfirmation) {
            Speaker.get(ctx).speak("Status aman dicatat. Pengawas Guardian tetap berjaga.")
        }
    }

    @JvmStatic
    fun handleCheckInPrompt(context: Context) {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        cfg.setGuardianAwaitingResponse(true)

        ensureChannel(ctx)
        val ackIntent = Intent(ctx, GuardianCheckReceiver::class.java).apply {
            action = ACTION_GUARDIAN_ACK
        }
        val ackPi = PendingIntent.getBroadcast(
            ctx,
            REQ_CHECKIN + 50,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(ctx, NavigationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            ctx,
            REQ_CHECKIN + 51,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_3d_sos_shield)
            .setContentTitle("Guardian Check-In Kobra OS")
            .setContentText("Apakah Anda aman? Konfirmasi dalam 60 detik sebelum SOS darurat dikirim.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPi)
            .addAction(0, "Saya Aman", ackPi)
            .setAutoCancel(false)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, notif)

        HapticFeedback.vibrateAction(ctx)
        Speaker.get(ctx).speak(
            "Pemeriksaan Guardian. Apakah Anda dalam keadaan aman? Katakan saya aman atau tekan tombol Saya Aman dalam enam puluh detik."
        )

        // Jadwalkan eskalasi otomatis 60 detik
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val escalateAt = System.currentTimeMillis() + ESCALATION_WAIT_MS
        val escPi = buildBroadcastPendingIntent(ctx, ACTION_GUARDIAN_ESCALATE, REQ_ESCALATE)
        try {
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, escalateAt, escPi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, escalateAt, escPi)
                }
            }
        } catch (_: Throwable) {
            Handler(Looper.getMainLooper()).postDelayed({
                handleEscalationIfUnconfirmed(ctx)
            }, ESCALATION_WAIT_MS)
        }
    }

    @JvmStatic
    fun handleEscalationIfUnconfirmed(context: Context) {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        if (!cfg.isGuardianAwaitingResponse) return
        cfg.setGuardianAwaitingResponse(false)

        if (cfg.hasSosContact()) {
            Speaker.get(ctx).speak("Tidak ada konfirmasi aman. Memicu SOS Guardian ke kontak darurat.")
            EmergencyBeacon.triggerAll(ctx, null)
        } else {
            Speaker.get(ctx).speak("Tidak ada konfirmasi Guardian. Silakan atur nomor kontak SOS darurat di Pengaturan.")
        }

        val interval = cfg.guardianIntervalMinutes
        if (interval > 0) {
            scheduleCheckIn(ctx, interval)
        }
    }

    @JvmStatic
    fun buildDailyBriefingSpeech(context: Context): String {
        val ctx = context.applicationContext
        val cfg = KobraConfig(ctx)
        val now = Date()
        val hari = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(now)
        val jam = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(now)
        val statusBat = SystemUtilityHelper.batteryStatus(ctx)
        val steps = cfg.todaySteps
        val agenda = cfg.listPlannerSpeech()
        val notif = KobraNotificationService.summary(3)

        return buildString {
            append("Ringkasan harian Kobra OS. Hari ini ").append(hari)
            append(", pukul ").append(jam).append(". ")
            append(statusBat).append(". ")
            append("Langkah hari ini: ").append(steps).append(" langkah. ")
            if (agenda.isNotEmpty()) {
                append("Jadwal hari ini: ").append(agenda).append(" ")
            } else {
                append("Belum ada jadwal agenda tertulis hari ini. ")
            }
            if (notif.isNotEmpty() && !notif.contains("Belum ada notifikasi")) {
                append("Notifikasi terbaru: ").append(notif)
            }
        }.trim()
    }

    @JvmStatic
    fun speakDailyBriefing(context: Context) {
        val ctx = context.applicationContext
        Speaker.get(ctx).speak(buildDailyBriefingSpeech(ctx))
    }

    private fun cancelPendingAlarms(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(buildBroadcastPendingIntent(ctx, ACTION_GUARDIAN_CHECK, REQ_CHECKIN))
        am.cancel(buildBroadcastPendingIntent(ctx, ACTION_GUARDIAN_ESCALATE, REQ_ESCALATE))
    }

    private fun buildBroadcastPendingIntent(ctx: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(ctx, GuardianCheckReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            ctx,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.guardian_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Pemeriksaan keamanan berkala Guardian Kobra OS"
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
