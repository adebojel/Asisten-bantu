package com.asisten.bantu

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM memory storage; key material remains inside Android Keystore. */
internal object EncryptedLocalMemoryStore {
    private const val PREFS = "kobra_secure_local_memory"
    private const val PAYLOAD = "payload_aes_gcm_v1"
    private const val KEY_ALIAS = "kobra_local_memory_aes_gcm_v1"
    private const val LEGACY_PREFS = KobraConfig.PREFS_CONFIG
    private const val LEGACY_KEY = "local_ai_memory_v1"
    private const val MAX_BYTES = 64 * 1024

    @Synchronized
    fun read(context: Context): String? {
        val app = context.applicationContext
        val secure = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (secure.contains(PAYLOAD)) {
            return try {
                val plain = decrypt(secure.getString(PAYLOAD, null) ?: return null)
                app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(LEGACY_KEY).commit()
                plain
            } catch (_: Exception) { null } // Fail closed; never fall back to plaintext.
        }
        val legacyPrefs = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(LEGACY_KEY, null)?.takeIf { it.isNotBlank() } ?: return "[]"
        return if (write(app, legacy)) {
            legacyPrefs.edit().remove(LEGACY_KEY).commit()
            legacy
        } else null
    }

    @Synchronized
    fun write(context: Context, json: String): Boolean {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP)
            val app = context.applicationContext
            val stored = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PAYLOAD, packed).commit()
            if (stored) app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().remove(LEGACY_KEY).commit()
            stored
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        val app = context.applicationContext
        val a = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PAYLOAD).commit()
        val b = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().remove(LEGACY_KEY).commit()
        return a && b
    }

    private fun decrypt(packed: String): String {
        val parts = packed.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12 && data.isNotEmpty())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(data)
        require(plain.size <= MAX_BYTES)
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}