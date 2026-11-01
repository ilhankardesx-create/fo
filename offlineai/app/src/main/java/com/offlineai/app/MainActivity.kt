package com.offlineai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : Activity(), CoroutineScope by MainScope() {

    companion object {
        private const val REQUEST_MODEL = 1001
        private const val PREFS = "offline_ai_prefs"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_THINKING = "thinking_enabled"
    }

    private val bg = Color.rgb(18, 18, 18)
    private val panel = Color.rgb(34, 34, 34)
    private val panel2 = Color.rgb(47, 47, 47)
    private val textColor = Color.rgb(245, 245, 242)
    private val muted = Color.rgb(170, 170, 166)

    private lateinit var engine: LocalModelEngine
    private lateinit var root: LinearLayout
    private lateinit var composer: LinearLayout
    private lateinit var welcome: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var baseScrollBottomPadding = 0
    private var thinkingEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        thinkingEnabled = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_THINKING, false)

        engine = LocalModelEngine(this, thinkingEnabled)
        buildUi()
        installImeHandling()
        restoreModel()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(16))
            setBackgroundColor(bg)
        }
        setContentView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(62)))

        val menu = roundButton("☰", panel2).apply {
            textSize = 24f
            setOnClickListener { showMenu(this) }
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(52), dp(52)))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), dp(70))
        }
        content.addView(welcome, FrameLayout.LayoutParams(-1, -1))

        welcome.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_offline_ai_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Offline AI logo"
        }, LinearLayout.LayoutParams(dp(84), dp(84)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        welcome.addView(TextView(this).apply {
            text = "Offline AI"
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        welcome.addView(TextView(this).apply {
            text = "İnternetsiz. Cihazın içinde."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        status = TextView(this).apply {
            text = "Model seçilmedi"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = rounded(panel2, 99f)
            setPadding(dp(18), dp(11), dp(18), dp(11))
        }
        welcome.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(26) })

        progress = ProgressBar(this).apply { visibility = View.GONE }
        welcome.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply { topMargin = dp(18) })

        scroll = ScrollView(this).apply {
            visibility = View.GONE
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(12))
        }
        baseScrollBottomPadding = scroll.paddingBottom

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), 0, dp(2), dp(16))
        }
        scroll.addView(
            messages,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(7), dp(5))
            background = strokeRounded(panel, Color.rgb(68, 68, 68), 99f)
        }
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(70)))

        input = EditText(this).apply {
            hint = "Bir şey sorun…"
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 17f
            background = null
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(10), 0, dp(10), 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendCurrentMessage()
                    true
                } else {
                    false
                }
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -1, 1f))

        composer.addView(roundButton("●", panel).apply {
            setTextColor(muted)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Sesli giriş sonraki sürümde.", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        send = roundButton("↑", Color.WHITE).apply {
            textSize = 25f
            setTextColor(Color.BLACK)
            setOnClickListener { sendCurrentMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(56), dp(56)))
    }

    private fun installImeHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
                val navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                val keyboardShift = if (imeBottom > navBottom) imeBottom - navBottom else 0

                composer.translationY = -keyboardShift.toFloat()
                scroll.setPadding(
                    scroll.paddingLeft,
                    scroll.paddingTop,
                    scroll.paddingRight,
                    baseScrollBottomPadding + keyboardShift + if (keyboardShift > 0) dp(78) else 0
                )

                if (keyboardShift > 0) {
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
                insets
            }
            root.requestApplyInsets()
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Model seç / içe aktar")
            menu.add("Yeni sohbet")
            menu.add(if (thinkingEnabled) "Derin düşünme: Açık ✓" else "Derin düşünme: Kapalı")
            menu.add("Modeli kaldır")
            menu.add("Hakkında")
            setOnMenuItemClickListener { item ->
                when {
                    item.title.toString() == "Model seç / içe aktar" -> chooseModel()
                    item.title.toString() == "Yeni sohbet" -> newChat()
                    item.title.toString().startsWith("Derin düşünme:") -> toggleThinking()
                    item.title.toString() == "Modeli kaldır" -> removeModel()
                    item.title.toString() == "Hakkında" -> AlertDialog.Builder(this@MainActivity)
                        .setTitle("Offline AI")
                        .setMessage(
                            "Prototype 0.5\n" +
                                "GPU otomatik denenir; desteklenmezse 8 thread CPU'ya geçer.\n" +
                                "Derin düşünme varsayılan olarak kapalı."
                        )
                        .setPositiveButton("Tamam", null)
                        .show()
                }
                true
            }
            show()
        }
    }

    private fun toggleThinking() {
        thinkingEnabled = !thinkingEnabled
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_THINKING, thinkingEnabled)
            .apply()
        engine.setThinking(thinkingEnabled)
        Toast.makeText(
            this,
            if (thinkingEnabled) "Derin düşünme açıldı." else "Derin düşünme kapatıldı.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun chooseModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_MODEL)
    }

    @Deprecated("Deprecated in Android API, sufficient for prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val name = displayName(uri)
        if (!name.endsWith(".litertlm", ignoreCase = true)) {
            Toast.makeText(this, "Lütfen .litertlm model dosyası seç.", Toast.LENGTH_LONG).show()
            return
        }
        importAndLoad(uri, name)
    }

    private fun importAndLoad(uri: Uri, name: String) {
        progress.visibility = View.VISIBLE
        status.text = "En hızlı motor seçiliyor…"
        launch {
            runCatching {
                val modelFile = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "models").apply { mkdirs() }
                    val out = File(dir, "model.litertlm")
                    contentResolver.openInputStream(uri)!!.use { source ->
                        FileOutputStream(out).use { target -> source.copyTo(target) }
                    }
                    out
                }
                val backend = engine.load(modelFile.absolutePath)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_MODEL_PATH, modelFile.absolutePath)
                    .putString(KEY_MODEL_NAME, name)
                    .apply()
                backend
            }.onSuccess { backend ->
                progress.visibility = View.GONE
                status.text = "✓ $name · $backend"
                Toast.makeText(
                    this@MainActivity,
                    if (backend == "GPU") {
                        "Model GPU üzerinde hazır."
                    } else {
                        "GPU açılamadı; model 8 thread CPU modunda hazır."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure {
                progress.visibility = View.GONE
                status.text = "Model yüklenemedi"
                Toast.makeText(
                    this@MainActivity,
                    "Model ne GPU ne CPU ile açılabildi.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreModel() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null) ?: return
        val name = prefs.getString(KEY_MODEL_NAME, "Yerel model") ?: "Yerel model"
        val file = File(path)
        if (!file.exists()) return
        progress.visibility = View.VISIBLE
        status.text = "En hızlı motor seçiliyor…"
        launch {
            runCatching { engine.load(path) }
                .onSuccess { backend ->
                    progress.visibility = View.GONE
                    status.text = "✓ $name · $backend"
                    if (backend == "CPU ×8") {
                        Toast.makeText(
                            this@MainActivity,
                            "GPU bu cihazda açılamadı; otomatik 8 thread CPU'ya geçildi.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .onFailure {
                    progress.visibility = View.GONE
                    status.text = "Model tekrar seçilmeli"
                    Toast.makeText(
                        this@MainActivity,
                        "Model açılamadı. Sol üst menüden tekrar seç.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun sendCurrentMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        if (!engine.isLoaded) {
            Toast.makeText(
                this,
                "Önce sol üst menüden bir .litertlm model seç.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        input.setText("")
        showConversation()
        addBubble(prompt, true)
        val answer = addBubble("", false)
        val typingJob = startTypingAnimation(answer)
        send.isEnabled = false

        launch {
            var firstChunk = true
            runCatching {
                engine.streamReply(prompt).collect { chunk ->
                    if (firstChunk) {
                        firstChunk = false
                        typingJob.cancel()
                        answer.text = ""
                    }
                    answer.append(chunk)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }.onFailure {
                typingJob.cancel()
                answer.text = "Hata: ${it.message ?: "yanıt üretilemedi"}"
            }

            typingJob.cancel()
            if (firstChunk && answer.text.isBlank()) {
                answer.text = "Yanıt üretilemedi."
            }
            send.isEnabled = true
        }
    }

    private fun startTypingAnimation(target: TextView): Job = launch {
        val frames = arrayOf("●", "● ●", "● ● ●")
        var index = 0
        while (true) {
            target.text = frames[index % frames.size]
            index++
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            delay(320)
        }
    }

    private fun newChat() {
        messages.removeAllViews()
        scroll.visibility = View.GONE
        welcome.visibility = View.VISIBLE
        launch { runCatching { engine.resetConversation() } }
    }

    private fun removeModel() {
        launch {
            runCatching { engine.closeModel() }
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_MODEL_PATH)
                .remove(KEY_MODEL_NAME)
                .apply()
            File(filesDir, "models/model.litertlm").delete()
            status.text = "Model seçilmedi"
            newChat()
        }
    }

    private fun showConversation() {
        welcome.visibility = View.GONE
        scroll.visibility = View.VISIBLE
    }

    private fun addBubble(value: String, user: Boolean): TextView {
        val bubble = TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(textColor)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(if (user) panel2 else Color.TRANSPARENT, 20f)
        }
        val lp = LinearLayout.LayoutParams(
            if (user) dp(310) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = dp(10)
        }
        messages.addView(bubble, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return "model.litertlm"
    }

    private fun roundButton(label: String, color: Int) = Button(this).apply {
        text = label
        setTextColor(textColor)
        textSize = 20f
        gravity = Gravity.CENTER
        background = rounded(color, 99f)
        setPadding(0, 0, 0, 0)
        isAllCaps = false
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun strokeRounded(color: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        engine.shutdownBlocking()
        cancel()
        super.onDestroy()
    }
}

class LocalModelEngine(
    private val context: android.content.Context,
    initialThinkingEnabled: Boolean = false,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    @Volatile
    private var busy = false

    @Volatile
    private var thinkingEnabled = initialThinkingEnabled

    @Volatile
    private var activeBackend = ""

    val isLoaded: Boolean
        get() = engine != null && conversation != null

    fun setThinking(enabled: Boolean) {
        thinkingEnabled = enabled
    }

    suspend fun load(modelPath: String): String = withContext(dispatcher) {
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
                val cpuLoaded = createEngine(modelPath, Backend.CPU(threadCount = 8))
                activeBackend = "CPU ×8"
                cpuLoaded
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

    fun streamReply(prompt: String): Flow<String> = channelFlow {
        withContext(dispatcher) {
            if (busy) error("Model şu anda yanıt üretiyor")
            val conv = conversation ?: error("Model yüklenmedi")
            busy = true
            try {
                conv.sendMessageAsync(
                    prompt,
                    maxOutputToken = if (thinkingEnabled) 1024 else 768,
                    thinkingConfig = ThinkingConfig(
                        enableThinking = thinkingEnabled,
                        thinkingTokenBudget = if (thinkingEnabled) 512 else -1
                    )
                ).collect { send(it.toString()) }
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

    fun shutdownBlocking() {
        runBlocking { runCatching { closeModel() } }
        dispatcher.close()
        executor.shutdown()
    }
}
