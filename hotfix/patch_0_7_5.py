from pathlib import Path

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

old = '''                getOrCreateEngine().streamReply(prompt).collect { chunk ->
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

new = '''                val rawAnswer = StringBuilder()
                getOrCreateEngine().streamReply(prompt).collect { chunk ->
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
                    rawAnswer.append(chunk)
                    answer.text = formatAssistantText(rawAnswer.toString())
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }'''

if old not in s:
    raise SystemExit("stream rendering block not found")
s = s.replace(old, new, 1)

marker = '''    private fun startThinkingAnimation(target: TextView): Job = launch {
'''
formatter = r'''    private fun formatAssistantText(raw: String): String {
        var value = raw.replace("\r\n", "\n")

        // Clean native rendering: never expose raw markdown markers to users.
        value = value
            .replace("**", "")
            .replace("__", "")
            .replace("```", "")

        // Repair list markers deterministically if a small local model emits them inline.
        value = value.replace(
            Regex("(?<![\\d\\n])([1-9]\\d?\\.)\\s*(?=[\\p{L}\\p{N}])"),
            "\n$1 "
        )
        value = value.replace(
            Regex("(?<!\\n)([-•])\\s+(?=[\\p{L}\\p{N}])"),
            "\n$1 "
        )

        value = value.replace(Regex("[ \\t]+"), " ")
        value = value.replace(Regex(" *\\n *"), "\n")
        value = value.replace(Regex("\\n{3,}"), "\n\n")
        return value.trimStart()
    }

'''
if marker not in s:
    raise SystemExit("thinking animation marker not found")
s = s.replace(marker, formatter + marker, 1)

s = s.replace("Prototype 0.7.2", "Prototype 0.7.5")
p.write_text(s)
print("0.7.5 clean rendering patch applied")
