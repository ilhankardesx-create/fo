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

class LocalModelEngine(private val context: Context) {

    enum class ActiveBackend { GPU, CPU_8 }

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    @Volatile private var busy = false

    @Volatile
    var activeBackend: ActiveBackend? = null
        private set

    val isLoaded: Boolean
        get() = engine != null && conversation != null

    suspend fun load(modelPath: String) = withContext(dispatcher) {
        mutex.withLock {
            if (loadedPath == modelPath && isLoaded) return@withLock
            releaseInternal()

            val gpuAttempt = runCatching {
                createEngine(modelPath, Backend.GPU())
            }

            val loaded: Pair<Engine, Conversation>
            val backend: ActiveBackend

            if (gpuAttempt.isSuccess) {
                loaded = gpuAttempt.getOrThrow()
                backend = ActiveBackend.GPU
            } else {
                loaded = createEngine(modelPath, Backend.CPU(threadCount = 8))
                backend = ActiveBackend.CPU_8
            }

            engine = loaded.first
            conversation = loaded.second
            loadedPath = modelPath
            activeBackend = backend
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
        try {
            candidate.initialize()
            val candidateConversation = candidate.createConversation(conversationConfig())
            return candidate to candidateConversation
        } catch (t: Throwable) {
            runCatching { candidate.close() }
            throw t
        }
    }

    fun streamReply(prompt: String): Flow<String> = channelFlow {
        withContext(dispatcher) {
            if (busy) error("Model şu anda yanıt üretiyor")
            val conv = conversation ?: error("Model yüklenmedi")
            busy = true
            try {
                conv.sendMessageAsync(
                    prompt,
                    maxOutputToken = 1024,
                    thinkingConfig = ThinkingConfig(
                        enableThinking = true,
                        thinkingTokenBudget = 512
                    )
                ).collect { message ->
                    val answer = message.toString()
                    if (answer.isNotEmpty()) send(answer)
                }
            } finally {
                busy = false
            }
        }
    }

    suspend fun resetConversation() = withContext(dispatcher) {
        mutex.withLock {
            stopGenerationIfNeeded()
            conversation?.close()
            conversation = engine?.createConversation(conversationConfig())
        }
    }

    suspend fun closeModel() = withContext(dispatcher) {
        mutex.withLock { releaseInternal() }
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            "Sen Offline AI adlı yerel çalışan yardımcı bir asistansın. " +
                "Kullanıcının yazdığı dilde cevap ver. Türkçe yazıyorsa doğal ve akıcı Türkçe cevap ver. " +
                "Soruyu dikkatlice değerlendir ve yalnızca sonuç cevabını kullanıcıya sun. " +
                "Sonuç odaklı, doğru ve anlaşılır cevap ver. Kullanıcı ayrıntı isterse ayrıntılı ol."
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
        activeBackend = null
    }

    private suspend fun stopGenerationIfNeeded() {
        while (busy) {
            conversation?.cancelProcess()
            delay(50)
        }
    }

    fun shutdownBlocking() {
        runBlocking { runCatching { closeModel() } }
        dispatcher.close()
        executor.shutdown()
    }
}
