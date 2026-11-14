package com.asisten.bantu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver untuk menjadwalkan dan mengeksekusi pemeriksaan Guardian Check-In serta eskalasi SOS.
 */
class GuardianCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            GuardianManager.ACTION_GUARDIAN_CHECK -> GuardianManager.handleCheckInPrompt(context)
            GuardianManager.ACTION_GUARDIAN_ACK -> GuardianManager.acknowledgeSafe(context, true)
            GuardianManager.ACTION_GUARDIAN_ESCALATE -> GuardianManager.handleEscalationIfUnconfirmed(context)
        }
    }
}
