package com.offlineai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    }

    private val bg = Color.rgb(18, 18, 18)
    private val panel = Color.rgb(34, 34, 34)
    private val panel2 = Color.rgb(47, 47, 47)
    private val textColor = Color.rgb(245, 245, 242)
    private val muted = Color.rgb(170, 170, 166)
    private val accent = Color.rgb(227, 126, 94)

    private lateinit var engine: LocalModelEngine
    private lateinit var welcome: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        engine = LocalModelEngine(this)
        buildUi()
        restoreModel()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
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

        welcome.addView(TextView(this).apply {
            text = "✳"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }, LinearLayout.LayoutParams(-1, dp(78)))

        welcome.addView(TextView(this).apply {
            text = "Offline AI"
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2))

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

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(5))
            background = strokeRounded(panel, Color.rgb(68, 68, 68), 99f)
        }
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(70)))

        composer.addView(roundButton("+", panel2).apply {
            textSize = 29f
            setOnClickListener { chooseModel() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))

        input = EditText(this).apply {
            hint = "Bir şey sorun…"
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 17f
            background = null
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), 0, dp(10), 0)
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

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Model seç / içe aktar")
            menu.add("Yeni sohbet")
            menu.add("Modeli kaldır")
            menu.add("Hakkında")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Model seç / içe aktar" -> chooseModel()
                    "Yeni sohbet" -> newChat()
                    "Modeli kaldır" -> removeModel()
                    "Hakkında" -> AlertDialog.Builder(this@MainActivity)
                        .setTitle("Offline AI")
                        .setMessage("Prototype 0.2\nLiteRT-LM ile cihaz üzerinde yerel yapay zekâ.")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
                true
            }
            show()
        }
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
        status.text = "Model hazırlanıyor…"
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
                engine.load(modelFile.absolutePath)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_MODEL_PATH, modelFile.absolutePath)
                    .putString(KEY_MODEL_NAME, name)
                    .apply()
            }.onSuccess {
                progress.visibility = View.GONE
                status.text = "✓ $name"
                Toast.makeText(this@MainActivity, "Model hazır.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                progress.visibility = View.GONE
                status.text = "Model yüklenemedi"
                Toast.makeText(this@MainActivity, it.message ?: "Model hatası", Toast.LENGTH_LONG).show()
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
        status.text = "Model açılıyor…"
        launch {
            runCatching { engine.load(path) }
                .onSuccess {
                    progress.visibility = View.GONE
                    status.text = "✓ $name"
                }
                .onFailure {
                    progress.visibility = View.GONE
                    status.text = "Model tekrar seçilmeli"
                }
        }
    }

    private fun sendCurrentMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        if (!engine.isLoaded) {
            Toast.makeText(this, "Önce + ile bir .litertlm model seç.", Toast.LENGTH_LONG).show()
            return
        }

        input.setText("")
        showConversation()
        addBubble(prompt, true)
        val answer = addBubble("", false)
        send.isEnabled = false

        launch {
            runCatching {
                engine.streamReply(prompt).collect { chunk ->
                    answer.append(chunk)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }.onFailure {
                answer.text = "Hata: ${it.message ?: "yanıt üretilemedi"}"
            }
            send.isEnabled = true
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
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
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

class LocalModelEngine(private val context: android.content.Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null
    @Volatile
    private var busy = false

    val isLoaded: Boolean
        get() = engine != null && conversation != null

    suspend fun load(modelPath: String) = withContext(dispatcher) {
        mutex.withLock {
            if (loadedPath == modelPath && isLoaded) return@withLock
            releaseInternal()

            val candidate = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            )
            candidate.initialize()
            val candidateConversation = candidate.createConversation()
            engine = candidate
            conversation = candidateConversation
            loadedPath = modelPath
        }
    }

    fun streamReply(prompt: String): Flow<String> = channelFlow {
        withContext(dispatcher) {
            if (busy) error("Model şu anda yanıt üretiyor")
            val conv = conversation ?: error("Model yüklenmedi")
            busy = true
            try {
                conv.sendMessageAsync(prompt).collect { send(it.toString()) }
            } finally {
                busy = false
            }
        }
    }

    suspend fun resetConversation() = withContext(dispatcher) {
        mutex.withLock {
            stopGenerationIfNeeded()
            conversation?.close()
            conversation = engine?.createConversation()
        }
    }

    suspend fun closeModel() = withContext(dispatcher) {
        mutex.withLock { releaseInternal() }
    }

    private suspend fun releaseInternal() {
        stopGenerationIfNeeded()
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        loadedPath = null
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
