// BitSquiggles optional Flutter renderer and complete application facade.
// Grug 2-Clause License: do what want; not sue grug.

import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import 'bitsquiggles_core.dart' as core;

export 'bitsquiggles_core.dart'
    show
        BitSquiggleColor,
        BitSquiggleMode,
        BitSquiggleStyle,
        PixelGrid,
        VisualSpec,
        bip380ChecksumInput;

core.VisualSpec spec32(
  int input, [
  core.BitSquiggleStyle style = core.BitSquiggleStyle.standard,
]) =>
    core.spec(32, input, style);

core.VisualSpec spec40(
  int input, [
  core.BitSquiggleStyle style = core.BitSquiggleStyle.standard,
]) =>
    core.spec(40, input, style);

core.PixelGrid pixels32(
  int input, [
  core.BitSquiggleStyle style = core.BitSquiggleStyle.standard,
]) =>
    core.pixels(32, input, style);

core.PixelGrid pixels40(
  int input, [
  core.BitSquiggleStyle style = core.BitSquiggleStyle.standard,
]) =>
    core.pixels(40, input, style);

Color _color(core.BitSquiggleColor color) =>
    Color(0xff000000 | int.parse(color.hex.substring(1), radix: 16));

void _validateGrid(int width, core.PixelGrid grid) {
  final dimensions = core.dimensions(width);
  if (grid.bitWidth != width ||
      grid.width != dimensions.pixelWidth ||
      grid.height != dimensions.pixelHeight ||
      grid.pixels.length != dimensions.pixelWidth * dimensions.pixelHeight ||
      grid.pixels.any((value) => value != 0 && value != 1)) {
    throw ArgumentError.value(
      grid,
      'grid',
      'must be a canonical ${dimensions.pixelWidth}x${dimensions.pixelHeight} '
          'BitSquiggle$width grid',
    );
  }
}

void _renderRaster(
  int width,
  Canvas canvas,
  core.PixelGrid grid, {
  int pixelSize = 1,
  int offsetX = 0,
  int offsetY = 0,
}) {
  if (pixelSize <= 0) {
    throw ArgumentError.value(pixelSize, 'pixelSize', 'must be positive');
  }
  _validateGrid(width, grid);
  final background = Paint()
    ..color = _color(grid.background)
    ..isAntiAlias = false;
  final foreground = Paint()
    ..color = _color(grid.foreground)
    ..isAntiAlias = false;
  canvas.drawRect(
    Rect.fromLTWH(
      offsetX.toDouble(),
      offsetY.toDouble(),
      (grid.width * pixelSize).toDouble(),
      (grid.height * pixelSize).toDouble(),
    ),
    background,
  );
  for (var row = 0; row < grid.height; row++) {
    for (var column = 0; column < grid.width; column++) {
      if (grid.pixels[row * grid.width + column] == 0) continue;
      canvas.drawRect(
        Rect.fromLTWH(
          (offsetX + column * pixelSize).toDouble(),
          (offsetY + row * pixelSize).toDouble(),
          pixelSize.toDouble(),
          pixelSize.toDouble(),
        ),
        foreground,
      );
    }
  }
}

/// Paint an exact integer-scaled BitSquiggle32 pixel grid.
void renderRaster32(
  Canvas canvas,
  core.PixelGrid grid, {
  int pixelSize = 1,
  int offsetX = 0,
  int offsetY = 0,
}) =>
    _renderRaster(
      32,
      canvas,
      grid,
      pixelSize: pixelSize,
      offsetX: offsetX,
      offsetY: offsetY,
    );

/// Paint an exact integer-scaled BitSquiggle40 pixel grid.
void renderRaster40(
  Canvas canvas,
  core.PixelGrid grid, {
  int pixelSize = 1,
  int offsetX = 0,
  int offsetY = 0,
}) =>
    _renderRaster(
      40,
      canvas,
      grid,
      pixelSize: pixelSize,
      offsetX: offsetX,
      offsetY: offsetY,
    );

void _validateVisual(int width, core.VisualSpec visual) {
  final dimensions = core.dimensions(width);
  if (visual.bitWidth != width ||
      visual.connections.length != dimensions.edgeCount ||
      visual.connections.any((value) => value != 0 && value != 1) ||
      visual.cells.length != dimensions.rows ||
      visual.cells.any(
        (row) =>
            row.length != dimensions.columns ||
            row.any((value) => value != 0 && value != 1),
      )) {
    throw ArgumentError.value(
      visual,
      'visual',
      'must be a canonical BitSquiggle$width visual specification',
    );
  }
}

void _renderSmooth(
  int width,
  Canvas canvas,
  core.VisualSpec visual,
  Size size,
) {
  _validateVisual(width, visual);
  if (!size.width.isFinite || !size.height.isFinite) {
    throw ArgumentError.value(size, 'size', 'must be finite');
  }
  if (size.width <= 0 || size.height <= 0) return;
  final dimensions = core.dimensions(width);
  final scale = math.min(
    size.width / dimensions.pixelWidth,
    size.height / dimensions.pixelHeight,
  );
  final scaledWidth = dimensions.pixelWidth * scale;
  final scaledHeight = dimensions.pixelHeight * scale;
  final offsetX = (size.width - scaledWidth) / 2;
  final offsetY = (size.height - scaledHeight) / 2;

  canvas.drawRRect(
    RRect.fromRectAndRadius(
      Rect.fromLTWH(offsetX, offsetY, scaledWidth, scaledHeight),
      Radius.circular(scale),
    ),
    Paint()..color = _color(visual.background),
  );

  final foregroundPath = Path()..fillType = PathFillType.nonZero;
  final blobs = core.smoothBlobs(width, visual.connections);
  for (final blob in blobs) {
    foregroundPath.addRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          offsetX + (1 + blob.leftColumn * 3) * scale,
          offsetY + (1 + blob.topRow * 3) * scale,
          (2 + 3 * (blob.rightColumn - blob.leftColumn)) * scale,
          (2 + 3 * (blob.bottomRow - blob.topRow)) * scale,
        ),
        Radius.circular(scale),
      ),
    );
  }
  if (blobs.isNotEmpty) {
    canvas.drawPath(foregroundPath, Paint()..color = _color(visual.foreground));
  }
}

/// Paint a smooth BitSquiggle32 presentation.
void renderSmooth32(Canvas canvas, core.VisualSpec visual, Size size) =>
    _renderSmooth(32, canvas, visual, size);

/// Paint a smooth BitSquiggle40 presentation.
void renderSmooth40(Canvas canvas, core.VisualSpec visual, Size size) =>
    _renderSmooth(40, canvas, visual, size);

/// Reusable smooth renderer widget that consumes an already-derived spec.
final class BitSquiggleView extends StatelessWidget {
  const BitSquiggleView({
    required this.visual,
    this.width,
    this.height,
    super.key,
  });

  final core.VisualSpec visual;
  final double? width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    final dimensions = core.dimensions(visual.bitWidth);
    return CustomPaint(
      size: Size(
        width ?? dimensions.pixelWidth * 10.0,
        height ?? dimensions.pixelHeight * 10.0,
      ),
      painter: _BitSquigglePainter(visual),
    );
  }
}

final class _BitSquigglePainter extends CustomPainter {
  const _BitSquigglePainter(this.visual);

  final core.VisualSpec visual;

  @override
  void paint(Canvas canvas, Size size) =>
      _renderSmooth(visual.bitWidth, canvas, visual, size);

  @override
  bool shouldRepaint(_BitSquigglePainter oldDelegate) =>
      oldDelegate.visual != visual;
}
