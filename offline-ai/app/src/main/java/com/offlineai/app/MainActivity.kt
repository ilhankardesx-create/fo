package com.offlineai.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.google.ai.edge.litertlm.ThinkingConfig
import io.noties.markwon.Markwon
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    companion object {
        private const val IMPORT_MODEL = 1001
        private const val PREFS = "offline_ai_prefs"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_MODEL_NAME = "model_name"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val markwon by lazy { Markwon.create(this) }

    private lateinit var chatColumn: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var modelButton: Button
    private lateinit var statusText: TextView

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var generationJob: Job? = null
    private var modelPath: String? = null
    private var mtpActive = false
    private var backendName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 15, 16)
        window.navigationBarColor = Color.rgb(15, 15, 16)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildUi()

        modelPath = resolveExistingModel()
        updateModelStatus()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 15, 16))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }

        val menu = Button(this).apply {
            text = "☰"
            textSize = 22f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(46, 46, 48), 100)
            minWidth = dp(54)
            minHeight = dp(54)
            setPadding(0, 0, 0, 0)
            setOnClickListener { newChat() }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(54), dp(54)))

        val titleWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        val title = TextView(this).apply {
            text = "İnternetsiz Yapay Zeka"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(160, 169, 181))
        }
        titleWrap.addView(title)
        titleWrap.addView(statusText)
        header.addView(titleWrap, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        modelButton = Button(this).apply {
            text = "Model"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(31, 43, 59), 24)
            setOnClickListener { openModelPicker() }
        }
        header.addView(modelButton, LinearLayout.LayoutParams(dp(76), dp(44)))
        root.addView(header)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chatColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        scroll.addView(chatColumn, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(33, 33, 34), 36)
            setPadding(dp(8), dp(6), dp(7), dp(6))
        }

        val plus = Button(this).apply {
            text = "+"
            textSize = 27f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(46, 46, 48), 100)
            setPadding(0, 0, 0, 0)
            setOnClickListener { openModelPicker() }
        }
        inputBar.addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)))

        input = EditText(this).apply {
            hint = "Bir şey sorun…"
            setHintTextColor(Color.rgb(170, 170, 174))
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine(false)
            maxLines = 5
            background = null
            setPadding(dp(14), dp(6), dp(10), dp(6))
        }
        inputBar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        sendButton = Button(this).apply {
            text = "↑"
            textSize = 25f
            setTextColor(Color.BLACK)
            background = rounded(Color.WHITE, 100)
            setPadding(0, 0, 0, 0)
            setOnClickListener { sendCurrentMessage() }
        }
        inputBar.addView(sendButton, LinearLayout.LayoutParams(dp(58), dp(58)))

        root.addView(inputBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        setContentView(root)
    }

    private fun sendCurrentMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty() || generationJob?.isActive == true) return

        val path = modelPath ?: resolveExistingModel()
        if (path == null) {
            statusText.text = "Önce Gemma 4 modelini seç"
            openModelPicker()
            return
        }
        modelPath = path

        addUserMessage(prompt)
        input.setText("")
        val assistantView = addAssistantMessage("Düşünüyor…")
        setGenerating(true)

        generationJob = scope.launch {
            try {
                ensureEngine(path)
                val sb = StringBuilder()
                var lastDraw = 0L

                withContext(Dispatchers.Default) {
                    val activeConversation = conversation ?: error("Sohbet başlatılamadı")
                    activeConversation.sendMessageAsync(prompt).collect { message ->
                        val delta = message.toString()
                        if (delta.isNotEmpty()) {
                            sb.append(delta)
                            val now = SystemClock.uptimeMillis()
                            if (now - lastDraw >= 18L) {
                                lastDraw = now
                                val snapshot = sb.toString()
                                withContext(Dispatchers.Main) {
                                    renderMarkdown(assistantView, snapshot)
                                    scrollToBottom()
                                }
                            }
                        }
                    }
                }

                if (sb.isEmpty()) sb.append("Yanıt üretilemedi.")
                renderMarkdown(assistantView, sb.toString())
            } catch (t: Throwable) {
                renderMarkdown(assistantView, "**Model hatası:** ${t.message ?: t.javaClass.simpleName}")
            } finally {
                setGenerating(false)
                updateModelStatus()
                scrollToBottom()
            }
        }
    }

    private suspend fun ensureEngine(path: String) {
        if (conversation != null && engine != null) return

        withContext(Dispatchers.IO) {
            releaseEngine()

            var lastError: Throwable? = null
            val attempts = listOf(
                Triple<Backend, Boolean, String>(Backend.GPU(), true, "GPU · MTP"),
                Triple<Backend, Boolean, String>(Backend.GPU(), false, "GPU"),
                Triple<Backend, Boolean, String>(Backend.CPU(), false, "CPU")
            )

            for ((backend, mtp, label) in attempts) {
                try {
                    val spec = SpeculativeDecodingConfig(
                        mode = if (mtp) SpeculativeDecodingMode.ON else SpeculativeDecodingMode.OFF,
                        lenience = 0f
                    )
                    val e = Engine(
                        EngineConfig(
                            modelPath = path,
                            backend = backend,
                            maxNumTokens = 4096,
                            cacheDir = cacheDir.absolutePath,
                            speculativeDecodingConfig = spec
                        )
                    )
                    e.initialize()

                    val c = e.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "Kullanıcının dilinde doğal, doğru ve doğrudan yanıt ver. " +
                                    "Gereksiz giriş yapma, kendini tanıtma ve soruyu tekrar etme. " +
                                    "Tarif ve listelerde kısa başlıklar, boş satırlar ve maddeler kullan. " +
                                    "Ölçüler ve sayısal bilgiler tutarlı ve gerçekçi olsun."
                            ),
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.95,
                                temperature = 0.7,
                                seed = 0
                            ),
                            maxOutputToken = 1024,
                            thinkingConfig = ThinkingConfig(
                                enableThinking = true,
                                thinkingTokenBudget = 256
                            ),
                            speculativeDecodingConfig = spec
                        )
                    )

                    engine = e
                    conversation = c
                    mtpActive = mtp
                    backendName = label
                    return@withContext
                } catch (t: Throwable) {
                    lastError = t
                    try { conversation?.close() } catch (_: Throwable) {}
                    try { engine?.close() } catch (_: Throwable) {}
                    conversation = null
                    engine = null
                }
            }
            throw lastError ?: IllegalStateException("Model açılamadı")
        }
    }

    private fun releaseEngine() {
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        conversation = null
        engine = null
        mtpActive = false
        backendName = ""
    }

    private fun newChat() {
        if (generationJob?.isActive == true) return
        releaseEngine()
        chatColumn.removeAllViews()
        updateModelStatus()
    }

    private fun addUserMessage(text: String) {
        val row = FrameLayout(this)
        val tv = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = rounded(Color.rgb(48, 48, 49), 28)
            maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        }
        row.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            leftMargin = dp(64)
            rightMargin = dp(2)
            topMargin = dp(12)
            bottomMargin = dp(8)
        })
        chatColumn.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scrollToBottom()
    }

    private fun addAssistantMessage(initial: String): TextView {
        val tv = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(242, 244, 247))
            setLineSpacing(0f, 1.22f)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(Color.rgb(29, 43, 59), 22)
        }
        chatColumn.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
            topMargin = dp(4)
            bottomMargin = dp(10)
        })
        tv.text = initial
        scrollToBottom()
        return tv
    }

    private fun renderMarkdown(view: TextView, markdown: String) {
        markwon.setMarkdown(view, markdown)
    }

    private fun setGenerating(generating: Boolean) {
        sendButton.isEnabled = !generating
        modelButton.isEnabled = !generating
        input.isEnabled = !generating
        sendButton.text = if (generating) "■" else "↑"
        if (generating) statusText.text = "Düşünüyor…"
    }

    private fun updateModelStatus() {
        val path = modelPath
        statusText.text = when {
            generationJob?.isActive == true -> "Düşünüyor…"
            path == null -> "Model seçilmedi"
            backendName.isNotBlank() -> "Gemma 4 · $backendName · çevrimdışı"
            else -> "Gemma 4 hazır · çevrimdışı"
        }
    }

    private fun scrollToBottom() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun openModelPicker() {
        if (generationJob?.isActive == true) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_MODEL)
    }

    @Deprecated("Deprecated in Android SDK; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_MODEL || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importModel(uri)
    }

    private fun importModel(uri: Uri) {
        statusText.text = "Model içe aktarılıyor…"
        modelButton.isEnabled = false
        sendButton.isEnabled = false

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val name = queryDisplayName(uri).takeIf { it.endsWith(".litertlm", ignoreCase = true) }
                        ?: "gemma-4-E4B-it.litertlm"
                    val dir = File(filesDir, "models").apply { mkdirs() }
                    val out = File(dir, name)
                    contentResolver.openInputStream(uri).use { inputStream ->
                        requireNotNull(inputStream) { "Model dosyası açılamadı" }
                        FileOutputStream(out).use { output ->
                            inputStream.copyTo(output, 1024 * 1024)
                        }
                    }
                    out
                }
                releaseEngine()
                modelPath = result.absolutePath
                prefs.edit()
                    .putString(KEY_MODEL_PATH, result.absolutePath)
                    .putString(KEY_MODEL_NAME, result.name)
                    .apply()
                statusText.text = "Model hazır · çevrimdışı"
            } catch (t: Throwable) {
                statusText.text = "Model alınamadı: ${t.message ?: "hata"}"
            } finally {
                modelButton.isEnabled = true
                sendButton.isEnabled = true
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "model.litertlm"
        } catch (_: Throwable) {
            "model.litertlm"
        }
    }

    private fun resolveExistingModel(): String? {
        val saved = prefs.getString(KEY_MODEL_PATH, null)
        if (!saved.isNullOrBlank() && File(saved).isFile) return saved

        val roots = listOfNotNull(filesDir, getExternalFilesDir(null))
        for (root in roots) {
            val found = findModel(root, 4)
            if (found != null) {
                prefs.edit()
                    .putString(KEY_MODEL_PATH, found.absolutePath)
                    .putString(KEY_MODEL_NAME, found.name)
                    .apply()
                return found.absolutePath
            }
        }
        return null
    }

    private fun findModel(root: File, depth: Int): File? {
        if (depth < 0 || !root.exists()) return null
        if (root.isFile) return root.takeIf { it.name.endsWith(".litertlm", ignoreCase = true) }
        val children = root.listFiles() ?: return null
        children.firstOrNull { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }?.let { return it }
        for (child in children) {
            if (child.isDirectory) findModel(child, depth - 1)?.let { return it }
        }
        return null
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
