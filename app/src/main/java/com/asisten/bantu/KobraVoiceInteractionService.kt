package com.asisten.bantu

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Layanan Asisten Digital Bawaan Android (VoiceInteractionService) agar Kobra OS
 * dapat dipilih pada menu Setelan -> Asisten Digital Default.
 */
class KobraVoiceInteractionService : VoiceInteractionService()

class KobraVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return KobraVoiceInteractionSession(this)
    }
}

class KobraVoiceInteractionSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (AsistenAccessibilityService.isRunning()) {
            AsistenAccessibilityService.requestListen()
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_listen", true)
            }
            context.startActivity(intent)
        }
        hide()
    }
}
