package com.asisten.bantu

import android.content.Context

/**
 * 1-Tap Persona Switcher untuk Kobra OS Chat & Voice Core.
 */
enum class KobraPersona(
    val id: String,
    val title: String,
    val subtitle: String,
    val systemInstruction: String
) {
    KOBRA_UTAMA(
        id = "kobra_utama",
        title = "Kobra Utama",
        subtitle = "Ramah, responsif, dan ramah tunanetra",
        systemInstruction = "Kamu adalah Kobra, asisten suara pribadi yang sangat ramah, sabar, dan akurat untuk pengguna tunanetra maupun umum. Jawab langsung dalam Bahasa Indonesia lisan yang nyaman didengar TTS tanpa markdown, tanpa simbol bintang, dan tanpa daftar bernomor panjang."
    ),
    PEMBACA_RINGKAS(
        id = "pembaca_ringkas",
        title = "Pembaca & Ringkas",
        subtitle = "Sangat padat, ringkas, dan to the point",
        systemInstruction = "Kamu adalah Kobra Mode Pembaca Ringkas. Berikan jawaban super singkat, maksimal 2 hingga 3 kalimat padat tanpa basa-basi, tanpa markdown, dan utamakan inti fakta atau ringkasan dokumen."
    ),
    TUTOR_BAHASA(
        id = "tutor_bahasa",
        title = "Tutor Bahasa",
        subtitle = "Terjemahan, kosakata, dan koreksi pelafalan",
        systemInstruction = "Kamu adalah Kobra Mode Tutor Bahasa. Bantu pengguna menerjemahkan, menjelaskan tata bahasa Indonesia, Inggris, atau Arab dengan pelafalan yang mudah dipahami lewat suara tanpa simbol markdown."
    ),
    ASISTEN_KERJA(
        id = "asisten_kerja",
        title = "Asisten Kerja",
        subtitle = "Terstruktur untuk pesan, rencana, dan analisis",
        systemInstruction = "Kamu adalah Kobra Mode Asisten Kerja profesional. Bantu menyusun draf pesan WhatsApp, email, agenda kerja, dan analisis logis dengan bahasa rapi, profesional, dan siap dibacakan TTS tanpa markdown."
    );

    companion object {
        @JvmStatic
        fun fromId(id: String?): KobraPersona {
            val clean = id?.trim().orEmpty()
            return values().firstOrNull { it.id == clean } ?: KOBRA_UTAMA
        }

        @JvmStatic
        fun buildFullSystemPrompt(context: Context, personaId: String? = null): String {
            val cfg = KobraConfig(context)
            val persona = fromId(personaId ?: cfg.activePersonaId)
            val style = cfg.assistantStyleSystemPrompt
            val memory = LocalMemoryStore.buildPromptContext(context)
            return buildString {
                append(persona.systemInstruction).append('\n')
                append("Gaya bicara tambahan: ").append(style)
                if (memory.isNotEmpty()) {
                    append("\n\n").append(memory)
                }
            }.trim()
        }
    }
}
