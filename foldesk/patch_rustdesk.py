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

# v0.2: Samsung Fold devices do not always expose half-open posture through
# Flutter MediaQuery.displayFeatures. Keep automatic detection, but also add a
# manual FoldDesk button so the keyboard/trackpad can always be forced on.
shell = widget_dst.read_text(encoding='utf-8')

shell = shell.replace(
    "class _FoldDeskShellState extends State<FoldDeskShell> {\n  bool? _lastMode;",
    "class _FoldDeskShellState extends State<FoldDeskShell> {\n  bool? _lastMode;\n  bool _manualMode = false;",
    1,
)

old_mode = """    final feature = _tableTopFeature(media);\n    final enabled = feature != null;\n    _reportMode(enabled);\n\n    if (!enabled) return widget.remote;"""
new_mode = """    final feature = _tableTopFeature(media);\n    final enabled = feature != null || _manualMode;\n    _reportMode(enabled);\n\n    if (!enabled) {\n      return Stack(\n        fit: StackFit.expand,\n        children: [\n          widget.remote,\n          Positioned(\n            right: 12,\n            bottom: 86,\n            child: SafeArea(\n              child: Material(\n                color: const Color(0xE61C222A),\n                elevation: 8,\n                borderRadius: BorderRadius.circular(22),\n                clipBehavior: Clip.antiAlias,\n                child: InkWell(\n                  onTap: () => setState(() => _manualMode = true),\n                  child: const Padding(\n                    padding: EdgeInsets.symmetric(horizontal: 14, vertical: 10),\n                    child: Row(\n                      mainAxisSize: MainAxisSize.min,\n                      children: [\n                        Icon(Icons.keyboard_alt_outlined,\n                            color: Colors.white, size: 19),\n                        SizedBox(width: 7),\n                        Text(\n                          'FoldDesk',\n                          style: TextStyle(\n                            color: Colors.white,\n                            fontWeight: FontWeight.w600,\n                            fontSize: 13,\n                          ),\n                        ),\n                      ],\n                    ),\n                  ),\n                ),\n              ),\n            ),\n          ),\n        ],\n      );\n    }"""
if old_mode not in shell:
    raise SystemExit('FoldDesk manual-mode anchor not found')
shell = shell.replace(old_mode, new_mode, 1)

old_bounds = """        final bounds = feature!.bounds;\n        final fallbackSplit = height * 0.50;\n        final candidate = bounds.center.dy > 0 ? bounds.center.dy : fallbackSplit;\n        final split = candidate.clamp(height * 0.38, height * 0.62).toDouble();\n        var topEnd = bounds.top > 0 ? bounds.top : split;\n        var bottomStart = bounds.bottom > bounds.top ? bounds.bottom : split;"""
new_bounds = """        final bounds = feature?.bounds;\n        final fallbackSplit = height * 0.50;\n        final candidate = bounds != null && bounds.center.dy > 0\n            ? bounds.center.dy\n            : fallbackSplit;\n        final split = candidate.clamp(height * 0.38, height * 0.62).toDouble();\n        var topEnd = bounds != null && bounds.top > 0 ? bounds.top : split;\n        var bottomStart = bounds != null && bounds.bottom > bounds.top\n            ? bounds.bottom\n            : split;"""
if old_bounds not in shell:
    raise SystemExit('FoldDesk bounds anchor not found')
shell = shell.replace(old_bounds, new_bounds, 1)

trackpad_anchor = """              Positioned(\n                left: 0,\n                top: bottomStart,\n                right: 0,\n                bottom: 0,\n                child: FoldDeskControlDeck(\n                  onPointerDelta: widget.onPointerDelta,\n                  onLeftClick: widget.onLeftClick,\n                  onRightClick: widget.onRightClick,\n                  onScroll: widget.onScroll,\n                  onText: widget.onText,\n                  onKey: widget.onKey,\n                ),\n              ),"""
trackpad_with_close = trackpad_anchor + """\n              if (_manualMode)\n                Positioned(\n                  right: 8,\n                  top: 8,\n                  child: SafeArea(\n                    child: Material(\n                      color: const Color(0xD91C222A),\n                      shape: const CircleBorder(),\n                      clipBehavior: Clip.antiAlias,\n                      child: IconButton(\n                        tooltip: 'FoldDesk kapat',\n                        onPressed: () => setState(() => _manualMode = false),\n                        icon: const Icon(Icons.close, color: Colors.white),\n                        iconSize: 20,\n                      ),\n                    ),\n                  ),\n                ),"""
if trackpad_anchor not in shell:
    raise SystemExit('FoldDesk close-button anchor not found')
shell = shell.replace(trackpad_anchor, trackpad_with_close, 1)
widget_dst.write_text(shell, encoding='utf-8')

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
