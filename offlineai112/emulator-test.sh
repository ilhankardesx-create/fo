#!/usr/bin/env bash
set -euo pipefail

APK="project112/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.internetsizyapayzeka.app2026"

adb install -r "$APK"
adb shell am force-stop "$PKG" || true
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3

adb shell uiautomator dump /sdcard/before.xml >/dev/null
adb pull /sdcard/before.xml before.xml >/dev/null

python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse('before.xml').getroot()
node=None
for n in root.iter('node'):
    if n.attrib.get('text') == '+':
        node=n
        break
if node is None:
    raise SystemExit('PLUS_BUTTON_NOT_FOUND')
m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds'])
if not m:
    raise SystemExit('PLUS_BOUNDS_INVALID')
x1,y1,x2,y2=map(int,m.groups())
open('plus-bounds.txt','w').write(node.attrib['bounds']+'\n')
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
sleep 1

adb shell uiautomator dump /sdcard/menu.xml >/dev/null
adb pull /sdcard/menu.xml menu.xml >/dev/null
adb exec-out screencap -p > emulator-attachment-menu.png

grep -q 'text="Fotoğraflar"' menu.xml
grep -q 'text="Dosyalar"' menu.xml

python3 - <<'PY'
import re, xml.etree.ElementTree as ET
plus=open('plus-bounds.txt').read().strip()
pm=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', plus)
px1,py1,px2,py2=map(int,pm.groups())
root=ET.parse('menu.xml').getroot()
ys=[]
for label in ('Fotoğraflar','Dosyalar'):
    found=False
    for n in root.iter('node'):
        if n.attrib.get('text') == label:
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib['bounds'])
            if not m: raise SystemExit(label+'_BOUNDS_INVALID')
            x1,y1,x2,y2=map(int,m.groups())
            ys.append((label,y1,y2))
            found=True
            break
    if not found: raise SystemExit(label+'_NOT_FOUND')
# Menu must be visibly above the bottom + button.
if max(y2 for _,_,y2 in ys) > py1:
    raise SystemExit('ATTACHMENT_MENU_NOT_ABOVE_PLUS')
open('menu-bounds.txt','w').write('\n'.join(f'{a}: {b}-{c}' for a,b,c in ys)+'\n')
PY

PID="$(adb shell pidof "$PKG" | tr -d '\r' || true)"
if [ -n "$PID" ]; then
  adb logcat -d --pid="$PID" > app-logcat.txt || true
else
  adb logcat -d > app-logcat.txt || true
fi
if grep -E 'FATAL EXCEPTION|AndroidRuntime.*Process: com\.internetsizyapayzeka\.app2026' app-logcat.txt; then
  echo 'APP_CRASH_DETECTED'
  exit 1
fi

echo 'BUILD112_ATTACHMENT_MENU_TEST_PASS' | tee emulator-status.txt
