from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

s = s.replace(
    "    fun streamReply(prompt: String): Flow<String>\n",
    "    fun streamReply(prompt: String, imagePath: String? = null): Flow<String>\n"
)
s = s.replace(
    "        private const val REQUEST_MODEL = 1001\n",
    "        private const val REQUEST_MODEL = 1001\n        private const val REQUEST_IMAGE = 1002\n"
)
s = s.replace('        private const val DEFAULT_MODEL_NAME = "Qwen3 4B"\n', '        private const val DEFAULT_MODEL_NAME = "Gemma 4 E4B"\n')
s = s.replace('        private const val DEFAULT_MODEL_FILE = "qwen3_4b_mixed_int4.litertlm"\n', '        private const val DEFAULT_MODEL_FILE = "gemma-4-E4B-it.litertlm"\n')
s = s.replace(
    '        private const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm"\n',
    '        private const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"\n'
)
s = s.replace('        private const val DEFAULT_MODEL_MIN_FREE_BYTES = 3_500_000_000L\n', '        private const val DEFAULT_MODEL_MIN_FREE_BYTES = 5_000_000_000L\n')

s = s.replace(
    "    private lateinit var progress: ProgressBar\n",
    "    private lateinit var progress: ProgressBar\n"
    "    private lateinit var attachmentBar: LinearLayout\n"
    "    private lateinit var attachmentLabel: TextView\n"
    "    private var selectedImageFile: File? = null\n"
)

s = s.replace('text = "Offline AI"', 'text = "İnternetsiz Yapay Zeka"')
s = s.replace('.setTitle("Offline AI")', '.setTitle("İnternetsiz Yapay Zeka")')
s = s.replace('"Offline AI güvenli açılış\\n\\n', '"İnternetsiz Yapay Zeka güvenli açılış\\n\\n')

start = s.index('        welcome.addView(TextView(this).apply {\n            text = "Model indir"')
end = s.index('        status = TextView(this).apply {', start)
gemma_ui = '''        welcome.addView(TextView(this).apply {
            text = "Gemma 4 E4B"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })

        val gemmaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokeRounded(panel, Color.rgb(65, 65, 65), 18f)
            setPadding(dp(18), dp(14), dp(18), dp(13))
        }
        gemmaCard.addView(TextView(this).apply {
            text = "Gemma 4 E4B · ~3,66 GB"
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })
        gemmaCard.addView(TextView(this).apply {
            text = "Metin + fotoğraf analizi · Derin Düşünme"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        gemmaCard.addView(TextView(this).apply {
            text = "12 GB+ RAM önerilir"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        gemmaCard.addView(TextView(this).apply {
            text = "İndir"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            paint.isUnderlineText = true
            setPadding(0, dp(9), 0, 0)
            setOnClickListener { confirmModelDownload(gemmaModel) }
        })
        welcome.addView(gemmaCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

'''
s = s[:start] + gemma_ui + s[end:]

needle = '        composer = LinearLayout(this).apply {\n'
attachment = '''        attachmentBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = strokeRounded(panel, Color.rgb(68, 68, 68), 16f)
        }
        attachmentLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(textColor)
            maxLines = 1
        }
        attachmentBar.addView(attachmentLabel, LinearLayout.LayoutParams(0, -2, 1f))
        attachmentBar.addView(roundButton("×", panel2).apply {
            textSize = 18f
            setOnClickListener { clearSelectedImage() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(attachmentBar, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })

'''
if needle not in s:
    raise SystemExit("composer marker missing")
s = s.replace(needle, attachment + needle, 1)

needle = '        input = EditText(this).apply {\n'
attach_button = '''        val attach = roundButton("＋", panel2).apply {
            textSize = 24f
            contentDescription = "Fotoğraf ekle"
            setOnClickListener { chooseImage() }
        }
        composer.addView(attach, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
            rightMargin = dp(4)
        })

'''
if needle not in s:
    raise SystemExit("input marker missing")
s = s.replace(needle, attach_button + needle, 1)

s = s.replace('menu.add("AI modeli indir / değiştir")', 'menu.add("Gemma 4 E4B indir / değiştir")')
s = s.replace('"AI modeli indir / değiştir" -> showModelChooser()', '"Gemma 4 E4B indir / değiştir" -> showModelChooser()')

about_old = '''                            "Prototype 0.7.5\\n" +
                                "Hızlı 1.7B veya Güçlü 4B model tek dokunuşla indirilip kurulur.\\n" +
                                "Derin Düşünme her cevapta aktiftir.\\n" +
                                "GPU otomatik denenir; gerekirse CPU ×8 kullanılır."'''
about_new = '''                            "Prototype 0.8.0 · Gemma 4\\n" +
                                "Gemma 4 E4B ile metin ve fotoğraf analizi cihazda çalışır.\\n" +
                                "Derin Düşünme aktiftir.\\n" +
                                "GPU otomatik denenir; gerekirse CPU kullanılır."'''
if about_old not in s:
    raise SystemExit("about block missing")
s = s.replace(about_old, about_new, 1)

model_start = s.index('    private val fastModel = ModelOption(')
auto_start = s.index('    private fun autoModelFile(model: ModelOption): File {', model_start)
model_defs = '''    private val gemmaModel = ModelOption(
        key = "gemma4e4b",
        label = "Gemma 4",
        name = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeLabel = "~3,66 GB",
        minFreeBytes = 5_000_000_000L,
        minValidBytes = 3_300_000_000L,
        powerful = true
    )

    private fun modelByKey(key: String?): ModelOption = gemmaModel

'''
s = s[:model_start] + model_defs + s[auto_start:]

pat = re.compile(r'    private fun showModelChooser\(\) \{.*?\n    \}\n\n    private fun handleModelStatusTap\(\)', re.S)
repl = '''    private fun showModelChooser() {
        val ramGb = totalRamGb()
        val message = if (ramGb < 12) {
            "Bu cihaz yaklaşık $ramGb GB RAM. Gemma 4 E4B için 12 GB veya daha fazlası önerilir. " +
                "Model yine de denenebilir fakat bellek yetersizliği yaşanabilir."
        } else {
            "Gemma 4 E4B · ~3,66 GB\\nMetin, fotoğraf analizi ve Derin Düşünme desteği."
        }

        AlertDialog.Builder(this)
            .setTitle("Gemma 4 E4B")
            .setMessage(message)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("İndir") { _, _ -> confirmModelDownload(gemmaModel) }
            .show()
    }

    private fun handleModelStatusTap()'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("showModelChooser block missing")

pat = re.compile(r'    private fun confirmModelDownload\(model: ModelOption\) \{.*?\n    \}\n\n    private fun startModelDownload', re.S)
repl = '''    private fun confirmModelDownload(model: ModelOption) {
        val targetDir = getExternalFilesDir("models")
        if (targetDir == null) {
            Toast.makeText(this, "Model depolama alanı kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        targetDir.mkdirs()
        if (targetDir.usableSpace < model.minFreeBytes) {
            Toast.makeText(
                this,
                "Gemma 4 E4B için en az 5 GB boş alan gerekiyor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val ramGb = totalRamGb()
        val warning = if (ramGb < 12) {
            "\\n\\n⚠️ Bu cihaz yaklaşık $ramGb GB RAM. Google 12 GB+ öneriyor."
        } else ""

        AlertDialog.Builder(this)
            .setTitle("Gemma 4 E4B indir")
            .setMessage(
                "Model yaklaşık 3,66 GB indirilecek. İndirme bir kez yapılır; sonrasında " +
                    "metin sohbeti ve fotoğraf analizi cihazda çalışır.$warning"
            )
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("İndir") { _, _ -> startModelDownload(model) }
            .show()
    }

    private fun startModelDownload'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("confirmModelDownload block missing")

s = s.replace('setTitle("Offline AI · ${model.label} model")', 'setTitle("İnternetsiz Yapay Zeka · ${model.name}")')

marker = '    private fun chooseModel() {'
image_funcs = r'''    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    private fun selectImage(uri: Uri) {
        launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val originalName = displayName(uri)
                    val ext = originalName.substringAfterLast('.', "jpg")
                        .lowercase()
                        .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "jpg"
                    val dir = File(cacheDir, "images").apply { mkdirs() }
                    val out = File(dir, "selected_${System.currentTimeMillis()}.$ext")
                    contentResolver.openInputStream(uri)?.use { source ->
                        FileOutputStream(out).use { target -> source.copyTo(target) }
                    } ?: error("Görsel açılamadı")
                    out
                }
            }.onSuccess { file ->
                selectedImageFile?.delete()
                selectedImageFile = file
                attachmentLabel.text = "📷 ${file.name}"
                attachmentBar.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Fotoğraf eklendi.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    "Fotoğraf açılamadı: ${error.message ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun clearSelectedImage(deleteFile: Boolean = true) {
        if (deleteFile) selectedImageFile?.delete()
        selectedImageFile = null
        if (::attachmentBar.isInitialized) attachmentBar.visibility = View.GONE
        if (::attachmentLabel.isInitialized) attachmentLabel.text = ""
    }

'''
if marker not in s:
    raise SystemExit("chooseModel marker missing")
s = s.replace(marker, image_funcs + marker, 1)

pat = re.compile(r'    @Deprecated\("Deprecated in Android API, sufficient for prototype"\)\n    override fun onActivityResult\(.*?\n    \}\n\n    private fun importAndLoad', re.S)
repl = '''    @Deprecated("Deprecated in Android API, sufficient for prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_IMAGE -> selectImage(uri)
            REQUEST_MODEL -> {
                val name = displayName(uri)
                if (!name.endsWith(".litertlm", ignoreCase = true)) {
                    Toast.makeText(this, "Lütfen .litertlm model dosyası seç.", Toast.LENGTH_LONG).show()
                    return
                }
                importAndLoad(uri, name)
            }
        }
    }

    private fun importAndLoad'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("onActivityResult block missing")

pat = re.compile(r'    private fun sendCurrentMessage\(\) \{.*?\n    \}\n\n    private fun formatAssistantText', re.S)
repl = '''    private fun sendCurrentMessage() {
        val typedPrompt = input.text.toString().trim()
        val image = selectedImageFile
        if (typedPrompt.isEmpty() && image == null) return

        val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_MODEL_PATH, null)
        if (savedPath == null || !File(savedPath).exists()) {
            Toast.makeText(this, "Önce Gemma 4 E4B modelini indir.", Toast.LENGTH_LONG).show()
            showModelChooser()
            return
        }

        val prompt = if (typedPrompt.isNotEmpty()) {
            typedPrompt
        } else {
            "Bu görseli ayrıntılı olarak analiz et ve gördüklerini Türkçe açıkla."
        }
        val imagePath = image?.absolutePath

        input.setText("")
        clearSelectedImage(deleteFile = false)
        showConversation()
        val userText = if (imagePath != null) "📷 Fotoğraf\\n$prompt" else prompt
        addMessage(userText, user = true)
        val answer = addMessage("", user = false)
        val thinkingJob = startThinkingAnimation(answer)
        send.isEnabled = false

        launch {
            var firstChunk = true
            runCatching {
                ensureSavedModelLoaded()
                val rawAnswer = StringBuilder()
                getOrCreateEngine().streamReply(prompt, imagePath).collect { chunk ->
                    if (chunk.isBlank()) return@collect
                    if (firstChunk) {
                        firstChunk = false
                        thinkingJob.cancel()
                        answer.alpha = 1f
                        answer.text = ""
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    }
                    rawAnswer.append(chunk)
                    answer.text = formatAssistantText(rawAnswer.toString())
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            }.onFailure { error ->
                thinkingJob.cancel()
                answer.setTextColor(textColor)
                answer.text = "Model hatası: ${error.message ?: error.javaClass.simpleName}"
            }

            thinkingJob.cancel()
            imagePath?.let { runCatching { File(it).delete() } }
            if (firstChunk && answer.text.isBlank()) answer.text = "Yanıt üretilemedi."
            send.isEnabled = true
        }
    }

    private fun formatAssistantText'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("sendCurrentMessage block missing")

s = s.replace(
    '''    private fun newChat() {
        messages.removeAllViews()
''',
    '''    private fun newChat() {
        clearSelectedImage()
        messages.removeAllViews()
''',
    1
)

s = s.replace(
    '            runCatching { autoModelFile(fastModel).delete() }\n            runCatching { autoModelFile(powerfulModel).delete() }\n',
    '            runCatching { autoModelFile(gemmaModel).delete() }\n'
)
s = s.replace('status.text = "Henüz model indirilmedi"', 'status.text = "Gemma 4 E4B henüz indirilmedi"')
s = s.replace('"Offline AI"', '"İnternetsiz Yapay Zeka"')
s = s.replace('Offline AI', 'İnternetsiz Yapay Zeka')

p.write_text(s)
print("Gemma 4 E4B + image input patch applied")
