import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

typedef FoldDeskMoveCallback = FutureOr<void> Function(
  Offset delta,
  Offset localPosition,
);
typedef FoldDeskVoidCallback = FutureOr<void> Function();
typedef FoldDeskScrollCallback = FutureOr<void> Function(int direction);
typedef FoldDeskTextCallback = FutureOr<void> Function(String text);
typedef FoldDeskKeyCallback = FutureOr<void> Function(String key);

class FoldDeskShell extends StatefulWidget {
  const FoldDeskShell({
    super.key,
    required this.remote,
    required this.onPointerDelta,
    required this.onLeftClick,
    required this.onRightClick,
    required this.onScroll,
    required this.onText,
    required this.onKey,
    this.onModeChanged,
  });

  final Widget remote;
  final FoldDeskMoveCallback onPointerDelta;
  final FoldDeskVoidCallback onLeftClick;
  final FoldDeskVoidCallback onRightClick;
  final FoldDeskScrollCallback onScroll;
  final FoldDeskTextCallback onText;
  final FoldDeskKeyCallback onKey;
  final ValueChanged<bool>? onModeChanged;

  @override
  State<FoldDeskShell> createState() => _FoldDeskShellState();
}

class _FoldDeskShellState extends State<FoldDeskShell> {
  bool? _lastMode;

  ui.DisplayFeature? _tableTopFeature(MediaQueryData media) {
    for (final feature in media.displayFeatures) {
      final isFold = feature.type == ui.DisplayFeatureType.fold ||
          feature.type == ui.DisplayFeatureType.hinge;
      final halfOpened =
          feature.state == ui.DisplayFeatureState.postureHalfOpened;
      final horizontal = feature.bounds.width >= feature.bounds.height;
      if (isFold && halfOpened && horizontal) return feature;
    }
    return null;
  }

  void _reportMode(bool enabled) {
    if (_lastMode == enabled) return;
    _lastMode = enabled;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.onModeChanged?.call(enabled);
    });
  }

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final feature = _tableTopFeature(media);
    final enabled = feature != null;
    _reportMode(enabled);

    if (!enabled) return widget.remote;

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final height = constraints.maxHeight;
        final bounds = feature!.bounds;
        final fallbackSplit = height * 0.50;
        final candidate = bounds.center.dy > 0 ? bounds.center.dy : fallbackSplit;
        final split = candidate.clamp(height * 0.38, height * 0.62).toDouble();
        var topEnd = bounds.top > 0 ? bounds.top : split;
        var bottomStart = bounds.bottom > bounds.top ? bounds.bottom : split;
        topEnd = topEnd.clamp(height * 0.32, height * 0.68).toDouble();
        bottomStart = bottomStart.clamp(topEnd, height * 0.70).toDouble();

        final hingeGap = (height * 0.008).clamp(6.0, 12.0).toDouble();
        bottomStart = (bottomStart + hingeGap)
            .clamp(topEnd + hingeGap, height * 0.72)
            .toDouble();

        const sideBezel = 8.0;
        const topBezel = 9.0;
        final bottomBezel = (topEnd * 0.050).clamp(14.0, 24.0).toDouble();
        final viewportHeight =
            (topEnd - topBezel - bottomBezel).clamp(80.0, topEnd).toDouble();
        final viewportWidth =
            (width - sideBezel * 2).clamp(80.0, width).toDouble();

        final upperMedia = media.copyWith(
          size: Size(viewportWidth, viewportHeight),
          displayFeatures: const <ui.DisplayFeature>[],
          viewInsets: EdgeInsets.zero,
          padding: EdgeInsets.zero,
        );

        return ColoredBox(
          color: const Color(0xFF080A0D),
          child: Stack(
            children: [
              Positioned(
                left: 0,
                top: 0,
                right: 0,
                height: topEnd,
                child: DecoratedBox(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Color(0xFF1A1D22), Color(0xFF090A0D)],
                    ),
                    border: Border(
                      bottom: BorderSide(color: Color(0xFF30343A), width: 1),
                    ),
                  ),
                  child: Stack(
                    children: [
                      Positioned(
                        left: sideBezel,
                        top: topBezel,
                        right: sideBezel,
                        height: viewportHeight,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: Colors.black,
                            borderRadius: BorderRadius.circular(6),
                            border: Border.all(
                              color: const Color(0xFF2D3138),
                              width: 1,
                            ),
                            boxShadow: const [
                              BoxShadow(
                                color: Color(0xAA000000),
                                blurRadius: 8,
                                offset: Offset(0, 3),
                              ),
                            ],
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(5),
                            child: ClipRect(
                              child: MediaQuery(
                                data: upperMedia,
                                child: Align(
                                  alignment: Alignment.topCenter,
                                  child: widget.remote,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        top: 3,
                        left: width / 2 - 3,
                        child: Container(
                          width: 6,
                          height: 6,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: const Color(0xFF050608),
                            border: Border.all(color: const Color(0xFF30343A)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (bottomStart > topEnd)
                Positioned(
                  left: 0,
                  right: 0,
                  top: topEnd,
                  height: bottomStart - topEnd,
                  child: DecoratedBox(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Color(0xFF040506),
                          Color(0xFF15181D),
                          Color(0xFF040506),
                        ],
                      ),
                    ),
                    child: Center(
                      child: FractionallySizedBox(
                        widthFactor: 0.74,
                        child: Divider(
                          height: 1,
                          thickness: 1,
                          color: Color(0xFF2A2E34),
                        ),
                      ),
                    ),
                  ),
                ),
              Positioned(
                left: 0,
                top: bottomStart,
                right: 0,
                bottom: 0,
                child: FoldDeskControlDeck(
                  onPointerDelta: widget.onPointerDelta,
                  onLeftClick: widget.onLeftClick,
                  onRightClick: widget.onRightClick,
                  onScroll: widget.onScroll,
                  onText: widget.onText,
                  onKey: widget.onKey,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class FoldDeskControlDeck extends StatelessWidget {
  const FoldDeskControlDeck({
    super.key,
    required this.onPointerDelta,
    required this.onLeftClick,
    required this.onRightClick,
    required this.onScroll,
    required this.onText,
    required this.onKey,
  });

  final FoldDeskMoveCallback onPointerDelta;
  final FoldDeskVoidCallback onLeftClick;
  final FoldDeskVoidCallback onRightClick;
  final FoldDeskScrollCallback onScroll;
  final FoldDeskTextCallback onText;
  final FoldDeskKeyCallback onKey;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF1D2025), Color(0xFF101216)],
        ),
        border: Border(top: BorderSide(color: Color(0xFF34383F))),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(7, 9, 7, 9),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              flex: 76,
              child: _FoldDeskKeyboard(onText: onText, onKey: onKey),
            ),
            const SizedBox(width: 8),
            Expanded(
              flex: 24,
              child: _FoldDeskTrackpad(
                onPointerDelta: onPointerDelta,
                onLeftClick: onLeftClick,
                onRightClick: onRightClick,
                onScroll: onScroll,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _KeySpec {
  const _KeySpec(
    this.label, {
    this.text,
    this.key,
    this.flex = 1,
    this.small = false,
  });

  final String label;
  final String? text;
  final String? key;
  final double flex;
  final bool small;
}

class _FoldDeskKeyboard extends StatefulWidget {
  const _FoldDeskKeyboard({required this.onText, required this.onKey});

  final FoldDeskTextCallback onText;
  final FoldDeskKeyCallback onKey;

  @override
  State<_FoldDeskKeyboard> createState() => _FoldDeskKeyboardState();
}

class _FoldDeskKeyboardState extends State<_FoldDeskKeyboard> {
  bool _shift = false;
  bool _caps = false;

  String _letter(String value) {
    final upper = _shift != _caps;
    return upper ? value.toUpperCase() : value.toLowerCase();
  }

  void _sendText(String value) {
    final isLetter =
        value.length == 1 && RegExp(r'[a-zA-Z]').hasMatch(value);
    widget.onText(isLetter ? _letter(value) : value);
    if (_shift) setState(() => _shift = false);
  }

  void _handle(_KeySpec spec) {
    if (spec.label == 'Shift') {
      setState(() => _shift = !_shift);
      return;
    }
    if (spec.label == 'Caps') {
      setState(() => _caps = !_caps);
      widget.onKey('VK_CAPITAL');
      return;
    }
    if (spec.text != null) {
      _sendText(spec.text!);
      return;
    }
    if (spec.key != null) {
      widget.onKey(spec.key!);
      if (_shift) setState(() => _shift = false);
    }
  }

  List<_KeySpec> _functionRow() => const [
        _KeySpec('Esc', key: 'VK_ESCAPE', flex: 1.15, small: true),
        _KeySpec('F1', key: 'VK_F1', small: true),
        _KeySpec('F2', key: 'VK_F2', small: true),
        _KeySpec('F3', key: 'VK_F3', small: true),
        _KeySpec('F4', key: 'VK_F4', small: true),
        _KeySpec('F5', key: 'VK_F5', small: true),
        _KeySpec('F6', key: 'VK_F6', small: true),
        _KeySpec('F7', key: 'VK_F7', small: true),
        _KeySpec('F8', key: 'VK_F8', small: true),
        _KeySpec('F9', key: 'VK_F9', small: true),
        _KeySpec('F10', key: 'VK_F10', small: true),
        _KeySpec('F11', key: 'VK_F11', small: true),
        _KeySpec('F12', key: 'VK_F12', small: true),
        _KeySpec('Del', key: 'VK_DELETE', flex: 1.20, small: true),
      ];

  List<_KeySpec> _numberRow() => [
        const _KeySpec('~\n`', text: '`', flex: 0.85, small: true),
        _KeySpec(_shift ? '!\n1' : '1', text: _shift ? '!' : '1'),
        _KeySpec(_shift ? '@\n2' : '2', text: _shift ? '@' : '2'),
        _KeySpec(_shift ? '#\n3' : '3', text: _shift ? '#' : '3'),
        _KeySpec(_shift ? '\$\n4' : '4', text: _shift ? r'$' : '4'),
        _KeySpec(_shift ? '%\n5' : '5', text: _shift ? '%' : '5'),
        _KeySpec(_shift ? '^\n6' : '6', text: _shift ? '^' : '6'),
        _KeySpec(_shift ? '&\n7' : '7', text: _shift ? '&' : '7'),
        _KeySpec(_shift ? '*\n8' : '8', text: _shift ? '*' : '8'),
        _KeySpec(_shift ? '(\n9' : '9', text: _shift ? '(' : '9'),
        _KeySpec(_shift ? ')\n0' : '0', text: _shift ? ')' : '0'),
        _KeySpec(_shift ? '_\n-' : '-', text: _shift ? '_' : '-'),
        _KeySpec(_shift ? '+\n=' : '=', text: _shift ? '+' : '='),
        const _KeySpec('Backspace', key: 'VK_BACK', flex: 1.85, small: true),
      ];

  List<_KeySpec> _qRow() => [
        const _KeySpec('Tab', key: 'VK_TAB', flex: 1.35, small: true),
        for (final c in 'qwertyuiop'.split(''))
          _KeySpec(_letter(c), text: c),
        _KeySpec(_shift ? '{\n[' : '[', text: _shift ? '{' : '['),
        _KeySpec(_shift ? '}\n]' : ']', text: _shift ? '}' : ']'),
        _KeySpec(
          _shift ? '|\n\\' : '\\',
          text: _shift ? '|' : '\\',
          flex: 1.15,
        ),
      ];

  List<_KeySpec> _aRow() => [
        _KeySpec('Caps', key: 'VK_CAPITAL', flex: 1.65, small: true),
        for (final c in 'asdfghjkl'.split(''))
          _KeySpec(_letter(c), text: c),
        _KeySpec(_shift ? ':\n;' : ';', text: _shift ? ':' : ';'),
        _KeySpec(_shift ? '"\n\'' : "'", text: _shift ? '"' : "'"),
        const _KeySpec('Enter', key: 'VK_RETURN', flex: 1.80, small: true),
      ];

  List<_KeySpec> _zRow() => [
        _KeySpec('Shift', key: 'VK_SHIFT', flex: 2.0, small: true),
        for (final c in 'zxcvbnm'.split(''))
          _KeySpec(_letter(c), text: c),
        _KeySpec(_shift ? '<\n,' : ',', text: _shift ? '<' : ','),
        _KeySpec(_shift ? '>\n.' : '.', text: _shift ? '>' : '.'),
        _KeySpec(_shift ? '?\n/' : '/', text: _shift ? '?' : '/'),
        _KeySpec('Shift', key: 'VK_SHIFT', flex: 2.1, small: true),
      ];

  List<_KeySpec> _bottomRow() => const [
        _KeySpec('Ctrl', key: 'VK_CONTROL', flex: 1.15, small: true),
        _KeySpec('Fn', flex: 0.90, small: true),
        _KeySpec('⊞', key: 'VK_LWIN', flex: 1.05),
        _KeySpec('Alt', key: 'VK_MENU', flex: 1.05, small: true),
        _KeySpec('', text: ' ', flex: 5.2),
        _KeySpec('Alt', key: 'VK_MENU', flex: 1.05, small: true),
        _KeySpec('☰', key: 'VK_APPS', flex: 0.95),
        _KeySpec('←', key: 'VK_LEFT', flex: 0.92),
        _KeySpec('↓', key: 'VK_DOWN', flex: 0.92),
        _KeySpec('↑', key: 'VK_UP', flex: 0.92),
        _KeySpec('→', key: 'VK_RIGHT', flex: 0.92),
      ];

  Widget _row(List<_KeySpec> keys, {double weight = 1}) {
    return Expanded(
      flex: (weight * 100).round(),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final spec in keys)
            Expanded(
              flex: (spec.flex * 100).round(),
              child: Padding(
                padding: const EdgeInsets.all(2.0),
                child: _LaptopKey(
                  label: spec.label,
                  small: spec.small,
                  active: (spec.label == 'Shift' && _shift) ||
                      (spec.label == 'Caps' && _caps),
                  onTap: () => _handle(spec),
                ),
              ),
            ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFF14171B),
        borderRadius: BorderRadius.circular(11),
        border: Border.all(color: const Color(0xFF343940)),
        boxShadow: const [
          BoxShadow(
            color: Color(0xAA000000),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(5, 5, 5, 6),
        child: Column(
          children: [
            _row(_functionRow(), weight: 0.76),
            _row(_numberRow()),
            _row(_qRow()),
            _row(_aRow()),
            _row(_zRow()),
            _row(_bottomRow(), weight: 1.05),
          ],
        ),
      ),
    );
  }
}

class _LaptopKey extends StatefulWidget {
  const _LaptopKey({
    required this.label,
    required this.onTap,
    this.small = false,
    this.active = false,
  });

  final String label;
  final VoidCallback onTap;
  final bool small;
  final bool active;

  @override
  State<_LaptopKey> createState() => _LaptopKeyState();
}

class _LaptopKeyState extends State<_LaptopKey> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed == value) return;
    setState(() => _pressed = value);
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.active || _pressed;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => _setPressed(true),
      onTapCancel: () => _setPressed(false),
      onTapUp: (_) {
        _setPressed(false);
        widget.onTap();
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 55),
        transform: Matrix4.translationValues(0, _pressed ? 2 : 0, 0),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(6),
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: active
                ? const [Color(0xFF4B525C), Color(0xFF2A3037)]
                : const [Color(0xFF353A42), Color(0xFF23272D)],
          ),
          border: Border.all(
            color: active
                ? const Color(0xFF69727E)
                : const Color(0xFF111318),
          ),
          boxShadow: _pressed
              ? const []
              : const [
                  BoxShadow(
                    color: Color(0xCC050608),
                    blurRadius: 2,
                    offset: Offset(0, 3),
                  ),
                  BoxShadow(
                    color: Color(0x22FFFFFF),
                    blurRadius: 1,
                    offset: Offset(0, -1),
                  ),
                ],
        ),
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2),
            child: FittedBox(
              fit: BoxFit.scaleDown,
              child: Text(
                widget.label,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: const Color(0xFFF4F5F7),
                  fontSize: widget.small ? 9.5 : 12.0,
                  height: 0.95,
                  fontWeight:
                      widget.small ? FontWeight.w500 : FontWeight.w600,
                  shadows: const [
                    Shadow(
                      color: Colors.black,
                      blurRadius: 2,
                      offset: Offset(0, 1),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _FoldDeskTrackpad extends StatefulWidget {
  const _FoldDeskTrackpad({
    required this.onPointerDelta,
    required this.onLeftClick,
    required this.onRightClick,
    required this.onScroll,
  });

  final FoldDeskMoveCallback onPointerDelta;
  final FoldDeskVoidCallback onLeftClick;
  final FoldDeskVoidCallback onRightClick;
  final FoldDeskScrollCallback onScroll;

  @override
  State<_FoldDeskTrackpad> createState() => _FoldDeskTrackpadState();
}

class _FoldDeskTrackpadState extends State<_FoldDeskTrackpad> {
  final Map<int, Offset> _positions = <int, Offset>{};
  int _maxPointers = 0;
  int _gestureStartMs = 0;
  double _travel = 0;
  double _scrollAccumulator = 0;
  bool _leftPressed = false;
  bool _rightPressed = false;

  void _down(PointerDownEvent e) {
    if (_positions.isEmpty) {
      _gestureStartMs = DateTime.now().millisecondsSinceEpoch;
      _maxPointers = 0;
      _travel = 0;
      _scrollAccumulator = 0;
    }
    _positions[e.pointer] = e.localPosition;
    if (_positions.length > _maxPointers) _maxPointers = _positions.length;
  }

  void _move(PointerMoveEvent e) {
    final old = _positions[e.pointer];
    if (old == null) return;
    _positions[e.pointer] = e.localPosition;
    final delta = e.localPosition - old;
    _travel += delta.distance;

    if (_maxPointers >= 2 || _positions.length >= 2) {
      _scrollAccumulator += delta.dy;
      const threshold = 13.0;
      while (_scrollAccumulator.abs() >= threshold) {
        widget.onScroll(_scrollAccumulator > 0 ? 1 : -1);
        _scrollAccumulator +=
            _scrollAccumulator > 0 ? -threshold : threshold;
      }
    } else {
      widget.onPointerDelta(delta * 1.35, e.localPosition);
    }
  }

  void _up(PointerUpEvent e) {
    _positions.remove(e.pointer);
    if (_positions.isNotEmpty) return;
    final elapsed =
        DateTime.now().millisecondsSinceEpoch - _gestureStartMs;
    final isTap = elapsed <= 260 && _travel <= 18;
    if (isTap) {
      if (_maxPointers >= 2) {
        widget.onRightClick();
      } else {
        widget.onLeftClick();
      }
    }
  }

  void _cancel(PointerCancelEvent e) {
    _positions.remove(e.pointer);
    if (_positions.isEmpty) {
      _maxPointers = 0;
      _travel = 0;
      _scrollAccumulator = 0;
    }
  }

  Widget _clickButton({required bool left}) {
    final pressed = left ? _leftPressed : _rightPressed;
    return Expanded(
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) => setState(() {
          if (left) {
            _leftPressed = true;
          } else {
            _rightPressed = true;
          }
        }),
        onTapCancel: () => setState(() {
          if (left) {
            _leftPressed = false;
          } else {
            _rightPressed = false;
          }
        }),
        onTapUp: (_) {
          setState(() {
            if (left) {
              _leftPressed = false;
            } else {
              _rightPressed = false;
            }
          });
          if (left) {
            widget.onLeftClick();
          } else {
            widget.onRightClick();
          }
        },
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 55),
          margin: EdgeInsets.only(
            top: 5,
            right: left ? 2.5 : 0,
            left: left ? 0 : 2.5,
          ),
          transform: Matrix4.translationValues(0, pressed ? 2 : 0, 0),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: pressed
                  ? const [Color(0xFF2A2E34), Color(0xFF1D2025)]
                  : const [Color(0xFF3A3F47), Color(0xFF25292F)],
            ),
            border: Border.all(color: const Color(0xFF111318)),
            boxShadow: pressed
                ? const []
                : const [
                    BoxShadow(
                      color: Color(0xCC000000),
                      blurRadius: 3,
                      offset: Offset(0, 3),
                    ),
                  ],
          ),
          child: Center(
            child: Icon(
              left ? Icons.mouse_outlined : Icons.ads_click_outlined,
              size: 14,
              color: Colors.white38,
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: const Color(0xFF14171B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF343940)),
        boxShadow: const [
          BoxShadow(
            color: Color(0xAA000000),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(7),
        child: Column(
          children: [
            Expanded(
              child: Listener(
                behavior: HitTestBehavior.opaque,
                onPointerDown: _down,
                onPointerMove: _move,
                onPointerUp: _up,
                onPointerCancel: _cancel,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(9),
                    gradient: const LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [Color(0xFF2A2E34), Color(0xFF20242A)],
                    ),
                    border: Border.all(color: const Color(0xFF4A5059)),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x88000000),
                        blurRadius: 5,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: const SizedBox.expand(),
                ),
              ),
            ),
            SizedBox(
              height: 46,
              child: Row(
                children: [
                  _clickButton(left: true),
                  _clickButton(left: false),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
