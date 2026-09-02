import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../bitsquiggles_renderer_flutter.dart' as bit;

Future<Uint8List> renderImage(
  int width,
  int height,
  void Function(ui.Canvas canvas) draw,
) async {
  final recorder = ui.PictureRecorder();
  draw(ui.Canvas(recorder));
  final picture = recorder.endRecording();
  final image = await picture.toImage(width, height);
  final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
  final bytes = Uint8List.fromList(
    data!.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
  );
  image.dispose();
  picture.dispose();
  return bytes;
}

List<int> rgba(String hex) => [
      int.parse(hex.substring(1, 3), radix: 16),
      int.parse(hex.substring(3, 5), radix: 16),
      int.parse(hex.substring(5, 7), radix: 16),
      255,
    ];

Future<void> expectExactRaster(
  bit.PixelGrid grid,
  void Function(ui.Canvas canvas, bit.PixelGrid grid) render,
) async {
  final image = await renderImage(
    grid.width,
    grid.height,
    (canvas) => render(canvas, grid),
  );
  final background = rgba(grid.background.hex);
  final foreground = rgba(grid.foreground.hex);
  for (var index = 0; index < grid.pixels.length; index++) {
    final expected = grid.pixels[index] == 0 ? background : foreground;
    expect(image.sublist(index * 4, index * 4 + 4), expected);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('facade derives both canonical widths', () {
    expect(bit.spec32(0x89abcdef).mixed, 0x47ac5876);
    expect(bit.spec40(0x39527804db).mixed, 0x34b1a077c8);
    expect(bit.pixels32(0).pixels.length, 16 * 22);
    expect(bit.pixels40(0).pixels.length, 22 * 22);
    expect(bit.bip380ChecksumInput('89f8spxm'), 0x39527804db);
  });

  test('exact renderer reproduces every 32-bit source pixel', () async {
    await expectExactRaster(
      bit.pixels32(0x89abcdef),
      (canvas, grid) => bit.renderRaster32(canvas, grid),
    );
  });

  test('exact renderer reproduces every 40-bit source pixel', () async {
    await expectExactRaster(
      bit.pixels40(0x39527804db),
      (canvas, grid) => bit.renderRaster40(canvas, grid),
    );
  });

  test('exact renderer rejects invalid scale and cross-width grids', () {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    expect(
      () => bit.renderRaster32(canvas, bit.pixels32(0), pixelSize: 0),
      throwsArgumentError,
    );
    expect(
      () => bit.renderRaster32(canvas, bit.pixels40(0)),
      throwsArgumentError,
    );
    expect(
      () => bit.renderRaster40(canvas, bit.pixels32(0)),
      throwsArgumentError,
    );
    recorder.endRecording().dispose();
  });

  for (final width in [32, 40]) {
    test('smooth renderer paints BitSquiggle$width', () async {
      final visual =
          width == 32 ? bit.spec32(0x89abcdef) : bit.spec40(0x39527804db);
      final size = width == 32 ? const Size(160, 220) : const Size(220, 220);
      final image = await renderImage(
        size.width.toInt(),
        size.height.toInt(),
        (canvas) => width == 32
            ? bit.renderSmooth32(canvas, visual, size)
            : bit.renderSmooth40(canvas, visual, size),
      );
      final foreground = rgba(visual.foreground.hex);
      var foregroundPixels = 0;
      var nonTransparentPixels = 0;
      for (var offset = 0; offset < image.length; offset += 4) {
        if (image[offset + 3] != 0) nonTransparentPixels++;
        var foregroundMatch = true;
        for (var channel = 0; channel < 4; channel++) {
          foregroundMatch &= image[offset + channel] == foreground[channel];
        }
        if (foregroundMatch) foregroundPixels++;
      }
      expect(nonTransparentPixels, greaterThan(0));
      expect(foregroundPixels, greaterThan(0));
    });
  }

  test('smooth renderer rejects cross-width visuals and non-finite sizes', () {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    expect(
      () => bit.renderSmooth32(canvas, bit.spec40(0), const Size(160, 220)),
      throwsArgumentError,
    );
    expect(
      () => bit.renderSmooth40(canvas, bit.spec32(0), const Size(220, 220)),
      throwsArgumentError,
    );
    expect(
      () => bit.renderSmooth32(
        canvas,
        bit.spec32(0),
        const Size(double.infinity, 220),
      ),
      throwsArgumentError,
    );
    recorder.endRecording().dispose();
  });

  testWidgets('view derives natural dimensions from its variant', (
    tester,
  ) async {
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: bit.BitSquiggleView(visual: bit.spec40(0x39527804db)),
      ),
    );
    final paint = tester.widget<CustomPaint>(find.byType(CustomPaint));
    expect(paint.size, const Size(220, 220));
  });
}
