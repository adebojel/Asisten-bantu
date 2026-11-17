package com.asisten.bantu

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Stores emergency contact names and phone numbers as AES-256-GCM ciphertext.
 * The AES key is non-exportable and held by Android Keystore.
 *
 * Legacy SharedPreferences are migrated only after the encrypted payload commits.
 * Backup export is explicit and returns the legacy-compatible typed JSON structure.
 */
internal object EncryptedSosStore {
    private const val SECURE_PREFS = "kobra_secure_sos"
    private const val PAYLOAD = "payload_aes_gcm_v1"
    private const val KEY_ALIAS = "kobra_sos_aes_gcm_v1"
    private const val LEGACY_PREFS = KobraConfig.PREFS_SOS
    private const val MAX_BYTES = 16 * 1024

    @JvmStatic
    @Synchronized
    fun migrate(context: Context): Boolean = read(context) != null

    @JvmStatic
    @Synchronized
    fun get(context: Context, key: String): String {
        if (key.isBlank()) return ""
        return read(context)?.optString(key, "")?.trim().orEmpty()
    }

    @JvmStatic
    @Synchronized
    fun set(context: Context, key: String, value: String): Boolean {
        if (key !in setOf("name1", "phone1", "name2", "phone2")) return false
        val data = read(context) ?: return false
        return try {
            data.put(key, value.take(256))
            write(context, data.toString())
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun exportPrefsJson(context: Context): JSONObject {
        val data = read(context)
            ?: throw IllegalStateException("Kontak SOS terenkripsi tidak dapat dibaca.")
        val out = JSONObject()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = JSONObject()
            item.put("type", "string")
            item.put("value", data.optString(key, ""))
            out.put(key, item)
        }
        return out
    }

    @JvmStatic
    @Synchronized
    fun clear(context: Context): Boolean {
        val app = context.applicationContext
        val secureCleared = app.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
            .edit().remove(PAYLOAD).commit()
        val legacyCleared = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        return secureCleared && legacyCleared
    }

    private fun read(context: Context): JSONObject? {
        val app = context.applicationContext
        val secure = app.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
        if (secure.contains(PAYLOAD)) {
            return try {
                val packed = secure.getString(PAYLOAD, null) ?: return null
                val result = JSONObject(decrypt(packed))
                val purged = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .edit().clear().commit()
                if (!purged) return null
                result
            } catch (_: Exception) {
                null // Fail closed; never fall back to plaintext after encrypted data exists.
            }
        }

        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val migrated = JSONObject()
        for ((key, value) in legacy.all) {
            if (key in setOf("name1", "phone1", "name2", "phone2") && value is String) {
                migrated.put(key, value.take(256))
            }
        }
        if (migrated.length() == 0) return migrated
        return if (write(app, migrated.toString())) migrated else null
    }

    private fun write(context: Context, json: String): Boolean {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP)
            val app = context.applicationContext
            val saved = app.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(PAYLOAD, packed).commit()
            if (!saved) return false
            val purged = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit()
            saved && purged
        } catch (_: Exception) {
            false
        }
    }

    private fun decrypt(packed: String): String {
        val parts = packed.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12 && encrypted.isNotEmpty() && encrypted.size <= MAX_BYTES + 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted)
        require(plain.size <= MAX_BYTES)
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
