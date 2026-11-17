package com.asisten.bantu

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Installs the Qwen3 model shipped inside the APK into app-private storage.
 * The copy is atomic: LiteRT-LM never sees a partially copied model.
 */
object LocalModelAssetInstaller {
    private const val TAG = "KobraModelInstaller"
    private const val MODEL_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val MIN_MODEL_BYTES = 250L * 1024L * 1024L
    private const val MAX_MODEL_BYTES = 450L * 1024L * 1024L
    private const val EXPECTED_MODEL_BYTES = 347_251_840L
    private const val EXPECTED_MODEL_SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var verifiedFileKey: String? = null

    @JvmStatic
    fun isBundled(context: Context): Boolean = try {
        context.applicationContext.assets.list("")?.contains(MODEL_FILE) == true
    } catch (_: Exception) {
        false
    }

    @JvmStatic
    fun installIfNeeded(context: Context, callback: (Boolean, String?) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            try {
                val directory = LocalModelRegistry.directory(app)
                val destination = File(directory, MODEL_FILE)
                if (isVerified(destination)) {
                    callback(true, null)
                    return@execute
                }

                check(isBundled(app)) { "Aset model Qwen3 tidak ditemukan di APK." }
                val temporary = File(directory, "$MODEL_FILE.installing")
                temporary.delete()
                app.assets.open(MODEL_FILE).buffered().use { input ->
                    FileOutputStream(temporary).buffered(1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }

                check(isValid(temporary) && ModelFileIntegrity.matches(
                        temporary, EXPECTED_MODEL_BYTES, EXPECTED_MODEL_SHA256)) {
                    "Salinan model Qwen3 tidak valid: ukuran atau SHA-256 tidak cocok."
                }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) {
                    "Gagal memasang model Qwen3 ke penyimpanan privat aplikasi."
                }
                verifiedFileKey = fileKey(destination)
                Log.i(TAG, "Qwen3 model installed: ${destination.length()} bytes")
                callback(true, null)
            } catch (error: Exception) {
                Log.e(TAG, "Qwen3 asset installation failed", error)
                callback(false, error.message ?: "Gagal menyiapkan model Qwen3.")
            }
        }
    }

    private fun isVerified(file: File): Boolean {
        if (!isValid(file)) {
            verifiedFileKey = null
            return false
        }
        val key = fileKey(file)
        if (verifiedFileKey == key) return true
        val valid = ModelFileIntegrity.matches(file, EXPECTED_MODEL_BYTES, EXPECTED_MODEL_SHA256)
        verifiedFileKey = if (valid) key else null
        return valid
    }

    private fun fileKey(file: File): String =
        "${file.absolutePath}:${file.length()}:${file.lastModified()}"

    private fun isValid(file: File): Boolean =
        file.isFile && file.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES
}
