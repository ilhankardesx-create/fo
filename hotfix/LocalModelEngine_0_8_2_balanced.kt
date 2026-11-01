package com.offlineai.app

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
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
                createEngine(
                    modelPath = modelPath,
                    textBackend = Backend.GPU(),
                    visionBackend = Backend.GPU()
                )
            }

            val loaded = if (gpuAttempt.isSuccess) {
                activeBackend = "GPU · Vision GPU"
                gpuAttempt.getOrThrow()
            } else {
                activeBackend = "CPU ×8 · Vision CPU"
                createEngine(
                    modelPath = modelPath,
                    textBackend = Backend.CPU(threadCount = 8),
                    visionBackend = Backend.CPU(threadCount = 4)
                )
            }

            engine = loaded.first
            conversation = loaded.second
            loadedPath = modelPath
            activeBackend
        }
    }

    private fun createEngine(
        modelPath: String,
        textBackend: Backend,
        visionBackend: Backend
    ): Pair<Engine, Conversation> {
        val candidate = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = textBackend,
                visionBackend = visionBackend,
                maxNumTokens = 3072,
                cacheDir = context.cacheDir.absolutePath
            )
        )
        candidate.initialize()
        val candidateConversation = candidate.createConversation(conversationConfig())
        return candidate to candidateConversation
    }

    override fun streamReply(prompt: String, imagePath: String?): Flow<String> = channelFlow {
        withContext(dispatcher) {
            if (busy) error("Model şu anda yanıt üretiyor")
            val conv = conversation ?: error("Model yüklenmedi")
            busy = true
            try {
                val contents = if (imagePath.isNullOrBlank()) {
                    Contents.of(Content.Text(prompt))
                } else {
                    val image = java.io.File(imagePath)
                    if (!image.exists()) error("Seçilen fotoğraf bulunamadı")
                    Contents.of(
                        Content.Text(prompt),
                        Content.ImageFile(image.absolutePath)
                    )
                }

                conv.sendMessageAsync(
                    contents = contents,
                    extraContext = mapOf("enable_thinking" to true),
                    repetitionPenaltyConfig = RepetitionPenaltyConfig(
                        repetitionPenalty = 1.06f,
                        presencePenalty = 0.03f,
                        frequencyPenalty = 0.03f,
                        windowSize = 192
                    ),
                    noRepeatNgramConfig = NoRepeatNgramConfig(
                        noRepeatNgramSize = 4,
                        windowSize = 192
                    ),
                    maxOutputToken = 640,
                    thinkingConfig = ThinkingConfig(
                        enableThinking = true,
                        thinkingTokenBudget = 128
                    )
                ).collect { message ->
                    val chunk = message.toString()
                    if (chunk.isNotEmpty()) send(chunk)
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
            "Sen İnternetsiz Yapay Zeka adlı yardımcı bir asistansın. " +
                "Kullanıcının dilinde doğru, kısa ve düzenli cevap ver. " +
                "Gerektiğinde kısa bir girişten sonra numaralı liste veya madde işaretleri kullan. " +
                "Her maddeyi ayrı satıra yaz. Markdown yıldızlarını kullanıcıya göstermeden doğal metin üret. " +
                "Fotoğraf verilirse yalnızca görüntüde gerçekten görülen ayrıntılara dayan ve emin olmadığın şeyi kesinmiş gibi söyleme."
        ),
        samplerConfig = SamplerConfig(
            topK = 48,
            topP = 0.95,
            temperature = 0.75
        ),
        maxOutputToken = 640,
        thinkingConfig = ThinkingConfig(
            enableThinking = true,
            thinkingTokenBudget = 128
        )
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
