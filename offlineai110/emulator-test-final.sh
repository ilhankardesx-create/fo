#!/usr/bin/env bash
set -euo pipefail
CLEAN_APK="Internetsiz-Yapay-Zeka-1.2-build110-EMULATOR-PASSED.apk"
PREVIEW_APK="project110-preview/app/build/outputs/apk/debug/app-debug.apk"
APP_PACKAGE="com.internetsizyapayzeka.app2026"
ACTIVITY="com.offlineai.app.MainActivity"
: > emulator-status.txt

test -f "$CLEAN_APK"
test -f "$PREVIEW_APK"

echo '=== CLEAN APK NORMAL LAUNCH ===' | tee -a emulator-status.txt
adb install -r "$CLEAN_APK"
adb logcat -c
adb shell am force-stop "$APP_PACKAGE" || true
adb shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1
sleep 8
CLEAN_PID="$(adb shell pidof "$APP_PACKAGE" | tr -d '\r' || true)"
echo "CLEAN_APP_PID=$CLEAN_PID" | tee -a emulator-status.txt
test -n "$CLEAN_PID"
adb exec-out screencap -p > emulator-normal-launch.png
adb logcat -d AndroidRuntime:E '*:S' > clean-androidruntime-errors.txt || true
! grep -q 'FATAL EXCEPTION' clean-androidruntime-errors.txt
! grep -q "Process: $APP_PACKAGE" clean-androidruntime-errors.txt

echo '=== THEME PRESERVES CURRENT CHAT ===' | tee -a emulator-status.txt
adb install -r "$PREVIEW_APK"
adb shell am force-stop "$APP_PACKAGE" || true
adb shell am start -n "$APP_PACKAGE/$ACTIVITY" --ez __theme_preserve_test true
sleep 2
THEME_PID_BEFORE="$(adb shell pidof "$APP_PACKAGE" | tr -d '\r' || true)"
adb shell uiautomator dump /sdcard/theme-before.xml >/dev/null
adb pull /sdcard/theme-before.xml theme-before.xml >/dev/null
grep -q 'BUILD110_TEMA_KORUMA_KULLANICI' theme-before.xml
grep -q 'BUILD110_TEMA_KORUMA_ASISTAN' theme-before.xml
grep -q 'BUILD110_TASLAK_KORUNDU' theme-before.xml
adb exec-out screencap -p > emulator-theme-before.png
sleep 5
THEME_PID_AFTER="$(adb shell pidof "$APP_PACKAGE" | tr -d '\r' || true)"
test -n "$THEME_PID_AFTER"
test "$THEME_PID_BEFORE" = "$THEME_PID_AFTER"
adb shell uiautomator dump /sdcard/theme-after.xml >/dev/null
adb pull /sdcard/theme-after.xml theme-after.xml >/dev/null
grep -q 'BUILD110_TEMA_KORUMA_KULLANICI' theme-after.xml
grep -q 'BUILD110_TEMA_KORUMA_ASISTAN' theme-after.xml
grep -q 'BUILD110_TASLAK_KORUNDU' theme-after.xml
adb shell run-as "$APP_PACKAGE" cat shared_prefs/offline_ai_prefs.xml > theme-prefs.xml
grep -q 'name="dark_theme" value="false"' theme-prefs.xml
adb exec-out screencap -p > emulator-theme-after.png
echo "THEME_PID_BEFORE=$THEME_PID_BEFORE THEME_PID_AFTER=$THEME_PID_AFTER" | tee -a emulator-status.txt

echo '=== ABOUT CONTENT ===' | tee -a emulator-status.txt
adb shell am force-stop "$APP_PACKAGE" || true
adb shell am start -n "$APP_PACKAGE/$ACTIVITY" --ez __about_test true
sleep 3
adb shell uiautomator dump /sdcard/about.xml >/dev/null
adb pull /sdcard/about.xml about.xml >/dev/null
grep -q 'İlhan Kardeş' about.xml
grep -q 'ilhankardesx@gmail.com' about.xml
grep -q 'KULLANIM KILAVUZU' about.xml
adb exec-out screencap -p > emulator-about.png

echo '=== LONG STREAM AUTO-SCROLL STABILITY ===' | tee -a emulator-status.txt
adb logcat -c
adb shell am force-stop "$APP_PACKAGE" || true
adb shell am start -n "$APP_PACKAGE/$ACTIVITY" --ez __scroll_stream_test true
sleep 1
adb exec-out screencap -p > emulator-stream-start.png
sleep 12
STREAM_PID="$(adb shell pidof "$APP_PACKAGE" | tr -d '\r' || true)"
test -n "$STREAM_PID"
adb logcat -d -s BUILD110_SCROLL:D '*:S' > scroll-log.txt || true
grep -q 'DONE y=' scroll-log.txt
python - <<'PY'
import re, sys
vals=[]
for line in open('scroll-log.txt', encoding='utf-8', errors='ignore'):
    m=re.search(r'old=(\d+) y=(\d+)', line)
    if m:
        vals.append(int(m.group(2)))
if len(vals) < 5:
    print('Not enough scroll samples:', len(vals)); sys.exit(1)
drops=[(a,b) for a,b in zip(vals, vals[1:]) if b < a - 4]
print('scroll_samples=', len(vals), 'first=', vals[0], 'last=', vals[-1], 'drops=', drops[:10])
if drops or vals[-1] <= vals[0]:
    sys.exit(1)
PY
adb exec-out screencap -p > emulator-stream-finish.png
adb logcat -d AndroidRuntime:E '*:S' > preview-androidruntime-errors.txt || true
! grep -q 'FATAL EXCEPTION' preview-androidruntime-errors.txt
! grep -q "Process: $APP_PACKAGE" preview-androidruntime-errors.txt

echo 'PASS: BUILD110 clean launch + theme preservation + About content + monotonic long-answer auto-scroll + no AndroidRuntime crash' | tee -a emulator-status.txt
