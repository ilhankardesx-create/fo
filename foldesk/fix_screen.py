#!/usr/bin/env python3
from pathlib import Path
import sys

repo = Path(sys.argv[1] if len(sys.argv) > 1 else 'rustdesk').resolve()
shell = repo / 'flutter/lib/mobile/widgets/foldesk_shell.dart'
if not shell.exists():
    raise SystemExit(f'FoldDesk shell not found: {shell}')

text = shell.read_text(encoding='utf-8')

# v0.4: Keep RustDesk's remote widget at the exact upper-half layout size used
# by the working v0.2 build. The laptop bezel is a visual overlay only.
# This avoids resizing/re-parenting the live RustDesk canvas, which rendered
# black on the Fold device in v0.3.
metrics_start = text.find('        const sideBezel = 8.0;')
return_start = text.find('        return ColoredBox(', metrics_start)
if metrics_start < 0 or return_start < 0:
    raise SystemExit('FoldDesk screen metrics anchor not found')

metrics = '''        const sideBezel = 8.0;\n        const topBezel = 8.0;\n        final bottomBezel = (topEnd * 0.050).clamp(14.0, 22.0).toDouble();\n\n        final upperMedia = media.copyWith(\n          size: Size(width, topEnd),\n          displayFeatures: const <ui.DisplayFeature>[],\n          viewInsets: EdgeInsets.zero,\n          padding: EdgeInsets.zero,\n        );\n\n'''
text = text[:metrics_start] + metrics + text[return_start:]

# Recompute indexes after the replacement above. Reusing the old return_start
# can point inside the nested screen Stack when the replacement length changes,
# which previously produced an unbalanced Dart tree.
return_start = text.find('        return ColoredBox(', metrics_start)
stack_anchor = '''          child: Stack(\n            children: [\n'''
children_anchor = text.find(stack_anchor, return_start)
if return_start < 0 or children_anchor < 0:
    raise SystemExit('FoldDesk main Stack anchor not found')

children_start = children_anchor + len(stack_anchor)
old_display_signature = '''              Positioned(\n                left: 0,\n                top: 0,\n                right: 0,\n                height: topEnd,\n'''
display_start = text.find(old_display_signature, children_start)
hinge_start = text.find('              if (bottomStart > topEnd)', display_start)
if display_start < 0 or hinge_start < 0:
    raise SystemExit('FoldDesk display block anchor not found')

display = '''              Positioned(\n                left: 0,\n                top: 0,\n                right: 0,\n                height: topEnd,\n                child: ClipRect(\n                  child: MediaQuery(\n                    data: upperMedia,\n                    child: widget.remote,\n                  ),\n                ),\n              ),\n              Positioned(\n                left: 0,\n                top: 0,\n                right: 0,\n                height: topEnd,\n                child: IgnorePointer(\n                  child: Stack(\n                    children: [\n                      Positioned(\n                        left: 0,\n                        top: 0,\n                        right: 0,\n                        height: topBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        left: 0,\n                        top: 0,\n                        bottom: 0,\n                        width: sideBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        right: 0,\n                        top: 0,\n                        bottom: 0,\n                        width: sideBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        left: 0,\n                        right: 0,\n                        bottom: 0,\n                        height: bottomBezel,\n                        child: const DecoratedBox(\n                          decoration: BoxDecoration(\n                            gradient: LinearGradient(\n                              begin: Alignment.topCenter,\n                              end: Alignment.bottomCenter,\n                              colors: [\n                                Color(0xFF171A1F),\n                                Color(0xFF090B0E),\n                              ],\n                            ),\n                            border: Border(\n                              top: BorderSide(color: Color(0xFF30343A)),\n                            ),\n                          ),\n                        ),\n                      ),\n                      Positioned.fill(\n                        child: DecoratedBox(\n                          decoration: BoxDecoration(\n                            border: Border.fromBorderSide(\n                              BorderSide(\n                                color: Color(0xFF3A3F46),\n                                width: 1,\n                              ),\n                            ),\n                          ),\n                        ),\n                      ),\n                      Positioned(\n                        top: 2,\n                        left: width / 2 - 3,\n                        child: Container(\n                          width: 6,\n                          height: 6,\n                          decoration: BoxDecoration(\n                            shape: BoxShape.circle,\n                            color: Color(0xFF050608),\n                            border: Border.fromBorderSide(\n                              BorderSide(color: Color(0xFF3B4047)),\n                            ),\n                          ),\n                        ),\n                      ),\n                    ],\n                  ),\n                ),\n              ),\n'''

text = text[:display_start] + display + text[hinge_start:]
shell.write_text(text, encoding='utf-8')
print('FoldDesk v0.4 screen rendering fix applied')
print(shell)
