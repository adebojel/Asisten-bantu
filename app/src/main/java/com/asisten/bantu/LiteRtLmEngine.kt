package com.asisten.bantu

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Separate LiteRT-LM runtime for external .litertlm files.
 *
 * All engine/conversation operations are serialized on one owner executor.
 * Model files stay in app-private storage and are never bundled in the APK.
 */
class LiteRtLmEngine(context: Context) : AutoCloseable {
    interface Callback {
        fun onReady(success: Boolean, message: String)
        fun onPartial(text: String)
        fun onComplete()
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val owner: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)

    // These references are only accessed on owner.
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelPath: String? = null

    fun initializeAsync(modelFile: File, callback: Callback) {
        owner.execute {
            try {
                check(!closed.get()) { "Mesin LiteRT-LM sudah ditutup." }
                require(modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
                    "Berkas .litertlm tidak ditemukan atau tidak lengkap."
                }
                if (engine != null && activeModelPath == modelFile.absolutePath) {
                    callback.onReady(true, "Model LiteRT-LM sudah siap.")
                    return@execute
                }
                closeRuntimeOnOwner()

                val candidate = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = appContext.cacheDir.absolutePath
                    )
                )
                // Do not retain or close candidate if initialize throws; LiteRT-LM
                // documents that a failed initialization must not be closed.
                candidate.initialize()
                engine = candidate
                activeModelPath = modelFile.absolutePath
                conversation = candidate.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "Kamu adalah Kobra, asisten Android yang ringkas, jelas, aman, dan membantu. " +
                                "Jawab dalam bahasa yang digunakan pengguna. Jangan mengarang fakta."
                        ),
                        samplerConfig = SamplerConfig(
                            temperature = 0.7,
                            topK = 32,
                            topP = 0.9
                        )
                    )
                )
                callback.onReady(true, "Model lokal LiteRT-LM siap digunakan.")
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal memuat model LiteRT-LM", t)
                closeRuntimeOnOwner()
                callback.onReady(false, t.message ?: "Gagal memuat model lokal.")
            }
        }
    }

    fun sendMessageAsync(prompt: String, callback: Callback) {
        owner.execute {
            try {
                check(!closed.get()) { "Mesin LiteRT-LM sudah ditutup." }
                val activeConversation = conversation
                    ?: throw IllegalStateException("Model LiteRT-LM belum dimuat.")
                require(prompt.isNotBlank()) { "Pesan kosong." }
                runBlocking {
                    activeConversation.sendMessageAsync(prompt.trim()).collect { message ->
                        callback.onPartial(message.toString())
                    }
                }
                callback.onComplete()
            } catch (t: Throwable) {
                Log.e(TAG, "Generasi LiteRT-LM gagal", t)
                callback.onError(t.message ?: "Model lokal gagal menjawab.")
            }
        }
    }

    fun unloadAsync(onDone: (() -> Unit)? = null) {
        if (closed.get()) {
            onDone?.invoke()
            return
        }
        owner.execute {
            closeRuntimeOnOwner()
            onDone?.invoke()
        }
    }

    private fun closeRuntimeOnOwner() {
        try {
            conversation?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menutup conversation LiteRT-LM", t)
        } finally {
            conversation = null
        }
        try {
            engine?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Gagal menutup engine LiteRT-LM", t)
        } finally {
            engine = null
            activeModelPath = null
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        owner.execute {
            closeRuntimeOnOwner()
            owner.shutdown()
        }
    }

    companion object {
        private const val TAG = "KobraLiteRTLM"
        private const val MIN_MODEL_BYTES = 1024L * 1024L
    }
}
