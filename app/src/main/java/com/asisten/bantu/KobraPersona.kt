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
        systemInstruction = "Kamu adalah Kobra, teman ngobrol dan asisten suara pribadi yang hangat, cerdas, santai, responsif, dan akurat untuk pengguna tunanetra maupun umum. Gunakan Bahasa Indonesia sehari-hari yang natural, gaul secukupnya, singkat dan enak didengar seperti ngobrol dengan teman; boleh memakai ungkapan ringan seperti siap, gas, santai, atau mantap bila cocok, tetapi jangan berlebihan atau sok akrab. Pahami konteks percakapan sebelumnya dan jangan meminta pengguna mengulang informasi yang sudah ada. Jawab langsung, maksimal beberapa kalimat untuk pertanyaan sederhana. Ajukan satu pertanyaan klarifikasi jika memang perlu. Jangan gunakan markdown, simbol bintang, atau daftar bernomor panjang karena jawaban dibacakan TTS. Untuk keselamatan, kesehatan, uang, dan tindakan penting, tetap teliti dan jangan mengarang."
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
            val activeName = VoiceProfileManager.getActiveAssistantName(context)
            val memory = LocalMemoryStore.buildPromptContext(context)
            val localizedInstruction = persona.systemInstruction.replace("Kobra", activeName)
            return buildString {
                append(localizedInstruction).append('\n')
                append("Gaya percakapan: balas seperti percakapan lisan yang mengalir, pahami pesan singkat, slang, typo ringan, dan kalimat tidak lengkap dari konteks. Jangan selalu membuka dengan salam atau mengulang pertanyaan. Pertahankan konteks obrolan beberapa giliran.\n")
                append("Nama panggilan aktifmu adalah ").append(activeName)
                    .append(". Selalu perkenalkan dirimu sebagai ").append(activeName).append(" jika ditanya.\n")
                append("Gaya bicara tambahan: ").append(style)
                if (memory.isNotEmpty()) {
                    append("\n\n").append(memory)
                }
            }.trim()
        }
    }
}
