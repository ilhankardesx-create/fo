from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

# Replace the Gemma 4 send block with batched rendering. This avoids rebuilding
# and reformatting the whole answer on every tiny streamed token/chunk.
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

        val prompt = if (typedPrompt.isNotEmpty()) typedPrompt else
            "Bu görseli kısa ve düzenli biçimde analiz et."
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
            val rawAnswer = StringBuilder()
            var lastUiUpdate = 0L
            runCatching {
                ensureSavedModelLoaded()
                getOrCreateEngine().streamReply(prompt, imagePath).collect { chunk ->
                    if (chunk.isBlank()) return@collect
                    if (firstChunk) {
                        firstChunk = false
                        thinkingJob.cancel()
                        answer.alpha = 1f
                        answer.text = ""
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    }
                    rawAnswer.append(chunk)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastUiUpdate >= 55L) {
                        answer.text = formatAssistantText(rawAnswer.toString())
                        lastUiUpdate = now
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
                if (rawAnswer.isNotEmpty()) {
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
    raise SystemExit("sendCurrentMessage block not found")

# Match the compact, readable chat-card look: sans-serif, smaller text,
# slightly bold, tighter line spacing, dark card background.
pat = re.compile(r'    private fun addMessage\(value: String, user: Boolean\): TextView \{.*?\n    \}\n\n    private fun displayName', re.S)
repl = '''    private fun addMessage(value: String, user: Boolean): TextView {
        val bubble = TextView(this).apply {
            text = value
            textSize = if (user) 16f else 16f
            setTextColor(textColor)
            if (user) {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = rounded(panel2, 20f)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setLineSpacing(dp(1).toFloat(), 1.08f)
            } else {
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = rounded(Color.rgb(31, 43, 56), 18f)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setLineSpacing(dp(1).toFloat(), 1.12f)
            }
        }
        val lp = LinearLayout.LayoutParams(
            if (user) dp(310) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = dp(10)
            if (!user) {
                leftMargin = dp(12)
                rightMargin = dp(2)
            }
        }
        messages.addView(bubble, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun displayName'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("addMessage block not found")

# Keep the waiting label quiet and compact. The model itself no longer performs
# hidden thinking, so this is only a local loading indicator until first token.
pat = re.compile(r'    private fun startThinkingAnimation\(target: TextView\): Job = launch \{.*?\n    \}\n', re.S)
repl = '''    private fun startThinkingAnimation(target: TextView): Job = launch {
        target.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        target.setTextColor(muted)
        target.textSize = 14f
        target.text = "Düşünüyor"
        while (true) {
            target.alpha = 0.55f
            delay(350)
            target.alpha = 1f
            delay(350)
        }
    }
'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("thinking animation block not found")

s = s.replace("Prototype 0.8.0 · Gemma 4", "Prototype 0.8.1 · Gemma 4 Hızlı")
s = s.replace("Derin Düşünme aktiftir.", "Hızlı yanıt modu aktiftir.")
s = s.replace("Metin + fotoğraf analizi · Derin Düşünme", "Metin + fotoğraf analizi · Hızlı yanıt")

p.write_text(s)
print("0.8.1 fast UI + batched streaming patch applied")
