package com.offlineai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
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
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ModelBridge {
    val isLoaded: Boolean
    suspend fun load(modelPath: String): String
    fun streamReply(prompt: String): Flow<String>
    suspend fun resetConversation()
    suspend fun closeModel()
    fun shutdownBlocking()
}

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
    private val thinkingOrange = Color.rgb(211, 122, 61)

    // IMPORTANT: MainActivity has no direct LiteRT dependency. The native/model
    // engine is loaded by reflection only after the user actually needs it.
    private var engine: ModelBridge? = null

    private lateinit var root: LinearLayout
    private lateinit var composer: LinearLayout
    private lateinit var welcome: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = bg
            window.navigationBarColor = bg
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            buildUi()
            restoreModelLabelOnly()
        } catch (t: Throwable) {
            showSafeBootError(t)
        }
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
        root.addView(top, LinearLayout.LayoutParams(-1, dp(60)))

        val menu = roundButton("☰", panel2).apply {
            textSize = 23f
            setOnClickListener { showMenu(this) }
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(52), dp(52)))

        val title = TextView(this).apply {
            text = "Offline AI"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(textColor)
            setPadding(dp(14), 0, 0, 0)
        }
        top.addView(title, LinearLayout.LayoutParams(0, -1, 1f))

        val badge = TextView(this).apply {
            text = "Derin Düşünme"
            textSize = 11f
            setTextColor(thinkingOrange)
            background = strokeRounded(Color.TRANSPARENT, Color.rgb(91, 65, 47), 99f)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        }
        top.addView(badge, LinearLayout.LayoutParams(-2, -2))

        val content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), dp(58))
        }
        content.addView(welcome, FrameLayout.LayoutParams(-1, -1))

        welcome.addView(TextView(this).apply {
            text = "◉"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2))

        welcome.addView(TextView(this).apply {
            text = "Offline AI"
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

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
            setPadding(dp(2), 0, dp(2), dp(20))
        }
        scroll.addView(
            messages,
            ViewGroup.LayoutParams(
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
                } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -1, 1f))

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
                        .setMessage(
                            "Prototype 0.6.2\n" +
                                "Güvenli açılış: model motoru uygulama başlangıcında yüklenmez.\n" +
                                "Derin Düşünme her cevapta aktiftir.\n" +
                                "GPU otomatik denenir; gerekirse CPU ×8 kullanılır."
                        )
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
                val backend = getOrCreateEngine().load(modelFile.absolutePath)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_MODEL_PATH, modelFile.absolutePath)
                    .putString(KEY_MODEL_NAME, name)
                    .apply()
                backend
            }.onSuccess { backend ->
                progress.visibility = View.GONE
                status.text = "✓ $name · $backend"
                Toast.makeText(this@MainActivity, "Model $backend üzerinde hazır.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                progress.visibility = View.GONE
                status.text = "Model yüklenemedi"
                Toast.makeText(
                    this@MainActivity,
                    "Model açılamadı: ${error.message ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Startup safety: only display the saved-model label. Do NOT touch LiteRT,
    // GPU, CPU, native libraries or the model file during Activity startup.
    private fun restoreModelLabelOnly() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null) ?: return
        val name = prefs.getString(KEY_MODEL_NAME, "Yerel model") ?: "Yerel model"
        if (File(path).exists()) {
            status.text = "✓ $name · ilk soruda yüklenir"
        }
    }

    private fun getOrCreateEngine(): ModelBridge {
        engine?.let { return it }
        val clazz = Class.forName("com.offlineai.app.LocalModelEngine")
        val instance = clazz
            .getConstructor(Context::class.java)
            .newInstance(applicationContext) as ModelBridge
        engine = instance
        return instance
    }

    private suspend fun ensureSavedModelLoaded(): String {
        val current = engine
        if (current?.isLoaded == true) return "hazır"

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null) ?: error("Önce bir model seç")
        val name = prefs.getString(KEY_MODEL_NAME, "Yerel model") ?: "Yerel model"
        if (!File(path).exists()) error("Kayıtlı model dosyası bulunamadı")

        status.text = "Model yükleniyor…"
        val backend = getOrCreateEngine().load(path)
        status.text = "✓ $name · $backend"
        return backend
    }

    private fun sendCurrentMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return

        val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_MODEL_PATH, null)
        if (savedPath == null || !File(savedPath).exists()) {
            Toast.makeText(this, "Önce sol üst menüden bir .litertlm model seç.", Toast.LENGTH_LONG).show()
            return
        }

        input.setText("")
        showConversation()
        addMessage(prompt, user = true)
        val answer = addMessage("", user = false)
        val thinkingJob = startThinkingAnimation(answer)
        send.isEnabled = false

        launch {
            var firstChunk = true
            runCatching {
                ensureSavedModelLoaded()
                getOrCreateEngine().streamReply(prompt).collect { chunk ->
                    if (firstChunk) {
                        firstChunk = false
                        thinkingJob.cancel()
                        answer.text = ""
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    }
                    answer.append(chunk)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }.onFailure { error ->
                thinkingJob.cancel()
                answer.setTextColor(textColor)
                answer.text = "Model hatası: ${error.message ?: error.javaClass.simpleName}"
            }

            thinkingJob.cancel()
            if (firstChunk && answer.text.isBlank()) answer.text = "Yanıt üretilemedi."
            send.isEnabled = true
        }
    }

    private fun startThinkingAnimation(target: TextView): Job = launch {
        val frames = arrayOf("●  Düşünüyor", "● ●  Düşünüyor", "● ● ●  Düşünüyor")
        var index = 0
        target.typeface = Typeface.DEFAULT
        target.setTextColor(thinkingOrange)
        while (true) {
            target.text = frames[index % frames.size]
            index++
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            delay(330)
        }
    }

    private fun newChat() {
        messages.removeAllViews()
        scroll.visibility = View.GONE
        welcome.visibility = View.VISIBLE
        val active = engine
        if (active != null) launch { runCatching { active.resetConversation() } }
    }

    private fun removeModel() {
        val active = engine
        launch {
            if (active != null) runCatching { active.closeModel() }
            engine = null
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
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

    private fun addMessage(value: String, user: Boolean): TextView {
        val bubble = TextView(this).apply {
            text = value
            textSize = if (user) 16.5f else 18f
            setTextColor(textColor)
            if (user) {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = rounded(panel2, 20f)
                typeface = Typeface.DEFAULT
            } else {
                setPadding(dp(4), dp(15), dp(4), dp(15))
                background = null
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                setLineSpacing(0f, 1.22f)
            }
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

    private fun showSafeBootError(t: Throwable) {
        val fallback = TextView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(24), dp(48), dp(24), dp(24))
            text = "Offline AI güvenli açılış\n\nArayüz başlatılamadı: ${t.javaClass.simpleName}\n${t.message ?: ""}"
        }
        setContentView(fallback)
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
        runCatching { engine?.shutdownBlocking() }
        engine = null
        cancel()
        super.onDestroy()
    }
}
