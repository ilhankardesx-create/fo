from pathlib import Path

path = Path("offline-ai/app/src/main/java/com/offlineai/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    '        private const val KEY_MODEL_NAME = "model_name"\n',
    '        private const val KEY_MODEL_NAME = "model_name"\n        private const val KEY_LIGHT_THEME = "light_theme"\n',
    "theme pref key",
)

replace_once(
    '        header.addView(settingsButton, LinearLayout.LayoutParams(dp(54), dp(54)))\n',
    '        header.addView(settingsButton, LinearLayout.LayoutParams(dp(52), dp(52)))\n',
    "slightly smaller settings button",
)

replace_once(
    '        buildUi()\n        applyWindowInsets()\n        modelPath = resolveExistingModel()\n',
    '        buildUi()\n        applyThemeToExistingViews(rootView)\n        applySystemBarTheme()\n        applyWindowInsets()\n        modelPath = resolveExistingModel()\n',
    "initial theme apply",
)

replace_once(
    '''        content.addView(settingsRow("Yeni sohbet", "Mevcut konuşmayı temizle") {\n            popup.dismiss()\n            newChat()\n        })\n\n        popup = PopupWindow(content, dp(272), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {\n''',
    '''        content.addView(settingsRow("Yeni sohbet", "Mevcut konuşmayı temizle") {\n            popup.dismiss()\n            newChat()\n        })\n\n        val lightTheme = isLightTheme()\n        content.addView(settingsRow(\n            if (lightTheme) "Koyu tema" else "Açık tema",\n            if (lightTheme) "Koyu görünüme geç" else "Açık görünüme geç"\n        ) {\n            popup.dismiss()\n            toggleTheme()\n        })\n\n        applyThemeToExistingViews(content)\n\n        popup = PopupWindow(content, dp(272), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {\n''',
    "theme settings row",
)

replace_once(
    '        popup = PopupWindow(content, dp(228), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {\n',
    '        applyThemeToExistingViews(content)\n\n        popup = PopupWindow(content, dp(228), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {\n',
    "attachment popup theme",
)

replace_once(
    '        dialog.setContentView(outer)\n        dialog.show()\n',
    '        applyThemeToExistingViews(outer)\n        dialog.setContentView(outer)\n        dialog.show()\n',
    "models dialog theme",
)

replace_once(
    '''        chatColumn.addView(\n            row,\n''',
    '''        applyThemeToExistingViews(row)\n\n        chatColumn.addView(\n            row,\n''',
    "user message theme",
)

replace_once(
    '''        chatColumn.addView(\n            tv,\n''',
    '''        applyThemeToExistingViews(tv)\n\n        chatColumn.addView(\n            tv,\n''',
    "assistant message theme",
)

replace_once(
    '''        val color = if (hasContent || isGenerating) {\n            Color.rgb(43, 108, 238)\n        } else {\n            Color.rgb(63, 63, 66)\n        }\n        sendButton.background = rounded(color, 100)\n''',
    '''        val active = hasContent || isGenerating\n        val color = if (active) {\n            Color.rgb(43, 108, 238)\n        } else {\n            themeColor(Color.rgb(63, 63, 66))\n        }\n        sendButton.background = rounded(color, 100)\n        sendButton.setColorFilter(if (active) Color.WHITE else themeIconColor())\n''',
    "theme-aware send button",
)

# Icons created after a theme switch should automatically use the current palette.
text = text.replace('setColorFilter(Color.WHITE)', 'setColorFilter(themeIconColor())')
# The send arrow on the blue active button must stay white.
text = text.replace(
    'sendButton.setColorFilter(if (active) themeIconColor() else themeIconColor())',
    'sendButton.setColorFilter(if (active) Color.WHITE else themeIconColor())'
)

helpers = r'''

    private fun isLightTheme(): Boolean =
        prefs.getBoolean(KEY_LIGHT_THEME, false)

    private fun toggleTheme() {
        val newLight = !isLightTheme()
        prefs.edit().putBoolean(KEY_LIGHT_THEME, newLight).apply()
        applyThemeToExistingViews(rootView)
        settingsButton.setColorFilter(themeIconColor())
        if (pendingImagePath == null && attachmentPreview.visibility == View.VISIBLE) {
            attachmentThumb.setColorFilter(themeIconColor())
        }
        applySystemBarTheme()
        updateSendButtonState()
    }

    private fun themeIconColor(): Int =
        if (isLightTheme()) Color.rgb(35, 35, 38) else Color.WHITE

    private fun themeColor(color: Int): Int {
        val light = isLightTheme()
        return when (color) {
            Color.rgb(15, 15, 16), Color.rgb(248, 248, 250) ->
                if (light) Color.rgb(248, 248, 250) else Color.rgb(15, 15, 16)
            Color.rgb(33, 33, 34), Color.rgb(242, 242, 244) ->
                if (light) Color.rgb(242, 242, 244) else Color.rgb(33, 33, 34)
            Color.rgb(46, 46, 48), Color.rgb(229, 229, 232) ->
                if (light) Color.rgb(229, 229, 232) else Color.rgb(46, 46, 48)
            Color.rgb(48, 48, 50), Color.rgb(250, 250, 252) ->
                if (light) Color.rgb(250, 250, 252) else Color.rgb(48, 48, 50)
            Color.rgb(48, 48, 49), Color.rgb(232, 232, 235) ->
                if (light) Color.rgb(232, 232, 235) else Color.rgb(48, 48, 49)
            Color.rgb(55, 55, 58), Color.rgb(224, 224, 228) ->
                if (light) Color.rgb(224, 224, 228) else Color.rgb(55, 55, 58)
            Color.rgb(63, 63, 66), Color.rgb(218, 218, 222) ->
                if (light) Color.rgb(218, 218, 222) else Color.rgb(63, 63, 66)
            Color.rgb(66, 66, 69), Color.rgb(222, 222, 226) ->
                if (light) Color.rgb(222, 222, 226) else Color.rgb(66, 66, 69)
            Color.rgb(61, 61, 64), Color.rgb(234, 234, 237) ->
                if (light) Color.rgb(234, 234, 237) else Color.rgb(61, 61, 64)
            Color.rgb(58, 58, 60), Color.rgb(235, 235, 238) ->
                if (light) Color.rgb(235, 235, 238) else Color.rgb(58, 58, 60)
            Color.rgb(29, 43, 59), Color.rgb(228, 237, 248) ->
                if (light) Color.rgb(228, 237, 248) else Color.rgb(29, 43, 59)
            Color.rgb(27, 29, 33), Color.rgb(246, 247, 249) ->
                if (light) Color.rgb(246, 247, 249) else Color.rgb(27, 29, 33)
            Color.rgb(38, 41, 47), Color.rgb(238, 240, 244) ->
                if (light) Color.rgb(238, 240, 244) else Color.rgb(38, 41, 47)
            Color.rgb(53, 55, 60), Color.rgb(226, 228, 232) ->
                if (light) Color.rgb(226, 228, 232) else Color.rgb(53, 55, 60)

            Color.WHITE, Color.rgb(28, 28, 30) ->
                if (light) Color.rgb(28, 28, 30) else Color.WHITE
            Color.rgb(242, 244, 247), Color.rgb(30, 30, 33) ->
                if (light) Color.rgb(30, 30, 33) else Color.rgb(242, 244, 247)
            Color.rgb(170, 170, 174), Color.rgb(102, 102, 108) ->
                if (light) Color.rgb(102, 102, 108) else Color.rgb(170, 170, 174)
            Color.rgb(176, 176, 182), Color.rgb(98, 98, 104) ->
                if (light) Color.rgb(98, 98, 104) else Color.rgb(176, 176, 182)
            Color.rgb(185, 188, 195), Color.rgb(92, 94, 101) ->
                if (light) Color.rgb(92, 94, 101) else Color.rgb(185, 188, 195)
            Color.rgb(192, 195, 202), Color.rgb(86, 88, 96) ->
                if (light) Color.rgb(86, 88, 96) else Color.rgb(192, 195, 202)
            Color.rgb(155, 160, 169), Color.rgb(108, 111, 118) ->
                if (light) Color.rgb(108, 111, 118) else Color.rgb(155, 160, 169)
            else -> color
        }
    }

    private fun backgroundColorOf(view: View): Int? {
        return when (val bg = view.background) {
            is ColorDrawable -> bg.color
            is GradientDrawable -> bg.color?.defaultColor
            else -> null
        }
    }

    private fun applyThemeToExistingViews(view: View) {
        when (val bg = view.background) {
            is ColorDrawable -> {
                val mapped = themeColor(bg.color)
                if (mapped != bg.color) view.setBackgroundColor(mapped)
            }
            is GradientDrawable -> {
                val current = bg.color?.defaultColor
                if (current != null) {
                    val mapped = themeColor(current)
                    if (mapped != current) bg.setColor(mapped)
                }
            }
        }

        if (view is TextView) {
            val currentText = view.currentTextColor
            val accentBackground = backgroundColorOf(view) == Color.rgb(43, 108, 238)
            if (!(accentBackground && currentText == Color.WHITE)) {
                view.setTextColor(themeColor(currentText))
            }
            if (view is EditText) {
                view.setHintTextColor(themeColor(view.currentHintTextColor))
            }
        }

        if (view is ImageView && view.scaleType != ImageView.ScaleType.CENTER_CROP) {
            view.setColorFilter(themeIconColor())
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeToExistingViews(view.getChildAt(i))
            }
        }
    }

    private fun applySystemBarTheme() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = themeColor(Color.rgb(15, 15, 16))
        val light = isLightTheme()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (light) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
    }
'''

marker = '\n    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {'
if text.count(marker) != 1:
    raise SystemExit(f"theme helper marker: expected exactly 1 match, found {text.count(marker)}")
text = text.replace(marker, helpers + marker, 1)

path.write_text(text, encoding="utf-8")

required = [
    'KEY_LIGHT_THEME',
    'if (lightTheme) "Koyu tema" else "Açık tema"',
    'toggleTheme()',
    'applyThemeToExistingViews(rootView)',
    'LinearLayout.LayoutParams(dp(52), dp(52))',
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"missing expected 0.9.4 content: {needle}")

print("Offline AI 0.9.4 theme patch applied")
