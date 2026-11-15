package com.asisten.bantu

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the optional LiteRT-LM model instance for the process.
 * Requests are serialized; the model is unloaded after an idle period to return RAM.
 */
object LocalModelRuntimeManager {
    @Volatile private var engine: LiteRtLmEngine? = null
    private val busy = AtomicBoolean(false)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val QWEN_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val IDLE_UNLOAD_MS = 120_000L

    private val idleUnload = Runnable { release() }

    @JvmStatic fun modelFile(context: Context): File =
        File(LocalModelRegistry.directory(context), QWEN_FILE)

    @JvmStatic fun isModelAvailable(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > 1024L * 1024L
    }

    @JvmStatic fun answerAsync(context: Context, prompt: String, callback: (String?, String?) -> Unit) {
        val appContext = context.applicationContext
        val file = modelFile(appContext)
        if (!file.isFile || file.length() <= 1024L * 1024L) {
            postResult(callback, null, "Model Qwen3 .litertlm belum terpasang.")
            return
        }
        if (prompt.isBlank()) {
            postResult(callback, null, "Pertanyaan kosong.")
            return
        }
        if (!busy.compareAndSet(false, true)) {
            postResult(callback, null, "Model lokal sedang memproses permintaan lain. Coba lagi sebentar.")
            return
        }

        idleHandler.removeCallbacks(idleUnload)
        val active = synchronized(this) {
            engine ?: LiteRtLmEngine(appContext).also { engine = it }
        }
        active.initializeAsync(file, object : LiteRtLmEngine.Callback {
            override fun onReady(success: Boolean, message: String) {
                if (!success) {
                    finishRequest()
                    postResult(callback, null, message)
                    return
                }
                active.sendMessageAsync(prompt, object : LiteRtLmEngine.Callback {
                    private val response = StringBuilder()
                    override fun onReady(success: Boolean, message: String) {}
                    override fun onPartial(text: String) {
                        response.append(text)
                    }
                    override fun onComplete() {
                        val answer = response.toString().trim().takeIf { it.isNotEmpty() }
                        finishRequest()
                        postResult(callback, answer, if (answer == null) "Model tidak menghasilkan jawaban." else null)
                    }
                    override fun onError(message: String) {
                        finishRequest()
                        postResult(callback, null, message)
                    }
                })
            }
            override fun onPartial(text: String) {}
            override fun onComplete() {}
            override fun onError(message: String) {
                finishRequest()
                postResult(callback, null, message)
            }
        })
    }

    private fun postResult(callback: (String?, String?) -> Unit, answer: String?, error: String?) {
        mainHandler.post { callback(answer, error) }
    }

    private fun finishRequest() {
        busy.set(false)
        idleHandler.removeCallbacks(idleUnload)
        idleHandler.postDelayed(idleUnload, IDLE_UNLOAD_MS)
    }

    @JvmStatic fun release() {
        idleHandler.removeCallbacks(idleUnload)
        if (busy.get()) return
        synchronized(this) {
            engine?.close()
            engine = null
        }
    }
}
