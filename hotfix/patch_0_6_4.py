from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

# Keep the thinking label visible for thought-only / empty streaming chunks.
old_collect = '''                getOrCreateEngine().streamReply(prompt).collect { chunk ->
                    if (firstChunk) {
                        firstChunk = false
                        thinkingJob.cancel()
                        answer.text = ""
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    }
                    answer.append(chunk)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }'''
new_collect = '''                getOrCreateEngine().streamReply(prompt).collect { chunk ->
                    // LiteRT can emit thought-only events whose visible text is empty.
                    // Do not hide the thinking label until real user-facing text arrives.
                    if (chunk.isBlank()) return@collect
                    if (firstChunk) {
                        firstChunk = false
                        thinkingJob.cancel()
                        answer.alpha = 1f
                        answer.text = ""
                        answer.setTextColor(textColor)
                        answer.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    }
                    answer.append(chunk)
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }'''
if old_collect not in s:
    raise SystemExit("stream collect block not found")
s = s.replace(old_collect, new_collect)

# ChatGPT-style simple thinking state: one muted label, no dots and no skip button.
pattern = re.compile(
    r'''    private fun startThinkingAnimation\(target: TextView\): Job = launch \{\n.*?\n    \}\n\n    private fun newChat\(\)''',
    re.S,
)
replacement = '''    private fun startThinkingAnimation(target: TextView): Job = launch {
        target.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        target.setTextColor(muted)
        target.text = "Düşünüyor"
        target.alpha = 0.82f
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        // Gentle pulse only; the text itself stays exactly "Düşünüyor".
        var dimmed = false
        while (true) {
            target.alpha = if (dimmed) 0.58f else 0.82f
            dimmed = !dimmed
            delay(520)
        }
    }

    private fun newChat()'''
s, n = pattern.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit("thinking animation block not found")

s = s.replace("Prototype 0.6.3", "Prototype 0.6.4")

p.write_text(s)
print("0.6.4 thinking UI patch applied")
