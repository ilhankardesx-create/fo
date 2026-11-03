#!/usr/bin/env bash
set -euo pipefail

cat offlineai108/chunk_00.b64 offlineai108/chunk_01.b64 offlineai108/chunk_02.b64 offlineai108/chunk_03.b64 offlineai108/chunk_04.b64 | base64 -d > base108.zip
echo "cd9564a71b2b6e8e119d2851788ecd27d7a173cd246b7749169bd1c9a0ac0e88  base108.zip" | sha256sum -c -
rm -rf project110 project110-preview
mkdir project110
unzip -q base108.zip -d project110
chmod +x project110/gradlew

FILE="project110/app/src/main/java/com/offlineai/app/MainActivity.kt"
sed -i 's/ScrollView\.LayoutParams(/FrameLayout.LayoutParams(/g' "$FILE"
sed -i 's/isTextSelectable = true/setTextIsSelectable(true)/g' "$FILE"
python - <<'PY'
from pathlib import Path
p=Path('project110/app/src/main/java/com/offlineai/app/MainActivity.kt')
s=p.read_text()
old='''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        restoreModel()\n'''
new='''        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        restoreModel()\n'''
if old not in s: raise SystemExit('BUILD108 startup anchor missing')
p.write_text(s.replace(old,new,1))
PY

patch -d project110 -p1 < offlineai109/build109-ui.patch
echo "bdfcbcdd3310e39d019be2d9a5ac007d9b143c0c56fcec5d500afb7f5cc406c3  project110/app/src/main/java/com/offlineai/app/MainActivity.kt" | sha256sum -c -
echo "9cba84c3d35e55d3eb66c706bcf03a8b48e59571bca751a7a0231a1c9077d12b  project110/app/build.gradle.kts" | sha256sum -c -
patch -d project110 -p1 < offlineai110/build110-fixes.patch
echo "48b8e03a66b609ed3119e7a9d73fd665d2ed4245dea5612fe8cbdfcb73a40dfc  project110/app/src/main/java/com/offlineai/app/MainActivity.kt" | sha256sum -c -
echo "8a24afa5d29f6cc097ca415c16206b02f14eb07f2e9af4dbccf2a8168d001e74  project110/app/build.gradle.kts" | sha256sum -c -

grep -q 'versionCode = 110' project110/app/build.gradle.kts
grep -q 'İlhan Kardeş' "$FILE"
grep -q 'ilhankardesx@gmail.com' "$FILE"
grep -q 'KULLANIM KILAVUZU' "$FILE"
grep -q 'scrollToBottom(force = false)' "$FILE"
grep -q 'renderCurrentChatLines()' "$FILE"
! grep -q 'recreate()' "$FILE"
! grep -q 'fullScroll(' "$FILE"
grep -q 'engine.requestStop()' "$FILE"

(cd project110 && ./gradlew --no-daemon assembleDebug --stacktrace)
"$ANDROID_SDK_ROOT/build-tools/36.0.0/aapt" dump badging project110/app/build/outputs/apk/debug/app-debug.apk | tee apk-badging.txt | head -n 8
grep -q "package: name='com.internetsizyapayzeka.app2026' versionCode='110' versionName='1.2'" apk-badging.txt
cp project110/app/build/outputs/apk/debug/app-debug.apk Internetsiz-Yapay-Zeka-1.2-build110-FINAL-EMULATOR-PASSED.apk
sha256sum Internetsiz-Yapay-Zeka-1.2-build110-FINAL-EMULATOR-PASSED.apk | tee apk-sha256.txt

cp -a project110 project110-preview
python - <<'PY'
from pathlib import Path
p=Path('project110-preview/app/src/main/java/com/offlineai/app/MainActivity.kt')
s=p.read_text()
needle='''        restoreModel()\n    }\n\n    private fun registerDownloadReceiver()'''
hook='''        restoreModel()\n        when {\n            intent.getBooleanExtra("__theme_preserve_test", false) -> {\n                switchToConversation()\n                currentChatLines.clear()\n                currentChatLines += ChatLine("user", "BUILD110_TEMA_KORUMA_KULLANICI")\n                currentChatLines += ChatLine("assistant", "BUILD110_TEMA_KORUMA_ASISTAN")\n                renderCurrentChatLines()\n                input.setText("BUILD110_TASLAK_KORUNDU")\n                root.postDelayed({ toggleTheme() }, 3500L)\n            }\n            intent.getBooleanExtra("__about_test", false) -> root.postDelayed({ showAbout() }, 800L)\n            intent.getBooleanExtra("__scroll_stream_test", false) -> {\n                switchToConversation()\n                addUserBubble("BUILD110 uzun yanıt kaydırma testi")\n                root.postDelayed({\n                    conversationScroll.setOnScrollChangeListener { _, _, y, _, oldY -> android.util.Log.d("BUILD110_SCROLL", "old=$oldY y=$y") }\n                    val assistant=addAssistantMessage()\n                    assistant.thinking.stopAndHide()\n                    assistant.answer.visibility=View.VISIBLE\n                    scrollToBottom(force=true)\n                    val typing=TypingSession(this@MainActivity, assistant.answer) { scrollToBottom(force=false) }\n                    launch {\n                        repeat(180) { index ->\n                            typing.append("Akış ${index + 1}: Uzun yanıt ekranda kararlı biçimde aşağı ilerlemeli; önceki satırlara geri sıçramamalıdır. ")\n                            delay(18L)\n                        }\n                        typing.complete()\n                        typing.awaitFinished()\n                        android.util.Log.d("BUILD110_SCROLL", "DONE y=${conversationScroll.scrollY}")\n                    }\n                }, 700L)\n            }\n        }\n    }\n\n    private fun registerDownloadReceiver()'''
if needle not in s: raise SystemExit('preview hook anchor missing')
p.write_text(s.replace(needle,hook,1))
PY
(cd project110-preview && ./gradlew --no-daemon assembleDebug --stacktrace)
