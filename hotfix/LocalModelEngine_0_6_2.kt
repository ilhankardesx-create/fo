package com.offlineai.app

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalModelEngine(private val context: Context) : ModelBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    @Volatile
    private var busy = false

    @Volatile
    private var activeBackend = ""

    override val isLoaded: Boolean
        get() = engine != null && conversation != null

    override suspend fun load(modelPath: String): String = withContext(dispatcher) {
        mutex.withLock {
            if (loadedPath == modelPath && isLoaded) return@withLock activeBackend
            releaseInternal()

            val gpuAttempt = runCatching {
                createEngine(modelPath, Backend.GPU())
            }

            val loaded = if (gpuAttempt.isSuccess) {
                activeBackend = "GPU"
                gpuAttempt.getOrThrow()
            } else {
                activeBackend = "CPU ×8"
                createEngine(modelPath, Backend.CPU(threadCount = 8))
            }

            engine = loaded.first
            conversation = loaded.second
            loadedPath = modelPath
            activeBackend
        }
    }

    private fun createEngine(modelPath: String, backend: Backend): Pair<Engine, Conversation> {
        val candidate = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = 2048,
                cacheDir = context.cacheDir.absolutePath
            )
        )
        candidate.initialize()
        val candidateConversation = candidate.createConversation(conversationConfig())
        return candidate to candidateConversation
    }

    override fun streamReply(prompt: String): Flow<String> = channelFlow {
        withContext(dispatcher) {
            if (busy) error("Model şu anda yanıt üretiyor")
            val conv = conversation ?: error("Model yüklenmedi")
            busy = true
            try {
                // Same reasoning behavior that was stable in the working 0.5 build:
                // conversation starts with thinking disabled; thinking is enabled only
                // for each generation request with a bounded 512-token budget.
                conv.sendMessageAsync(
                    prompt,
                    maxOutputToken = 1024,
                    thinkingConfig = ThinkingConfig(
                        enableThinking = true,
                        thinkingTokenBudget = 512
                    )
                ).collect { chunk ->
                    send(chunk.toString())
                }
            } finally {
                busy = false
            }
        }
    }

    override suspend fun resetConversation() = withContext(dispatcher) {
        mutex.withLock {
            stopGenerationIfNeeded()
            conversation?.close()
            conversation = engine?.createConversation(conversationConfig())
        }
    }

    override suspend fun closeModel() = withContext(dispatcher) {
        mutex.withLock { releaseInternal() }
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "Sen Offline AI adlı yardımcı bir asistansın. " +
                "Kullanıcının yazdığı dilde cevap ver. Kullanıcı Türkçe yazıyorsa doğal ve akıcı Türkçe cevap ver. " +
                "Gereksiz yere uzun cevap verme; kullanıcı ayrıntı isterse ayrıntılı ol."
        ),
        maxOutputToken = 768,
        thinkingConfig = ThinkingConfig(enableThinking = false)
    )

    private suspend fun releaseInternal() {
        stopGenerationIfNeeded()
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        loadedPath = null
        activeBackend = ""
    }

    private suspend fun stopGenerationIfNeeded() {
        while (busy) {
            conversation?.cancelProcess()
            delay(50)
        }
    }

    override fun shutdownBlocking() {
        runBlocking { runCatching { closeModel() } }
        dispatcher.close()
        executor.shutdown()
    }
}
