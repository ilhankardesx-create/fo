#!/usr/bin/env bash
set -euo pipefail

cat offlineai108/chunk_00.b64 offlineai108/chunk_01.b64 offlineai108/chunk_02.b64 offlineai108/chunk_03.b64 offlineai108/chunk_04.b64 | base64 -d > base108.zip
echo "cd9564a71b2b6e8e119d2851788ecd27d7a173cd246b7749169bd1c9a0ac0e88  base108.zip" | sha256sum -c -
rm -rf project111
mkdir project111
unzip -q base108.zip -d project111
chmod +x project111/gradlew
FILE="project111/app/src/main/java/com/offlineai/app/MainActivity.kt"
sed -i 's/ScrollView\.LayoutParams(/FrameLayout.LayoutParams(/g' "$FILE"
sed -i 's/isTextSelectable = true/setTextIsSelectable(true)/g' "$FILE"
python - <<'PY'
from pathlib import Path
p=Path('project111/app/src/main/java/com/offlineai/app/MainActivity.kt')
s=p.read_text()
old='''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        restoreModel()\n'''
new='''        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        restoreModel()\n'''
if old not in s:
    raise SystemExit('BUILD108 startup anchor missing')
p.write_text(s.replace(old,new,1))
PY
patch -d project111 -p1 < offlineai109/build109-ui.patch
patch -d project111 -p1 < offlineai110/build110-fixes.patch
patch -d project111 -p1 < offlineai111/build111-ui.patch
patch -d project111 -p1 < offlineai111/build111-speed-final.patch

echo "e1d96fc74b45cbd2d0edabc1c26d768b160dcc002c76d1828f91d13504f770e1  project111/app/src/main/java/com/offlineai/app/MainActivity.kt" | sha256sum -c -
echo "77ad9f829175b75babc79b47e5e469b68930a8491d1637c878c91e7aa7a283e2  project111/app/build.gradle.kts" | sha256sum -c -
grep -q 'versionCode = 111' project111/app/build.gradle.kts
grep -q 'private fun showThemedPopup' "$FILE"
! grep -q 'PopupMenu' "$FILE"
! grep -q 'Typeface.create("serif"' "$FILE"
grep -q 'Typeface.create("sans-serif", Typeface.NORMAL)' "$FILE"
grep -q 'Typeface.create("sans-serif", Typeface.BOLD)' "$FILE"
grep -q 'pending.length > 180 -> 30' "$FILE"
grep -q 'pending.length > 90 -> 18' "$FILE"
grep -q 'pending.length > 45 -> 9' "$FILE"
grep -q 'pending.length > 18 -> 6' "$FILE"
grep -q 'else -> 3' "$FILE"
grep -q 'target.append(chunk); onUpdate(); delay(10L)' "$FILE"

(cd project111 && ./gradlew --no-daemon assembleDebug --stacktrace)
