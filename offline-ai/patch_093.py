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
    '''        // Model linklerini buraya koyacağız.\n        private const val FAST_MODEL_URL = ""\n        private const val SMART_MODEL_URL = ""\n\n        private const val FAST_MODEL_FILE = "gemma-4-e2b-it.litertlm"\n        private const val SMART_MODEL_FILE = "gemma-4-e4b-it.litertlm"\n''',
    '''        private const val FAST_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"\n        private const val SMART_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"\n        private const val POWER_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-12B-it-litert-lm/resolve/main/gemma-4-12B-it.litertlm?download=true"\n\n        private const val FAST_MODEL_FILE = "gemma-4-E2B-it.litertlm"\n        private const val SMART_MODEL_FILE = "gemma-4-E4B-it.litertlm"\n        private const val POWER_MODEL_FILE = "gemma-4-12B-it.litertlm"\n''',
    "model constants",
)

replace_once(
    '    private var fileIsLoading = false\n',
    '    private var fileIsLoading = false\n    private lateinit var samsungPerformanceBoost: SamsungPerformanceBoost\n',
    "Samsung boost field",
)

replace_once(
    '        PDFBoxResourceLoader.init(applicationContext)\n\n        window.statusBarColor = Color.TRANSPARENT\n',
    '        PDFBoxResourceLoader.init(applicationContext)\n        samsungPerformanceBoost = SamsungPerformanceBoost(this)\n\n        window.statusBarColor = Color.TRANSPARENT\n',
    "Samsung boost init",
)

replace_once(
    '        generationJob?.cancel()\n        releaseEngine()\n        scope.cancel()\n',
    '        generationJob?.cancel()\n        samsungPerformanceBoost.close()\n        releaseEngine()\n        scope.cancel()\n',
    "Samsung boost destroy",
)

replace_once(
    '        content.addView(settingsRow("Model indir", "Hızlı ve Akıllı modeller") {\n',
    '        content.addView(settingsRow("Model indir", "Hızlı, Akıllı ve 12B modeller") {\n',
    "settings subtitle",
)

replace_once(
    'description = "Daha az RAM kullanır ve daha hızlı yanıt verir. Günlük sohbet, kısa özetler ve basit işler için.",',
    'description = "Daha az RAM kullanır ve daha hızlı yanıt verir. Günlük sohbet, kısa özetler ve basit işler için. Yaklaşık 2,6 GB.",',
    "E2B description",
)

replace_once(
    'description = "Daha güçlü muhakeme ve görsel anlama. Daha ayrıntılı sorular ve belge analizi için önerilen model.",',
    'description = "Daha güçlü muhakeme ve görsel anlama. Daha ayrıntılı sorular ve belge analizi için önerilen model. Yaklaşık 3,7 GB.",',
    "E4B description",
)

third_card = '''\n        panel.addView(\n            modelCard(\n                badge = "GÜÇLÜ",\n                name = "Gemma 4 12B",\n                description = "Daha büyük ve güçlü model. Yaklaşık 6,9 GB. 16 GB RAM ve üzeri cihazlar için önerilir; bazı telefonlarda bellek nedeniyle çalışmayabilir.",\n                recommended = false,\n                url = POWER_MODEL_URL,\n                fileName = POWER_MODEL_FILE\n            ),\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.WRAP_CONTENT\n            ).apply { topMargin = dp(12) }\n        )\n'''
marker = '\n        val outer = FrameLayout(this).apply {'
if text.count(marker) != 1:
    raise SystemExit(f"12B insert marker: expected exactly 1 match, found {text.count(marker)}")
text = text.replace(marker, third_card + marker, 1)

replace_once(
    '''        val outer = FrameLayout(this).apply {\n            setPadding(dp(18), dp(18), dp(18), dp(18))\n            addView(\n                panel,\n                FrameLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    ViewGroup.LayoutParams.WRAP_CONTENT,\n                    Gravity.CENTER\n                )\n            )\n        }\n\n        dialog.setContentView(outer)\n        dialog.window?.setLayout(\n            (resources.displayMetrics.widthPixels * 0.94f).toInt(),\n            ViewGroup.LayoutParams.WRAP_CONTENT\n        )\n        dialog.show()\n        dialog.window?.setLayout(\n            (resources.displayMetrics.widthPixels * 0.94f).toInt(),\n            ViewGroup.LayoutParams.WRAP_CONTENT\n        )\n''',
    '''        val panelScroll = ScrollView(this).apply {\n            isFillViewport = false\n            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS\n            addView(\n                panel,\n                ViewGroup.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    ViewGroup.LayoutParams.WRAP_CONTENT\n                )\n            )\n        }\n\n        val outer = FrameLayout(this).apply {\n            setPadding(dp(18), dp(18), dp(18), dp(18))\n            addView(\n                panelScroll,\n                FrameLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    (resources.displayMetrics.heightPixels * 0.82f).toInt(),\n                    Gravity.CENTER\n                )\n            )\n        }\n\n        dialog.setContentView(outer)\n        dialog.show()\n        dialog.window?.setLayout(\n            (resources.displayMetrics.widthPixels * 0.94f).toInt(),\n            ViewGroup.LayoutParams.WRAP_CONTENT\n        )\n''',
    "scrollable model dialog",
)

replace_once(
    '        val assistantView = addAssistantMessage("Düşünüyor…")\n        setGenerating(true)\n\n        generationJob = scope.launch {\n',
    '        val assistantView = addAssistantMessage("Düşünüyor…")\n        setGenerating(true)\n        samsungPerformanceBoost.start()\n\n        generationJob = scope.launch {\n',
    "start Samsung boost",
)

replace_once(
    '            } finally {\n                setGenerating(false)\n                scrollToBottom()\n',
    '            } finally {\n                samsungPerformanceBoost.stop()\n                setGenerating(false)\n                scrollToBottom()\n',
    "stop Samsung boost",
)

path.write_text(text, encoding="utf-8")

required = [
    "POWER_MODEL_URL",
    "Gemma 4 12B",
    "16 GB RAM",
    "samsungPerformanceBoost.start()",
    "samsungPerformanceBoost.stop()",
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"missing expected patched content: {needle}")

print("Offline AI 0.9.3 patch applied")
