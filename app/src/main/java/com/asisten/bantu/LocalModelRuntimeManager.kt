package com.asisten.bantu

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the optional LiteRT-LM model instance for the process.
 * Qwen is kept separate from the legacy MediaPipe .task engine.
 */
object LocalModelRuntimeManager {
    @Volatile private var engine: LiteRtLmEngine? = null
    private val initializing = AtomicBoolean(false)

    private const val QWEN_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"

    fun modelFile(context: Context): File {
        return File(LocalModelRegistry.directory(context), QWEN_FILE)
    }

    fun isModelAvailable(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > 1024L * 1024L
    }

    fun answerAsync(context: Context, prompt: String, callback: (String?, String?) -> Unit) {
        val appContext = context.applicationContext
        val file = modelFile(appContext)
        if (!file.isFile || file.length() <= 1024L * 1024L) {
            callback(null, "Model Qwen3 .litertlm belum terpasang.")
            return
        }

        val active = synchronized(this) {
            engine ?: LiteRtLmEngine(appContext).also { engine = it }
        }
        if (!initializing.compareAndSet(false, true)) {
            callback(null, "Model lokal sedang disiapkan. Coba lagi sebentar.")
            return
        }

        active.initializeAsync(file, object : LiteRtLmEngine.Callback {
            override fun onReady(success: Boolean, message: String) {
                initializing.set(false)
                if (!success) {
                    callback(null, message)
                    return
                }
                active.sendMessageAsync(prompt, object : LiteRtLmEngine.Callback {
                    private val response = StringBuilder()
                    override fun onReady(success: Boolean, message: String) {}
                    override fun onPartial(text: String) {
                        response.append(text)
                    }
                    override fun onComplete() {
                        callback(response.toString().trim().takeIf { it.isNotEmpty() },
                            if (response.isEmpty()) "Model tidak menghasilkan jawaban." else null)
                    }
                    override fun onError(message: String) {
                        callback(null, message)
                    }
                })
            }
            override fun onPartial(text: String) {}
            override fun onComplete() {}
            override fun onError(message: String) {
                initializing.set(false)
                callback(null, message)
            }
        })
    }

    fun release() {
        synchronized(this) {
            engine?.close()
            engine = null
            initializing.set(false)
        }
    }
}
