from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

# Keep the fast batched renderer but restore thoughtful generation and polished text rendering.
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
            "Bu görseli kısa, düzenli ve doğru biçimde analiz et."
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
                        answer.textSize = 16f
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                    rawAnswer.append(chunk)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastUiUpdate >= 110L) {
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

# Replace the previous formatter with a plain, deterministic renderer that fixes
# markdown artifacts and makes inline lists readable without expensive rich text.
pat = re.compile(r'    private fun formatAssistantText\(raw: String\): String \{.*?\n    \}\n\n    private fun startThinkingAnimation', re.S)
repl = r'''    private fun formatAssistantText(raw: String): String {
        var value = raw.replace("\r\n", "\n")

        // Remove markdown/code markers. The UI itself supplies typography.
        value = value
            .replace("```", "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")

        // Markdown headings -> normal clean lines.
        value = value.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")

        // Small models sometimes emit '*Label:' markers inline. Turn those into
        // clean new lines before removing the remaining emphasis stars.
        value = value.replace(
            Regex("(?<!\\n)\\s*\\*(?=[\\p{L}\\p{N}][^*\\n]{0,45}:)"),
            "\n"
        )
        value = value.replace("*", "")

        // Repair numbered lists that arrive glued to the preceding sentence.
        value = value.replace(
            Regex("(?<!^)(?<!\\n)\\s+(?=\\d{1,2}[.)]\\s+)"),
            "\n"
        )

        // Normalize bullet lines.
        value = value.replace(Regex("(?m)^\\s*[-•]\\s*"), "• ")

        // If compact label sections arrive glued together after punctuation,
        // put each label on its own line.
        value = value.replace(
            Regex("(?<=[.!?])\\s*(?=[\\p{Lu}ÇĞİÖŞÜ][\\p{L}0-9 /&-]{1,35}:)"),
            "\n"
        )

        value = value.replace(Regex("[ \\t]+"), " ")
        value = value.replace(Regex(" *\\n *"), "\n")
        value = value.replace(Regex("\\n{3,}"), "\n\n")
        return value.trim()
    }

    private fun startThinkingAnimation'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("formatAssistantText block not found")

# Match the reference app: medium sans-serif, compact spacing, dark rounded card.
pat = re.compile(r'    private fun addMessage\(value: String, user: Boolean\): TextView \{.*?\n    \}\n\n    private fun displayName', re.S)
repl = '''    private fun addMessage(value: String, user: Boolean): TextView {
        val bubble = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(textColor)
            if (user) {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = rounded(panel2, 20f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setLineSpacing(dp(1).toFloat(), 1.08f)
            } else {
                setPadding(dp(18), dp(17), dp(18), dp(18))
                background = rounded(Color.rgb(31, 43, 56), 18f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setLineSpacing(dp(1).toFloat(), 1.16f)
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

# Thinking stays visible while the model uses a small real thinking budget.
pat = re.compile(r'    private fun startThinkingAnimation\(target: TextView\): Job = launch \{.*?\n    \}\n', re.S)
repl = '''    private fun startThinkingAnimation(target: TextView): Job = launch {
        target.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        target.setTextColor(muted)
        target.textSize = 14f
        target.text = "Düşünüyor"
        while (true) {
            target.alpha = 0.58f
            delay(360)
            target.alpha = 1f
            delay(360)
        }
    }
'''
s, n = pat.subn(lambda _m: repl, s, count=1)
if n != 1:
    raise SystemExit("thinking animation block not found")

s = s.replace("Prototype 0.8.1 · Gemma 4 Hızlı", "Prototype 0.8.2 · Gemma 4 Dengeli")
s = s.replace("Hızlı yanıt modu aktiftir.", "Hızlı ve dengeli düşünme aktiftir.")
s = s.replace("Metin + fotoğraf analizi · Hızlı yanıt", "Metin + fotoğraf analizi · Dengeli düşünme")

p.write_text(s)
print("0.8.2 polished typography + balanced streaming patch applied")
