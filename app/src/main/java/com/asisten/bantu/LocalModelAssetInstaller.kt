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
    private val executor = Executors.newSingleThreadExecutor()

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
                if (isValid(destination)) {
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

                check(isValid(temporary)) {
                    "Salinan model Qwen3 tidak lengkap (${temporary.length()} byte)."
                }
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) {
                    "Gagal memasang model Qwen3 ke penyimpanan privat aplikasi."
                }
                Log.i(TAG, "Qwen3 model installed: ${destination.length()} bytes")
                callback(true, null)
            } catch (error: Exception) {
                Log.e(TAG, "Qwen3 asset installation failed", error)
                callback(false, error.message ?: "Gagal menyiapkan model Qwen3.")
            }
        }
    }

    private fun isValid(file: File): Boolean =
        file.isFile && file.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES
}
