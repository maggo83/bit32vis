# BitSquiggle32 for Dart and Flutter

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This port provides a dependency-free Dart core and an optional Flutter Canvas
renderer. Both files are standalone and vendorable. The core imports only Dart
standard libraries; Flutter is required only by the renderer.

## 2. Include / install

### 2.1 Renderer-first Flutter integration (primary)

Copy [bitsquiggle32.dart](bitsquiggle32.dart) and
[bitsquiggles_renderer_flutter.dart](bitsquiggles_renderer_flutter.dart) into
the same source directory, then import the renderer as the complete façade:

```dart
import 'bitsquiggles_renderer_flutter.dart' as bitsquiggles;
```

The renderer exports the complete core API in addition to `renderRaster()`,
`renderSmooth()`, and `BitSquiggleView`.

### 2.2 Core-only Dart integration

For a headless or non-Flutter target, copy and import only the core:

```dart
import 'bitsquiggle32.dart' as bitsquiggles;
```

No package manifest or third-party dependency is needed.

## 3. Render a BitSquiggle

Derive identity-bearing data in the core, then pass that canonical output to a
renderer. Renderer operations intentionally never accept the identity input.

```dart
final visual = bitsquiggles.spec(0x12345678);
final grid = bitsquiggles.pixels(
  0x12345678,
  bitsquiggles.BitSquiggleStyle.blackAndWhite,
);
```

### 3.1 Exact raster rendering

Inside a Flutter painter, render an integer-scaled grid without antialiasing:

```dart
bitsquiggles.renderRaster(canvas, grid, pixelSize: 4);
```

Every source pixel becomes one whole `pixelSize` square. The resulting target
area is `16 * pixelSize` by `22 * pixelSize` logical pixels.

### 3.2 Optional smooth rendering

Render an already-derived `VisualSpec` into a target `Size`:

```dart
bitsquiggles.renderSmooth(canvas, visual, size);
```

The renderer scales the canonical 16×22 coordinates uniformly, centers them,
uses the canonical `smoothBlobs()` decomposition, and submits the foreground as
one non-zero-fill path so overlapping rounded rectangles have no seams.

For widget composition, `BitSquiggleView(visual: visual)` is a concise reusable
`CustomPaint` wrapper.

## 4. Exposed API

The core uses immutable Dart value types: `Edge`, `BitSquiggleColor`,
`VisualSpec`, `PixelGrid`, and `SmoothBlob`.

| Surface | Dart API |
| --- | --- |
| Dimensions | `rows`, `columns`, `edgeCount`, `pixelWidth`, `pixelHeight` |
| Styles | `BitSquiggleStyle` and its `values` list |
| Modes | `BitSquiggleMode` and its `values` list |
| Conformance helpers | `edges()`, `mix32()`, `freeConnectionCount()`, `matchesMode()` |
| Canonical output | `spec()`, `pixels()`, `smoothBlobs()` |
| Flutter renderer | `renderRaster()`, `renderSmooth()`, `BitSquiggleView` |

Inputs to `mix32()`, `spec()`, and `pixels()` are Dart integers in the inclusive
range `0` through `0xffffffff`. Returned mixed and input values remain unsigned
Dart integers in that range.

## 5. Test conformance

With Dart on `PATH`, run from this directory:

```sh
dart run test_bitsquiggle32.dart
```

The dependency-free executable checks dimensions, all 58 ordered edges, class
counts, the `0x89abcdef` golden vector, exact raster recovery, styles, invalid
inputs, smooth blobs, and every Java-generated vector and style in
[fixtures/v1-32.json](../fixtures/v1-32.json).

For the optional renderer, analyze it in a Flutter project that contains both
standalone source files:

```sh
flutter analyze bitsquiggles_renderer_flutter.dart
```

## 6. Package / release notes

The Dart port is source-only and not published to pub.dev. It follows the
shared version policy in [RELEASING.md](../RELEASING.md).

## 7. Limitations and compatibility

| Surface | Target |
| --- | --- |
| Core | Maintained Dart SDK; no Flutter or third-party imports |
| Renderer | Flutter Canvas and widgets |
| Exact output | Integer logical-pixel scaling; device-pixel alignment remains the caller's responsibility |
| Smooth output | Presentation-only; exact conformance remains the 16×22 raster |

## 8. License

Grug 2-Clause License. See the repository-level [LICENSE](../LICENSE).
