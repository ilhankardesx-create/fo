package com.offlineai.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
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
        private const val PICK_IMAGE = 1002
        private const val PICK_FILE = 1003
        private const val PREFS = "offline_ai_prefs"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_MODEL_NAME = "model_name"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val markwon by lazy { Markwon.create(this) }

    private lateinit var rootView: LinearLayout
    private lateinit var chatColumn: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var plusButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var attachmentPreview: LinearLayout
    private lateinit var attachmentThumb: ImageView
    private lateinit var attachmentName: TextView

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var generationJob: Job? = null
    private var modelPath: String? = null
    private var mtpActive = false
    private var backendName = ""
    private var engineVisionEnabled = false
    private var isGenerating = false

    private var pendingImagePath: String? = null
    private var pendingFileUri: Uri? = null
    private var pendingFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(15, 15, 16)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        buildUi()
        applyWindowInsets()

        modelPath = resolveExistingModel()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 15, 16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(Color.WHITE)
            background = rounded(Color.rgb(46, 46, 48), 100)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = "Ayarlar"
            setOnClickListener { showSettingsPopup(this) }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(54), dp(54)))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        rootView.addView(header)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        chatColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        scroll.addView(
            chatColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rootView.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(33, 33, 34), 36)
            setPadding(dp(8), dp(7), dp(7), dp(7))
        }

        attachmentPreview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(4), dp(6), dp(9))
        }

        attachmentThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(48, 48, 50), 16)
            clipToOutline = true
            contentDescription = "Ek önizleme"
        }
        attachmentPreview.addView(
            attachmentThumb,
            LinearLayout.LayoutParams(dp(78), dp(78)).apply { rightMargin = dp(10) }
        )

        attachmentName = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        attachmentPreview.addView(
            attachmentName,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val removeAttachment = Button(this).apply {
            text = "×"
            textSize = 24f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(55, 55, 58), 100)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            contentDescription = "Eki kaldır"
            setOnClickListener { clearAttachment() }
        }
        attachmentPreview.addView(
            removeAttachment,
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(8) }
        )
        composer.addView(
            attachmentPreview,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        plusButton = Button(this).apply {
            text = "+"
            textSize = 27f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(46, 46, 48), 100)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            contentDescription = "Ek ekle"
            setOnClickListener { showAttachmentPopup(this) }
        }
        inputRow.addView(plusButton, LinearLayout.LayoutParams(dp(52), dp(52)))

        input = EditText(this).apply {
            hint = "Bir şey sorun…"
            setHintTextColor(Color.rgb(170, 170, 174))
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine(false)
            maxLines = 5
            background = null
            setPadding(dp(14), dp(6), dp(10), dp(6))
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) scrollToBottom() }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateSendButtonState()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        sendButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_up)
            setColorFilter(Color.WHITE)
            background = rounded(Color.rgb(63, 63, 66), 100)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(15), dp(15), dp(15), dp(15))
            contentDescription = "Gönder"
            setOnClickListener { sendCurrentMessage() }
        }
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(58), dp(58)))
        composer.addView(
            inputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        rootView.addView(
            composer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        rootView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom && input.hasFocus()) scrollToBottom()
        }

        setContentView(rootView)
        updateSendButtonState()
    }

    private fun applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootView.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(
                    dp(14),
                    bars.top + dp(10),
                    dp(14),
                    maxOf(bars.bottom, ime.bottom) + dp(10)
                )
                insets
            }
            rootView.requestApplyInsets()
        } else {
            rootView.setPadding(dp(14), dp(10), dp(14), dp(10))
        }
    }

    private fun showAttachmentPopup(anchor: View) {
        if (isGenerating) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(48, 48, 50), 26)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        lateinit var popup: PopupWindow
        content.addView(popupRow(R.drawable.ic_photo, "Fotoğraflar") {
            popup.dismiss()
            openImagePicker()
        })
        content.addView(popupRow(R.drawable.ic_file, "Dosyalar") {
            popup.dismiss()
            openFilePicker()
        })

        popup = PopupWindow(content, dp(228), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(14).toFloat()
        }

        content.measure(
            View.MeasureSpec.makeMeasureSpec(dp(228), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val maxX = resources.displayMetrics.widthPixels - dp(228) - dp(12)
        val x = location[0].coerceIn(dp(12), maxX.coerceAtLeast(dp(12)))
        val y = (location[1] - content.measuredHeight - dp(10)).coerceAtLeast(dp(12))
        popup.showAtLocation(rootView, Gravity.NO_GRAVITY, x, y)
    }

    private fun popupRow(iconRes: Int, label: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.TRANSPARENT, 20)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val iconWrap = FrameLayout(this@MainActivity).apply {
                background = rounded(Color.rgb(66, 66, 69), 100)
            }
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            iconWrap.addView(
                icon,
                FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
            )
            addView(iconWrap, LinearLayout.LayoutParams(dp(48), dp(48)))

            val text = TextView(this@MainActivity).apply {
                this.text = label
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            addView(text, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
    }

    private fun showSettingsPopup(anchor: View) {
        if (isGenerating) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(48, 48, 50), 26)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        lateinit var popup: PopupWindow
        val modelName = prefs.getString(KEY_MODEL_NAME, null)
        content.addView(settingsRow("Model seç", modelName ?: "Model seçilmedi") {
            popup.dismiss()
            openModelPicker()
        })
        content.addView(settingsRow("Yeni sohbet", "Mevcut konuşmayı temizle") {
            popup.dismiss()
            newChat()
        })

        popup = PopupWindow(content, dp(260), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(14).toFloat()
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.showAtLocation(
            rootView,
            Gravity.NO_GRAVITY,
            dp(14),
            location[1] + anchor.height + dp(8)
        )
    }

    private fun settingsRow(title: String, subtitle: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.rgb(176, 176, 182))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            })
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_IMAGE)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_FILE)
    }

    private fun handleImagePicked(uri: Uri) {
        takeReadPermission(uri)
        val displayName = queryDisplayName(uri, "fotoğraf")
        scope.launch {
            try {
                val localFile = withContext(Dispatchers.IO) {
                    val ext = displayName.substringAfterLast('.', "jpg")
                        .takeIf { it.length in 2..5 } ?: "jpg"
                    val dir = File(filesDir, "attachments").apply { mkdirs() }
                    val out = File(dir, "img_${System.currentTimeMillis()}.$ext")
                    contentResolver.openInputStream(uri).use { source ->
                        requireNotNull(source) { "Fotoğraf açılamadı" }
                        FileOutputStream(out).use { target -> source.copyTo(target, 512 * 1024) }
                    }
                    out
                }
                pendingImagePath = localFile.absolutePath
                pendingFileUri = null
                pendingFileName = displayName
                showImagePreview(localFile, displayName)
            } catch (t: Throwable) {
                Toast.makeText(this@MainActivity, "Fotoğraf alınamadı: ${t.message ?: "hata"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleFilePicked(uri: Uri) {
        takeReadPermission(uri)
        pendingImagePath = null
        pendingFileUri = uri
        pendingFileName = queryDisplayName(uri, "dosya")

        attachmentThumb.setImageResource(R.drawable.ic_file)
        attachmentThumb.setColorFilter(Color.WHITE)
        attachmentThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
        attachmentThumb.setPadding(dp(18), dp(18), dp(18), dp(18))
        attachmentName.text = pendingFileName
        attachmentPreview.visibility = View.VISIBLE
        updateSendButtonState()
    }

    private fun showImagePreview(file: File, displayName: String) {
        attachmentThumb.clearColorFilter()
        attachmentThumb.setPadding(0, 0, 0, 0)
        attachmentThumb.scaleType = ImageView.ScaleType.CENTER_CROP
        attachmentThumb.setImageURI(Uri.fromFile(file))
        attachmentName.text = displayName
        attachmentPreview.visibility = View.VISIBLE
        updateSendButtonState()
    }

    private fun clearAttachment() {
        pendingImagePath = null
        pendingFileUri = null
        pendingFileName = null
        attachmentThumb.setImageDrawable(null)
        attachmentName.text = ""
        attachmentPreview.visibility = View.GONE
        updateSendButtonState()
    }

    private fun takeReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun sendCurrentMessage() {
        val typedPrompt = input.text.toString().trim()
        val imagePath = pendingImagePath
        val genericFileName = if (pendingFileUri != null && imagePath == null) pendingFileName else null

        if (isGenerating) return
        if (typedPrompt.isEmpty() && imagePath == null && genericFileName == null) return
        if (typedPrompt.isEmpty() && genericFileName != null) {
            Toast.makeText(this, "Dosyayla birlikte bir soru yaz", Toast.LENGTH_SHORT).show()
            return
        }

        val path = modelPath ?: resolveExistingModel()
        if (path == null) {
            Toast.makeText(this, "Önce ayarlardan modeli seç", Toast.LENGTH_SHORT).show()
            showSettingsPopup(settingsButton)
            return
        }
        modelPath = path

        val promptForModel = typedPrompt.ifEmpty { "Bu görseli açıkla." }
        addUserMessage(typedPrompt, imagePath, genericFileName)
        input.setText("")
        clearAttachment()

        val assistantView = addAssistantMessage("Düşünüyor…")
        setGenerating(true)

        generationJob = scope.launch {
            try {
                ensureEngine(path, wantsVision = imagePath != null)
                val sb = StringBuilder()
                var lastDraw = 0L

                withContext(Dispatchers.Default) {
                    val activeConversation = conversation ?: error("Sohbet başlatılamadı")
                    val flow = if (imagePath != null) {
                        activeConversation.sendMessageAsync(
                            Contents.of(
                                Content.ImageFile(imagePath),
                                Content.Text(promptForModel)
                            )
                        )
                    } else {
                        activeConversation.sendMessageAsync(promptForModel)
                    }

                    flow.collect { chunk ->
                        val delta = chunk.toString()
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
                scrollToBottom()
            }
        }
    }

    private suspend fun ensureEngine(path: String, wantsVision: Boolean = false) {
        if (conversation != null && engine != null && (!wantsVision || engineVisionEnabled)) return

        withContext(Dispatchers.IO) {
            releaseEngine()

            var lastError: Throwable? = null
            val attempts = listOf(
                Triple(Backend.GPU(), true, "GPU · MTP"),
                Triple(Backend.GPU(), false, "GPU"),
                Triple(Backend.CPU(), false, "CPU")
            )

            for ((backend, mtp, label) in attempts) {
                try {
                    val spec = SpeculativeDecodingConfig(
                        mode = if (mtp) SpeculativeDecodingMode.ON else SpeculativeDecodingMode.OFF
                    )
                    val e = Engine(
                        EngineConfig(
                            modelPath = path,
                            backend = backend,
                            visionBackend = if (wantsVision) backend else null,
                            maxNumTokens = 4096,
                            maxNumImages = if (wantsVision) 1 else null,
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
                    engineVisionEnabled = wantsVision
                    return@withContext
                } catch (t: Throwable) {
                    lastError = t
                    try { conversation?.close() } catch (_: Throwable) {}
                    try { engine?.close() } catch (_: Throwable) {}
                    conversation = null
                    engine = null
                    engineVisionEnabled = false
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
        engineVisionEnabled = false
    }

    private fun newChat() {
        if (isGenerating) return
        releaseEngine()
        chatColumn.removeAllViews()
        clearAttachment()
        input.setText("")
    }

    private fun addUserMessage(text: String, imagePath: String?, genericFileName: String?) {
        val row = FrameLayout(this)
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(48, 48, 49), 28)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        if (imagePath != null) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.rgb(58, 58, 60), 20)
                clipToOutline = true
                setImageURI(Uri.fromFile(File(imagePath)))
            }
            bubble.addView(image, LinearLayout.LayoutParams(dp(228), dp(178)))
        } else if (genericFileName != null) {
            val fileRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.rgb(61, 61, 64), 18)
                setPadding(dp(10), dp(9), dp(12), dp(9))
            }
            val fileIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_file)
                setColorFilter(Color.WHITE)
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            fileRow.addView(fileIcon, LinearLayout.LayoutParams(dp(34), dp(34)))
            fileRow.addView(TextView(this).apply {
                this.text = genericFileName
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            bubble.addView(fileRow, LinearLayout.LayoutParams(dp(228), ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (text.isNotEmpty()) {
            bubble.addView(TextView(this).apply {
                this.text = text
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), if (imagePath != null || genericFileName != null) dp(10) else dp(5), dp(10), dp(5))
                maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            })
        }

        row.addView(
            bubble,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END
            ).apply {
                leftMargin = dp(64)
                rightMargin = dp(2)
                topMargin = dp(12)
                bottomMargin = dp(8)
            }
        )
        chatColumn.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
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
        chatColumn.addView(
            tv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
                topMargin = dp(4)
                bottomMargin = dp(10)
            }
        )
        tv.text = initial
        scrollToBottom()
        return tv
    }

    private fun renderMarkdown(view: TextView, markdown: String) {
        markwon.setMarkdown(view, markdown)
    }

    private fun setGenerating(generating: Boolean) {
        isGenerating = generating
        settingsButton.isEnabled = !generating
        plusButton.isEnabled = !generating
        input.isEnabled = !generating
        sendButton.isEnabled = !generating
        sendButton.alpha = if (generating) 0.6f else 1f
        updateSendButtonState()
    }

    private fun updateSendButtonState() {
        if (!::sendButton.isInitialized) return
        val hasContent = input.text?.isNotBlank() == true || pendingImagePath != null || pendingFileUri != null
        val color = if (hasContent || isGenerating) Color.rgb(43, 108, 238) else Color.rgb(63, 63, 66)
        sendButton.background = rounded(color, 100)
    }

    private fun scrollToBottom() {
        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
            scroll.postDelayed({ scroll.fullScroll(View.FOCUS_DOWN) }, 80L)
        }
    }

    private fun openModelPicker() {
        if (isGenerating) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_MODEL)
    }

    @Deprecated("Deprecated in Android SDK; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            IMPORT_MODEL -> importModel(uri)
            PICK_IMAGE -> handleImagePicked(uri)
            PICK_FILE -> handleFilePicked(uri)
        }
    }

    private fun importModel(uri: Uri) {
        Toast.makeText(this, "Model içe aktarılıyor…", Toast.LENGTH_SHORT).show()
        settingsButton.isEnabled = false
        sendButton.isEnabled = false

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val name = queryDisplayName(uri, "gemma-4-E4B-it.litertlm").takeIf {
                        it.endsWith(".litertlm", ignoreCase = true)
                    } ?: "gemma-4-E4B-it.litertlm"
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
                Toast.makeText(this@MainActivity, "Model hazır", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(this@MainActivity, "Model alınamadı: ${t.message ?: "hata"}", Toast.LENGTH_LONG).show()
            } finally {
                settingsButton.isEnabled = true
                sendButton.isEnabled = true
                updateSendButtonState()
            }
        }
    }

    private fun queryDisplayName(uri: Uri, fallback: String): String {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: fallback
        } catch (_: Throwable) {
            fallback
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
        if (root.isFile) {
            return root.takeIf { it.name.endsWith(".litertlm", ignoreCase = true) }
        }
        val children = root.listFiles() ?: return null
        children.firstOrNull {
            it.isFile && it.name.endsWith(".litertlm", ignoreCase = true)
        }?.let { return it }
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
