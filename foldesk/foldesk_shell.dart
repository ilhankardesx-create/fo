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

        final upperMedia = media.copyWith(
          size: Size(width, topEnd),
          displayFeatures: const <ui.DisplayFeature>[],
        );

        return ColoredBox(
          color: const Color(0xFF090B0F),
          child: Stack(
            children: [
              Positioned(
                left: 0,
                top: 0,
                right: 0,
                height: topEnd,
                child: ClipRect(
                  child: MediaQuery(data: upperMedia, child: widget.remote),
                ),
              ),
              if (bottomStart > topEnd)
                Positioned(
                  left: 0,
                  right: 0,
                  top: topEnd,
                  height: bottomStart - topEnd,
                  child: const ColoredBox(color: Color(0xFF050608)),
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
    return LayoutBuilder(
      builder: (context, constraints) {
        final keyboardHeight =
            (constraints.maxHeight * 0.56).clamp(150.0, 260.0).toDouble();
        return DecoratedBox(
          decoration: const BoxDecoration(
            color: Color(0xFF111419),
            border: Border(top: BorderSide(color: Color(0xFF252A31))),
          ),
          child: Column(
            children: [
              SizedBox(
                height: keyboardHeight,
                child: _FoldDeskKeyboard(onText: onText, onKey: onKey),
              ),
              const Divider(height: 1, thickness: 1),
              Expanded(
                child: _FoldDeskTrackpad(
                  onPointerDelta: onPointerDelta,
                  onLeftClick: onLeftClick,
                  onRightClick: onRightClick,
                  onScroll: onScroll,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _FoldDeskKeyboard extends StatelessWidget {
  const _FoldDeskKeyboard({required this.onText, required this.onKey});

  final FoldDeskTextCallback onText;
  final FoldDeskKeyCallback onKey;

  static const _rows = <List<String>>[
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
    ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
    ['z', 'x', 'c', 'v', 'b', 'n', 'm'],
  ];

  Widget _key(String label, {double flex = 1, VoidCallback? onTap}) {
    return Expanded(
      flex: (flex * 100).round(),
      child: Padding(
        padding: const EdgeInsets.all(2),
        child: Material(
          color: const Color(0xFF262B33),
          borderRadius: BorderRadius.circular(7),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap ?? () => onText(label),
            child: Center(
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Text(
                  label,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (final row in _rows) {
      rows.add(
        Expanded(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: row.length < 9 ? 18 : 3),
            child: Row(children: [for (final char in row) _key(char)]),
          ),
        ),
      );
    }

    rows.add(
      Expanded(
        child: Row(
          children: [
            _key('Esc', flex: 1.1, onTap: () => onKey('VK_ESCAPE')),
            _key('Tab', flex: 1.1, onTap: () => onKey('VK_TAB')),
            _key('Space', flex: 3.8, onTap: () => onText(' ')),
            _key('⌫', flex: 1.2, onTap: () => onKey('VK_BACK')),
            _key('Enter', flex: 1.5, onTap: () => onKey('VK_RETURN')),
          ],
        ),
      ),
    );

    return Padding(
      padding: const EdgeInsets.fromLTRB(5, 5, 5, 4),
      child: Column(children: rows),
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
        _scrollAccumulator += _scrollAccumulator > 0 ? -threshold : threshold;
      }
    } else {
      widget.onPointerDelta(delta * 1.35, e.localPosition);
    }
  }

  void _up(PointerUpEvent e) {
    _positions.remove(e.pointer);
    if (_positions.isNotEmpty) return;
    final elapsed = DateTime.now().millisecondsSinceEpoch - _gestureStartMs;
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

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.opaque,
      onPointerDown: _down,
      onPointerMove: _move,
      onPointerUp: _up,
      onPointerCancel: _cancel,
      child: Stack(
        fit: StackFit.expand,
        children: [
          const ColoredBox(color: Color(0xFF171B21)),
          Center(
            child: Container(
              width: 42,
              height: 3,
              decoration: BoxDecoration(
                color: Colors.white24,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ),
          const Positioned(
            right: 10,
            bottom: 7,
            child: Text(
              '1 parmak: imleç  •  2 parmak: kaydır / sağ tık',
              style: TextStyle(color: Colors.white30, fontSize: 9),
            ),
          ),
        ],
      ),
    );
  }
}
