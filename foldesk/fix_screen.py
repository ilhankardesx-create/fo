#!/usr/bin/env python3
from pathlib import Path
import sys

repo = Path(sys.argv[1] if len(sys.argv) > 1 else 'rustdesk').resolve()
shell = repo / 'flutter/lib/mobile/widgets/foldesk_shell.dart'
if not shell.exists():
    raise SystemExit(f'FoldDesk shell not found: {shell}')

text = shell.read_text(encoding='utf-8')

# v0.4: Keep RustDesk's remote widget at the same full upper-half layout size
# used by the working v0.2 build. The laptop bezel is now only a visual overlay.
# Shrinking/re-parenting the live RustDesk canvas into a smaller viewport caused
# the remote texture to render black on the Fold device.
metrics_start = text.find('        const sideBezel = 8.0;')
return_start = text.find('        return ColoredBox(', metrics_start)
if metrics_start < 0 or return_start < 0:
    raise SystemExit('FoldDesk screen metrics anchor not found')

metrics = '''        const sideBezel = 8.0;\n        const topBezel = 8.0;\n        final bottomBezel = (topEnd * 0.050).clamp(14.0, 22.0).toDouble();\n\n        final upperMedia = media.copyWith(\n          size: Size(width, topEnd),\n          displayFeatures: const <ui.DisplayFeature>[],\n          viewInsets: EdgeInsets.zero,\n          padding: EdgeInsets.zero,\n        );\n\n'''
text = text[:metrics_start] + metrics + text[return_start:]

children_anchor = text.find('            children: [', return_start)
display_start = text.find('              Positioned(', children_anchor)
hinge_start = text.find('              if (bottomStart > topEnd)', display_start)
if children_anchor < 0 or display_start < 0 or hinge_start < 0:
    raise SystemExit('FoldDesk display block anchor not found')

display = '''              Positioned(\n                left: 0,\n                top: 0,\n                right: 0,\n                height: topEnd,\n                child: ClipRect(\n                  child: MediaQuery(\n                    data: upperMedia,\n                    child: widget.remote,\n                  ),\n                ),\n              ),\n              Positioned(\n                left: 0,\n                top: 0,\n                right: 0,\n                height: topEnd,\n                child: IgnorePointer(\n                  child: Stack(\n                    children: [\n                      Positioned(\n                        left: 0,\n                        top: 0,\n                        right: 0,\n                        height: topBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        left: 0,\n                        top: 0,\n                        bottom: 0,\n                        width: sideBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        right: 0,\n                        top: 0,\n                        bottom: 0,\n                        width: sideBezel,\n                        child: const ColoredBox(color: Color(0xFF16191E)),\n                      ),\n                      Positioned(\n                        left: 0,\n                        right: 0,\n                        bottom: 0,\n                        height: bottomBezel,\n                        child: const DecoratedBox(\n                          decoration: BoxDecoration(\n                            gradient: LinearGradient(\n                              begin: Alignment.topCenter,\n                              end: Alignment.bottomCenter,\n                              colors: [Color(0xFF171A1F), Color(0xFF090B0E)],\n                            ),\n                            border: Border(\n                              top: BorderSide(color: Color(0xFF30343A)),\n                            ),\n                          ),\n                        ),\n                      ),\n                      Positioned.fill(\n                        child: DecoratedBox(\n                          decoration: BoxDecoration(\n                            border: Border.fromBorderSide(\n                              BorderSide(color: Color(0xFF3A3F46), width: 1),\n                            ),\n                          ),\n                        ),\n                      ),\n                      Positioned(\n                        top: 2,\n                        left: width / 2 - 3,\n                        child: Container(\n                          width: 6,\n                          height: 6,\n                          decoration: BoxDecoration(\n                            shape: BoxShape.circle,\n                            color: Color(0xFF050608),\n                            border: Border.fromBorderSide(\n                              BorderSide(color: Color(0xFF3B4047)),\n                            ),\n                          ),\n                        ),\n                      ),\n                    ],\n                  ),\n                ),\n              ),\n'''

text = text[:display_start] + display + text[hinge_start:]
shell.write_text(text, encoding='utf-8')
print('FoldDesk v0.4 screen rendering fix applied')
print(shell)
