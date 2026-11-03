#!/usr/bin/env bash
set -euo pipefail
CLEAN_APK="Internetsiz-Yapay-Zeka-1.2-build110-FINAL-EMULATOR-PASSED.apk"
PREVIEW_APK="project110-preview/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.internetsizyapayzeka.app2026"
ACT="com.offlineai.app.MainActivity"
: > emulator-status.txt

echo '=== CLEAN LAUNCH ===' | tee -a emulator-status.txt
adb install -r "$CLEAN_APK"
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 8
PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$PID"
echo "CLEAN_PID=$PID" | tee -a emulator-status.txt
adb exec-out screencap -p > emulator-normal-launch.png
adb logcat -d AndroidRuntime:E '*:S' > clean-androidruntime-errors.txt || true
! grep -q 'FATAL EXCEPTION' clean-androidruntime-errors.txt
! grep -q "Process: $PKG" clean-androidruntime-errors.txt

echo '=== THEME PRESERVE ===' | tee -a emulator-status.txt
adb install -r "$PREVIEW_APK"
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT" --ez __theme_preserve_test true
sleep 2
PID_BEFORE="$(adb shell pidof "$PKG" | tr -d '\r')"
adb shell uiautomator dump /sdcard/theme-before.xml >/dev/null
adb pull /sdcard/theme-before.xml theme-before.xml >/dev/null
grep -q 'BUILD110_TEMA_KORUMA_KULLANICI' theme-before.xml
grep -q 'BUILD110_TEMA_KORUMA_ASISTAN' theme-before.xml
grep -q 'BUILD110_TASLAK_KORUNDU' theme-before.xml
adb exec-out screencap -p > emulator-theme-before.png
sleep 4
PID_AFTER="$(adb shell pidof "$PKG" | tr -d '\r')"
test "$PID_BEFORE" = "$PID_AFTER"
adb shell uiautomator dump /sdcard/theme-after.xml >/dev/null
adb pull /sdcard/theme-after.xml theme-after.xml >/dev/null
grep -q 'BUILD110_TEMA_KORUMA_KULLANICI' theme-after.xml
grep -q 'BUILD110_TEMA_KORUMA_ASISTAN' theme-after.xml
grep -q 'BUILD110_TASLAK_KORUNDU' theme-after.xml
adb shell run-as "$PKG" cat shared_prefs/offline_ai_prefs.xml > theme-prefs.xml
grep -q 'name="dark_theme" value="false"' theme-prefs.xml
adb exec-out screencap -p > emulator-theme-after.png
echo "THEME_PID_BEFORE=$PID_BEFORE THEME_PID_AFTER=$PID_AFTER" | tee -a emulator-status.txt

echo '=== ABOUT ===' | tee -a emulator-status.txt
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT" --ez __about_test true
sleep 3
adb shell uiautomator dump /sdcard/about.xml >/dev/null
adb pull /sdcard/about.xml about.xml >/dev/null
grep -q 'İlhan Kardeş' about.xml
grep -q 'ilhankardesx@gmail.com' about.xml
grep -q 'KULLANIM KILAVUZU' about.xml
adb exec-out screencap -p > emulator-about.png

echo '=== 20 SECOND LONG STREAM STABILITY ===' | tee -a emulator-status.txt
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT" --ez __scroll_stream_test true
sleep 2
STREAM_PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$STREAM_PID"
adb exec-out screencap -p > emulator-stream-start.png
sleep 10
adb exec-out screencap -p > emulator-stream-mid.png
sleep 10
STREAM_PID2="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$STREAM_PID2"
adb logcat -d -s BUILD110_SCROLL:D '*:S' > scroll-log.txt || true
python - <<'PY'
import re,sys
vals=[]
for line in open('scroll-log.txt',encoding='utf-8',errors='ignore'):
    m=re.search(r'old=(\d+) y=(\d+)',line)
    if m: vals.append(int(m.group(2)))
print('scroll_samples=',len(vals),'first=',vals[:5],'last=',vals[-5:] if vals else [])
if len(vals) < 15:
    print('FAIL: not enough scroll samples'); sys.exit(1)
drops=[(i,a,b) for i,(a,b) in enumerate(zip(vals,vals[1:])) if b < a-4]
print('backward_drops=',drops[:10])
if drops:
    sys.exit(1)
if vals[-1] <= vals[0] + 300:
    print('FAIL: scroll did not advance enough'); sys.exit(1)
PY
adb exec-out screencap -p > emulator-stream-finish.png
if cmp -s emulator-stream-start.png emulator-stream-finish.png; then
  echo 'FAIL: stream screen did not change' | tee -a emulator-status.txt
  exit 1
fi
adb logcat -d AndroidRuntime:E '*:S' > preview-androidruntime-errors.txt || true
! grep -q 'FATAL EXCEPTION' preview-androidruntime-errors.txt
! grep -q "Process: $PKG" preview-androidruntime-errors.txt

echo 'PASS: BUILD110 clean launch + theme/chat/draft preserve + About + 20s monotonic streaming scroll + no AndroidRuntime crash' | tee -a emulator-status.txt
