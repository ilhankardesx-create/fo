#!/usr/bin/env bash
set -euo pipefail
rm -rf project110-master INTERNETSIZ-YAPAY-ZEKA_BUILD110_KOTLIN_MASTER_SOURCE.zip base108-master.zip
cat offlineai108/chunk_00.b64 offlineai108/chunk_01.b64 offlineai108/chunk_02.b64 offlineai108/chunk_03.b64 offlineai108/chunk_04.b64 | base64 -d > base108-master.zip
echo "cd9564a71b2b6e8e119d2851788ecd27d7a173cd246b7749169bd1c9a0ac0e88  base108-master.zip" | sha256sum -c -
mkdir project110-master
unzip -q base108-master.zip -d project110-master
chmod +x project110-master/gradlew
FILE="project110-master/app/src/main/java/com/offlineai/app/MainActivity.kt"
sed -i 's/ScrollView\.LayoutParams(/FrameLayout.LayoutParams(/g' "$FILE"
sed -i 's/isTextSelectable = true/setTextIsSelectable(true)/g' "$FILE"
python - <<'PY'
from pathlib import Path
p=Path('project110-master/app/src/main/java/com/offlineai/app/MainActivity.kt')
s=p.read_text()
old='''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        restoreModel()\n'''
new='''        PDFBoxResourceLoader.init(applicationContext)\n        engine = LocalModelEngine(this)\n        registerDownloadReceiver()\n        buildUi()\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {\n            val appearance = if (isDarkTheme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS\n            window.insetsController?.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)\n        }\n        restoreModel()\n'''
if old not in s: raise SystemExit('BUILD108 startup anchor missing')
p.write_text(s.replace(old,new,1))
PY
patch -d project110-master -p1 < offlineai109/build109-ui.patch
patch -d project110-master -p1 < offlineai110/build110-fixes.patch
echo "48b8e03a66b609ed3119e7a9d73fd665d2ed4245dea5612fe8cbdfcb73a40dfc  project110-master/app/src/main/java/com/offlineai/app/MainActivity.kt" | sha256sum -c -
echo "8a24afa5d29f6cc097ca415c16206b02f14eb07f2e9af4dbccf2a8168d001e74  project110-master/app/build.gradle.kts" | sha256sum -c -
mkdir -p project110-master/docs
cat > project110-master/docs/BUILD110_MASTER_STATUS.txt <<'EOF'
INTERNETSIZ YAPAY ZEKA - BUILD 110 MASTER
versionCode: 110
versionName: 1.2
applicationId: com.internetsizyapayzeka.app2026

BUILD110 changes:
- ChatGPT-like full-screen scrolling conversation layout retained from BUILD109.
- Streaming auto-scroll stabilized: no fullScroll on every token; throttled/coalesced near-bottom scrolling.
- Theme toggle preserves current chat and input draft without Activity recreate.
- About dialog expanded with developer İlhan Kardeş, ilhankardesx@gmail.com and usage guide.
- Stop/model/file/photo/history behavior retained.

Final emulator gate passed before this master was packaged:
- clean APK launch
- theme/chat/draft preservation
- About content
- 20-second long streaming scroll: no backward drops
- no AndroidRuntime fatal crash

Canonical hashes:
MainActivity.kt SHA256 = 48b8e03a66b609ed3119e7a9d73fd665d2ed4245dea5612fe8cbdfcb73a40dfc
app/build.gradle.kts SHA256 = 8a24afa5d29f6cc097ca415c16206b02f14eb07f2e9af4dbccf2a8168d001e74
EOF
( cd project110-master && zip -qr ../INTERNETSIZ-YAPAY-ZEKA_BUILD110_KOTLIN_MASTER_SOURCE.zip . )
unzip -t INTERNETSIZ-YAPAY-ZEKA_BUILD110_KOTLIN_MASTER_SOURCE.zip >/dev/null
sha256sum INTERNETSIZ-YAPAY-ZEKA_BUILD110_KOTLIN_MASTER_SOURCE.zip | tee build110-master-sha256.txt
