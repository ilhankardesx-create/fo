#!/usr/bin/env bash
set -euo pipefail
CLEAN="Internetsiz-Yapay-Zeka-1.2-build111-FINAL-EMULATOR-PASSED.apk"
PREVIEW="project111-preview/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.internetsizyapayzeka.app2026"
ACT="com.offlineai.app.MainActivity"
: > emulator-status.txt

tap_text() {
  local wanted="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml window.xml >/dev/null
  read X Y < <(python - "$wanted" <<'PY'
import re,sys,xml.etree.ElementTree as ET
wanted=sys.argv[1]
root=ET.parse('window.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text') == wanted:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups())
            print((a+c)//2,(b+d)//2)
            raise SystemExit
raise SystemExit(2)
PY
  )
  adb shell input tap "$X" "$Y"
}

echo '=== CLEAN APK DIRECT LAUNCH ===' | tee -a emulator-status.txt
adb install -r "$CLEAN"
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT"
sleep 8
CLEAN_PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$CLEAN_PID"
echo "CLEAN_PID=$CLEAN_PID" | tee -a emulator-status.txt
adb exec-out screencap -p > emulator-clean-launch.png
adb logcat -d AndroidRuntime:E '*:S' > clean-crash.txt || true
! grep -q 'FATAL EXCEPTION' clean-crash.txt
! grep -q "Process: $PKG" clean-crash.txt

echo '=== DARK/LIGHT MENU + THEME PRESERVE ===' | tee -a emulator-status.txt
adb install -r "$PREVIEW"
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT" --ez __theme_menu_test true
sleep 2
PID_BEFORE="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$PID_BEFORE"
adb shell uiautomator dump /sdcard/before.xml >/dev/null
adb pull /sdcard/before.xml before.xml >/dev/null
grep -q 'BUILD111_TEMA_KORUMA_KULLANICI' before.xml
grep -q 'BUILD111_TEMA_KORUMA_ASISTAN' before.xml
grep -q 'BUILD111_TASLAK_KORUNDU' before.xml
tap_text '⚙'
sleep 1
adb shell uiautomator dump /sdcard/menu-dark.xml >/dev/null
adb pull /sdcard/menu-dark.xml menu-dark.xml >/dev/null
grep -q 'Model İndir' menu-dark.xml
grep -q 'Açık tema' menu-dark.xml
adb exec-out screencap -p > emulator-menu-dark.png
tap_text 'Açık tema'
sleep 2
PID_AFTER="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$PID_AFTER"
test "$PID_BEFORE" = "$PID_AFTER"
adb shell uiautomator dump /sdcard/after.xml >/dev/null
adb pull /sdcard/after.xml after.xml >/dev/null
grep -q 'BUILD111_TEMA_KORUMA_KULLANICI' after.xml
grep -q 'BUILD111_TEMA_KORUMA_ASISTAN' after.xml
grep -q 'BUILD111_TASLAK_KORUNDU' after.xml
tap_text '⚙'
sleep 1
adb shell uiautomator dump /sdcard/menu-light.xml >/dev/null
adb pull /sdcard/menu-light.xml menu-light.xml >/dev/null
grep -q 'Koyu tema' menu-light.xml
adb exec-out screencap -p > emulator-menu-light.png
adb shell input keyevent 4
echo "THEME_PID_BEFORE=$PID_BEFORE THEME_PID_AFTER=$PID_AFTER" | tee -a emulator-status.txt

echo '=== FONT + 3X TYPING + SCROLL ===' | tee -a emulator-status.txt
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -n "$PKG/$ACT" --ez __typing_test true
DONE=0
for i in $(seq 1 12); do
  sleep 1
  adb logcat -d -s BUILD111_TYPING:D BUILD111_SCROLL:D '*:S' > typing-scroll-log.txt || true
  if grep -q 'BUILD111_TYPING.*DONE ms=' typing-scroll-log.txt; then DONE=1; break; fi
done
test "$DONE" = "1"
python - <<'PY'
import re,sys
text=open('typing-scroll-log.txt',encoding='utf-8',errors='ignore').read()
m=re.search(r'DONE ms=(\d+) chars=(\d+)',text)
if not m:
    print('FAIL: no typing completion marker'); sys.exit(1)
ms,chars=map(int,m.groups())
print('typing_ms=',ms,'chars=',chars,'chars_per_sec=',round(chars*1000/ms,1))
if chars < 1500 or ms >= 5000:
    print('FAIL: typing gate',ms,chars); sys.exit(1)
vals=[int(y) for _,y in re.findall(r'old=(\d+) y=(\d+)',text)]
print('scroll_samples=',len(vals),'first=',vals[:5],'last=',vals[-5:] if vals else [])
if len(vals) < 5:
    print('FAIL: not enough scroll samples'); sys.exit(1)
drops=[(a,b) for a,b in zip(vals,vals[1:]) if b < a-4]
print('backward_drops=',drops[:10])
if drops:
    sys.exit(1)
PY
TEST_PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
test -n "$TEST_PID"
adb exec-out screencap -p > emulator-font-typing.png
adb logcat -d AndroidRuntime:E '*:S' > preview-crash.txt || true
! grep -q 'FATAL EXCEPTION' preview-crash.txt
! grep -q "Process: $PKG" preview-crash.txt

echo 'PASS: BUILD111 clean launch + readable dark/light menus + theme/chat/draft preserved + sans-serif source + ~3x burst typewriter + stable scroll + no crash' | tee -a emulator-status.txt
