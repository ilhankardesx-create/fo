from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

# Remove the small "Offline AI" title from the conversation/top bar. Keep only
# the hamburger menu so the chat area looks cleaner.
title_pattern = re.compile(
    r'''        val title = TextView\(this\)\.apply \{.*?        top\.addView\(title, LinearLayout\.LayoutParams\(0, -1, 1f\)\)\n\n''',
    re.S,
)
s, n = title_pattern.subn('', s, count=1)
if n != 1:
    raise SystemExit("top title block not found")

# Replace the single compressed model selector with a proper model download
# section: heading + two clear cards + visible download links.
status_pattern = re.compile(
    r'''        status = TextView\(this\)\.apply \{.*?        status\.setOnClickListener \{ handleModelStatusTap\(\) \}\n''',
    re.S,
)

model_ui = r'''        welcome.addView(TextView(this).apply {
            text = "Model indir"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26) })

        val fastCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokeRounded(panel, Color.rgb(65, 65, 65), 18f)
            setPadding(dp(18), dp(14), dp(18), dp(13))
        }
        fastCard.addView(TextView(this).apply {
            text = "Hızlı ve Hafif"
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })
        fastCard.addView(TextView(this).apply {
            text = "Qwen3 1.7B · ~0,9 GB"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        fastCard.addView(TextView(this).apply {
            text = "Düşük ve orta seviye cihazlar için"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        fastCard.addView(TextView(this).apply {
            text = "İndir"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            paint.isUnderlineText = true
            setPadding(0, dp(9), 0, 0)
            setOnClickListener { confirmModelDownload(fastModel) }
        })
        welcome.addView(fastCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        val powerfulCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokeRounded(panel, Color.rgb(65, 65, 65), 18f)
            setPadding(dp(18), dp(14), dp(18), dp(13))
        }
        powerfulCard.addView(TextView(this).apply {
            text = "Güçlü"
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        })
        powerfulCard.addView(TextView(this).apply {
            text = "Qwen3 4B · ~2,5 GB"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        powerfulCard.addView(TextView(this).apply {
            text = "Güçlü cihazlar için"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
        powerfulCard.addView(TextView(this).apply {
            text = "İndir"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            paint.isUnderlineText = true
            setPadding(0, dp(9), 0, 0)
            setOnClickListener { confirmModelDownload(powerfulModel) }
        })
        welcome.addView(powerfulCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        status = TextView(this).apply {
            text = "Henüz model indirilmedi"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(muted)
        }
        welcome.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
'''

s, n = status_pattern.subn(model_ui, s, count=1)
if n != 1:
    raise SystemExit("old model selector block not found")

# Keep the fallback chooser consistent with the new wording.
s = s.replace('.setTitle("AI modelini seç")', '.setTitle("Model indir")')
s = s.replace(
    '"⚡ Hızlı · Qwen3 1.7B · ~0,9 GB\\nDaha az RAM, daha hızlı cevap",',
    '"Hızlı ve Hafif · Qwen3 1.7B · ~0,9 GB\\nDüşük ve orta seviye cihazlar için",'
)
s = s.replace(
    '"🧠 Güçlü · Qwen3 4B · ~2,5 GB\\nDaha iyi cevaplar, daha fazla RAM"',
    '"Güçlü · Qwen3 4B · ~2,5 GB\\nGüçlü cihazlar için"'
)

# Default/removed state now belongs to the dedicated status line below the cards.
s = s.replace('status.text = "Model seç · Hızlı 1.7B / Güçlü 4B"', 'status.text = "Henüz model indirilmedi"')

s = s.replace("Prototype 0.7.1", "Prototype 0.7.2")

p.write_text(s)
print("0.7.2 model download UI + clean top bar patch applied")
