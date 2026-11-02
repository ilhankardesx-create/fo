from pathlib import Path

path = Path("offline-ai/app/src/main/java/com/offlineai/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


def replace_between(start_marker: str, end_marker: str, replacement: str, label: str) -> None:
    global text
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    text = text[:start] + replacement + text[end:]


replace_once(
    'import android.content.Intent\n',
    'import android.content.Intent\nimport android.content.ClipData\nimport android.content.ClipboardManager\n',
    'clipboard imports',
)
replace_once(
    'import io.noties.markwon.Markwon\n',
    'import io.noties.markwon.Markwon\nimport org.json.JSONArray\nimport org.json.JSONObject\n',
    'json imports',
)
replace_once(
    '        private const val KEY_LIGHT_THEME = "light_theme"\n',
    '        private const val KEY_LIGHT_THEME = "light_theme"\n        private const val KEY_CHAT_HISTORY = "chat_history_v1"\n',
    'history pref key',
)
replace_once(
    '    private lateinit var samsungPerformanceBoost: SamsungPerformanceBoost\n',
    '    private lateinit var samsungPerformanceBoost: SamsungPerformanceBoost\n    private var activeChatId = 0L\n    private var restoredHistoryContext: String? = null\n',
    'history fields',
)

# Open-theme panel should stand out a little more from the page background.
replace_once(
    '            Color.rgb(48, 48, 50), Color.rgb(250, 250, 252) ->\n                if (light) Color.rgb(250, 250, 252) else Color.rgb(48, 48, 50)\n',
    '            Color.rgb(48, 48, 50), Color.rgb(242, 242, 245) ->\n                if (light) Color.rgb(242, 242, 245) else Color.rgb(48, 48, 50)\n',
    'light settings panel contrast',
)
replace_once(
    '            Color.rgb(176, 176, 182), Color.rgb(98, 98, 104) ->\n                if (light) Color.rgb(98, 98, 104) else Color.rgb(176, 176, 182)\n',
    '            Color.rgb(176, 176, 182), Color.rgb(82, 82, 88) ->\n                if (light) Color.rgb(82, 82, 88) else Color.rgb(176, 176, 182)\n',
    'light subtitle contrast',
)

settings_function = r'''    private fun showSettingsPopup(anchor: View) {
        if (isGenerating) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(48, 48, 50), 26)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(7).toFloat()
        }

        lateinit var popup: PopupWindow
        val modelName = prefs.getString(KEY_MODEL_NAME, null)

        content.addView(settingsRow("Model indir", "Hızlı, Akıllı ve 12B modeller") {
            popup.dismiss()
            showModelsDialog()
        })

        content.addView(settingsRow("Model seç", modelName ?: "Model seçilmedi") {
            popup.dismiss()
            openModelPicker()
        })

        content.addView(settingsRow("Yeni sohbet", "Mevcut konuşmayı temizle") {
            popup.dismiss()
            newChat()
        })

        val lightTheme = isLightTheme()
        content.addView(settingsRow(
            if (lightTheme) "Koyu tema" else "Açık tema",
            if (lightTheme) "Koyu görünüme geç" else "Açık görünüme geç"
        ) {
            popup.dismiss()
            toggleTheme()
        })

        val chats = readChats().sortedByDescending { it.optLong("updatedAt", 0L) }
        if (chats.isNotEmpty()) {
            content.addView(View(this).apply {
                setBackgroundColor(themeColor(Color.rgb(63, 63, 66)))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(6)
                bottomMargin = dp(4)
            })

            content.addView(TextView(this).apply {
                text = "Son sohbetler"
                textSize = 13f
                setTextColor(themeColor(Color.rgb(176, 176, 182)))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), dp(8), dp(14), dp(4))
            })

            val historyContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            content.addView(historyContainer)

            fun renderHistory(showAll: Boolean) {
                historyContainer.removeAllViews()
                val visibleChats = if (showAll) chats else chats.take(5)
                visibleChats.forEach { chat ->
                    val id = chat.optLong("id", 0L)
                    val title = chat.optString("title", "Sohbet")
                    historyContainer.addView(historyRow(title) {
                        popup.dismiss()
                        openSavedChat(id)
                    })
                }

                if (!showAll && chats.size > 5) {
                    historyContainer.addView(TextView(this).apply {
                        text = "Tümünü gör"
                        textSize = 14f
                        setTextColor(Color.rgb(70, 125, 235))
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(dp(14), dp(10), dp(14), dp(12))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            renderHistory(true)
                            historyContainer.post {
                                popup.update(
                                    dp(300),
                                    (resources.displayMetrics.heightPixels * 0.78f).toInt()
                                )
                            }
                        }
                    })
                }
                applyThemeToExistingViews(historyContainer)
            }

            renderHistory(false)
        }

        applyThemeToExistingViews(content)

        val scrollContent = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        popup = PopupWindow(scrollContent, dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
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

'''
replace_between(
    '    private fun showSettingsPopup(anchor: View) {',
    '    private fun settingsRow(',
    settings_function,
    'settings popup with history',
)

replace_once(
    '        val promptForModel = when {\n',
    '        val basePromptForModel = when {\n',
    'base prompt rename',
)
replace_once(
    '\n        addUserMessage(typedPrompt, imagePath, genericFileName)\n',
    '''\n        val previousContext = restoredHistoryContext\n        val promptForModel = if (!previousContext.isNullOrBlank()) {\n            "$previousContext\\n\\nKullanıcı: $basePromptForModel"\n        } else {\n            basePromptForModel\n        }\n        restoredHistoryContext = null\n\n        ensureActiveChat(typedPrompt.ifBlank { genericFileName ?: if (imagePath != null) "Görsel analizi" else "Yeni sohbet" })\n        appendChatMessage("user", typedPrompt, imagePath, genericFileName)\n        addUserMessage(typedPrompt, imagePath, genericFileName)\n''',
    'save user chat message',
)
replace_once(
    '''                if (sb.isEmpty()) sb.append("Yanıt üretilemedi.")\n                renderMarkdown(assistantView, sb.toString())\n''',
    '''                if (sb.isEmpty()) sb.append("Yanıt üretilemedi.")\n                val finalAnswer = sb.toString()\n                renderMarkdown(assistantView, finalAnswer)\n                appendChatMessage("assistant", finalAnswer, null, null)\n                (assistantView.tag as? View)?.visibility = View.VISIBLE\n''',
    'save assistant chat message',
)
replace_once(
    '''        releaseEngine()\n        chatColumn.removeAllViews()\n        clearAttachment()\n        input.setText("")\n''',
    '''        releaseEngine()\n        activeChatId = 0L\n        restoredHistoryContext = null\n        chatColumn.removeAllViews()\n        clearAttachment()\n        input.setText("")\n''',
    'reset active chat on new chat',
)

assistant_function = r'''    private fun addAssistantMessage(initial: String, showCopyImmediately: Boolean = false): TextView {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tv = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(242, 244, 247))
            setLineSpacing(0f, 1.22f)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(Color.rgb(29, 43, 59), 22)
        }

        wrapper.addView(
            tv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val copyButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy)
            setColorFilter(themeIconColor())
            background = ColorDrawable(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "Yanıtı kopyala"
            visibility = if (showCopyImmediately) View.VISIBLE else View.GONE
            setOnClickListener {
                copyAnswerToClipboard(tv.text.toString())
            }
        }
        tv.tag = copyButton

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(2), 0, 0)
            addView(copyButton, LinearLayout.LayoutParams(dp(36), dp(36)))
        }
        wrapper.addView(
            actionRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        applyThemeToExistingViews(wrapper)

        chatColumn.addView(
            wrapper,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        )

        tv.text = initial
        scrollToBottom()
        return tv
    }

'''
replace_between(
    '    private fun addAssistantMessage(initial: String): TextView {',
    '    private fun renderMarkdown(',
    assistant_function,
    'assistant message with copy button',
)

history_helpers = r'''

    private fun historyRow(title: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(themeColor(Color.WHITE))
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun copyAnswerToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Yanıt", text))
        Toast.makeText(this, "Kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun readChats(): MutableList<JSONObject> {
        val raw = prefs.getString(KEY_CHAT_HISTORY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { index -> array.getJSONObject(index) }
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    private fun writeChats(chats: List<JSONObject>) {
        val array = JSONArray()
        chats.forEach { array.put(it) }
        prefs.edit().putString(KEY_CHAT_HISTORY, array.toString()).apply()
    }

    private fun ensureActiveChat(seed: String) {
        if (activeChatId != 0L) return
        activeChatId = System.currentTimeMillis()
        val chat = JSONObject().apply {
            put("id", activeChatId)
            put("title", makeChatTitle(seed))
            put("updatedAt", activeChatId)
            put("messages", JSONArray())
        }
        val chats = readChats()
        chats.add(chat)
        writeChats(chats)
    }

    private fun appendChatMessage(role: String, text: String, imagePath: String?, fileName: String?) {
        if (activeChatId == 0L) ensureActiveChat(text.ifBlank { fileName ?: "Yeni sohbet" })
        val chats = readChats()
        val index = chats.indexOfFirst { it.optLong("id", 0L) == activeChatId }
        if (index < 0) return

        val chat = chats[index]
        val messages = chat.optJSONArray("messages") ?: JSONArray().also { chat.put("messages", it) }
        val message = JSONObject().apply {
            put("role", role)
            put("text", text)
            if (!imagePath.isNullOrBlank()) put("imagePath", imagePath)
            if (!fileName.isNullOrBlank()) put("fileName", fileName)
        }
        messages.put(message)
        chat.put("updatedAt", System.currentTimeMillis())
        chats[index] = chat
        writeChats(chats)
    }

    private fun makeChatTitle(seed: String): String {
        val clean = seed.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return "Yeni sohbet"
        return if (clean.length <= 34) clean else clean.take(34).trimEnd() + "…"
    }

    private fun openSavedChat(chatId: Long) {
        if (chatId == 0L || isGenerating) return
        val chat = readChats().firstOrNull { it.optLong("id", 0L) == chatId } ?: return
        val messages = chat.optJSONArray("messages") ?: JSONArray()

        releaseEngine()
        activeChatId = chatId
        chatColumn.removeAllViews()
        clearAttachment()
        input.setText("")

        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val role = message.optString("role")
            val body = message.optString("text")
            val imagePath = message.optString("imagePath").takeIf { it.isNotBlank() && File(it).isFile }
            val fileName = message.optString("fileName").takeIf { it.isNotBlank() }
            if (role == "user") {
                addUserMessage(body, imagePath, fileName)
            } else if (role == "assistant") {
                val answerView = addAssistantMessage(body, showCopyImmediately = true)
                renderMarkdown(answerView, body)
            }
        }

        restoredHistoryContext = buildHistoryContext(messages)
        applyThemeToExistingViews(rootView)
        updateSendButtonState()
        scrollToBottom()
    }

    private fun buildHistoryContext(messages: JSONArray): String {
        val out = StringBuilder()
        val first = (messages.length() - 20).coerceAtLeast(0)
        for (i in first until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val role = message.optString("role")
            var body = message.optString("text").trim()
            if (body.isBlank()) {
                val fileName = message.optString("fileName")
                val imagePath = message.optString("imagePath")
                body = when {
                    fileName.isNotBlank() -> "[Dosya: $fileName]"
                    imagePath.isNotBlank() -> "[Görsel]"
                    else -> continue
                }
            }
            out.append(if (role == "assistant") "Asistan: " else "Kullanıcı: ")
                .append(body)
                .append("\\n")
        }
        return out.toString().takeLast(9000).trim()
    }
'''
marker = '\n    private fun isLightTheme(): Boolean ='
if text.count(marker) != 1:
    raise SystemExit(f"history helper marker: expected exactly 1 match, found {text.count(marker)}")
text = text.replace(marker, history_helpers + marker, 1)

path.write_text(text, encoding="utf-8")

required = [
    'KEY_CHAT_HISTORY',
    'text = "Son sohbetler"',
    'text = "Tümünü gör"',
    'openSavedChat(id)',
    'R.drawable.ic_copy',
    'copyAnswerToClipboard',
    'appendChatMessage("assistant"',
    'Color.rgb(242, 242, 245)',
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"missing expected 0.9.5 content: {needle}")

print("Offline AI 0.9.5 history/copy/light-theme patch applied")
