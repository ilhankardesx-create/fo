#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

repo = Path(sys.argv[1] if len(sys.argv) > 1 else 'rustdesk').resolve()
workspace = Path(__file__).resolve().parent
remote = repo / 'flutter/lib/mobile/pages/remote_page.dart'
widget_src = workspace / 'foldesk_shell.dart'
widget_dst = repo / 'flutter/lib/mobile/widgets/foldesk_shell.dart'

if not remote.exists():
    raise SystemExit(f'RustDesk remote_page.dart not found: {remote}')

text = remote.read_text(encoding='utf-8')
widget_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(widget_src, widget_dst)

import_line = "import '../widgets/foldesk_shell.dart';\n"
anchor = "import '../widgets/custom_scale_widget.dart';\n"
if import_line not in text:
    if anchor not in text:
        raise SystemExit('FoldDesk import anchor not found')
    text = text.replace(anchor, anchor + import_line, 1)

state_anchor = "  bool _showGestureHelp = false;\n"
state_insert = (
    "  bool _showGestureHelp = false;\n"
    "  bool _foldDeskMode = false;\n"
    "  bool _showBarBeforeFoldDesk = true;\n"
)
if 'bool _foldDeskMode' not in text:
    if state_anchor not in text:
        raise SystemExit('FoldDesk state anchor not found')
    text = text.replace(state_anchor, state_insert, 1)

show_action_old = "    final showActionButton = !_showBar || keyboardIsVisible || _showGestureHelp;"
show_action_new = (
    "    final showActionButton = !_foldDeskMode &&\n"
    "        (!_showBar || keyboardIsVisible || _showGestureHelp);"
)
if show_action_old in text:
    text = text.replace(show_action_old, show_action_new, 1)
elif '_foldDeskMode &&' not in text:
    raise SystemExit('FoldDesk action-button anchor not found')

old = """                              return Container(\n                                color: MyTheme.canvasColor,\n                                child: inputModel.isPhysicalMouse.value\n                                    ? getBodyForMobile()\n                                    : RawTouchGestureDetectorRegion(\n                                        child: getBodyForMobile(),\n                                        ffi: gFFI,\n                                      ),\n                              );"""

new = """                              final remoteBody = Container(\n                                color: MyTheme.canvasColor,\n                                child: inputModel.isPhysicalMouse.value\n                                    ? getBodyForMobile()\n                                    : RawTouchGestureDetectorRegion(\n                                        child: getBodyForMobile(),\n                                        ffi: gFFI,\n                                      ),\n                              );\n                              return FoldDeskShell(\n                                remote: remoteBody,\n                                onModeChanged: (enabled) {\n                                  if (!mounted || enabled == _foldDeskMode) return;\n                                  setState(() {\n                                    if (enabled) {\n                                      _showBarBeforeFoldDesk = _showBar;\n                                      _showBar = false;\n                                      _showGestureHelp = false;\n                                    } else {\n                                      _showBar = _showBarBeforeFoldDesk;\n                                    }\n                                    _foldDeskMode = enabled;\n                                  });\n                                  Future.delayed(\n                                      const Duration(milliseconds: 140), () {\n                                    if (mounted) {\n                                      gFFI.canvasModel.updateViewStyle();\n                                    }\n                                  });\n                                },\n                                onPointerDelta: (delta, localPosition) async {\n                                  if (inputModel.isViewOnly) return;\n                                  await gFFI.cursorModel.updatePan(\n                                      delta, localPosition, false);\n                                },\n                                onLeftClick: () async {\n                                  if (inputModel.isViewOnly) return;\n                                  await inputModel.tap(MouseButtons.left);\n                                },\n                                onRightClick: () async {\n                                  if (inputModel.isViewOnly) return;\n                                  await inputModel.tap(MouseButtons.right);\n                                },\n                                onScroll: (direction) {\n                                  if (inputModel.isViewOnly) return;\n                                  inputModel.scroll(direction);\n                                },\n                                onText: (value) {\n                                  if (inputModel.isViewOnly ||\n                                      !inputModel.keyboardInputAllowed) return;\n                                  bind.sessionInputString(\n                                      sessionId: sessionId, value: value);\n                                },\n                                onKey: (key) {\n                                  if (inputModel.isViewOnly ||\n                                      !inputModel.keyboardInputAllowed) return;\n                                  inputModel.inputKey(key);\n                                },\n                              );"""

if 'return FoldDeskShell(' not in text:
    if old not in text:
        raise SystemExit('FoldDesk remote body anchor not found')
    text = text.replace(old, new, 1)

remote.write_text(text, encoding='utf-8')

# Keep upstream Android namespace intact, but give the test APK its own package
# so it can be installed beside official RustDesk.
gradle = repo / 'flutter/android/app/build.gradle'
g = gradle.read_text(encoding='utf-8')
g = g.replace('applicationId "com.carriez.flutter_hbb"',
              'applicationId "com.foldesk.remote"', 1)
gradle.write_text(g, encoding='utf-8')

# Replace only visible app labels where available.
manifest = repo / 'flutter/android/app/src/main/AndroidManifest.xml'
if manifest.exists():
    m = manifest.read_text(encoding='utf-8')
    m = m.replace('android:label="RustDesk"', 'android:label="FoldDesk"')
    manifest.write_text(m, encoding='utf-8')

for strings in (repo / 'flutter/android/app/src/main/res').glob('values*/strings.xml'):
    s = strings.read_text(encoding='utf-8')
    s = s.replace('>RustDesk<', '>FoldDesk<')
    strings.write_text(s, encoding='utf-8')

print('FoldDesk RustDesk patch applied successfully')
print(f'Patched: {remote}')
print(f'Added:   {widget_dst}')
