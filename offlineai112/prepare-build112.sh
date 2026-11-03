#!/usr/bin/env bash
set -euo pipefail

# Rebuild the exact, emulator-passed BUILD111 source first.
bash offlineai111/prepare-build111-release.sh
mv project111 project112

# Apply only the BUILD112 attachment-menu + model-vision warning patch.
patch -d project112 -p1 < offlineai112/build112-attachment-menu.patch

FILE="project112/app/src/main/java/com/offlineai/app/MainActivity.kt"
GRADLE="project112/app/build.gradle.kts"

grep -q 'versionCode = 112' "$GRADLE"
grep -q 'showThemedPopup(anchor, listOf("Fotoğraflar", "Dosyalar"), openAbove = true)' "$FILE"
grep -q 'val popupHeight = dp(items.size \* 50 + 12)' "$FILE"
grep -q 'fotoğraf analizini desteklemiyor. Fotoğraf destekleyen bir model seç.' "$FILE"

# Build clean BUILD112 from source.
(cd project112 && ./gradlew --no-daemon assembleDebug --stacktrace)
