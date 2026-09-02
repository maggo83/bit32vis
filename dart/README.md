# BitSquiggles for Dart and Flutter

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This port provides a dependency-free Dart core and an optional Flutter Canvas
renderer. The source files are vendorable. The core imports only Dart
standard libraries; Flutter is required only by the renderer.

Both variants share `bitsquiggles_core.dart`. Flutter applications use the
single `bitsquiggles_renderer_flutter.dart` façade. BitSquiggle32 inputs range
through `0xffffffff`; BitSquiggle40 inputs range through `0xffffffffff`.

## 2. Include / install

### 2.1 Renderer-first Flutter integration (primary)

Copy [bitsquiggles_core.dart](bitsquiggles_core.dart) and
[bitsquiggles_renderer_flutter.dart](bitsquiggles_renderer_flutter.dart) into
the same source directory, then import the renderer as the complete façade:

```dart
import 'bitsquiggles_renderer_flutter.dart' as bitsquiggles;
```

The renderer exposes styles, result types, the BIP380 checksum adapter,
`spec32()`/`spec40()`, `pixels32()`/`pixels40()`, both drawing modes, and
`BitSquiggleView`. An application needs no second BitSquiggles import.

### 2.2 Core-only Dart integration

For a headless or non-Flutter target, copy and import only the shared core:

```dart
import 'bitsquiggles_core.dart' as bitsquiggles;
```

No package manifest or third-party dependency is needed.

## 3. Render a BitSquiggle

Derive identity-bearing data in the core, then pass that canonical output to a
renderer. Renderer operations intentionally never accept the identity input.

```dart
final visual = bitsquiggles.spec32(0x12345678);
final input40 = bitsquiggles.bip380ChecksumInput('89f8spxm');
final grid = bitsquiggles.pixels40(
  input40,
  bitsquiggles.BitSquiggleStyle.blackAndWhite,
);
```

### 3.1 Exact raster rendering

Inside a Flutter painter, render an integer-scaled grid without antialiasing:

```dart
bitsquiggles.renderRaster40(canvas, grid, pixelSize: 4);
```

Every source pixel becomes one whole `pixelSize` square. The resulting target
area is 16×22 source pixels for BitSquiggle32 and 22×22 for BitSquiggle40.
The renderer validates the variant and complete grid before painting.

### 3.2 Optional smooth rendering

Render an already-derived `VisualSpec` into a target `Size`:

```dart
bitsquiggles.renderSmooth32(canvas, visual, size);
```

The renderer scales the selected variant's exact-raster coordinates uniformly,
centers them, uses the canonical blob decomposition, and submits the foreground
as one non-zero-fill path so overlapping rounded rectangles have no seams.

For widget composition, `BitSquiggleView(visual: visual)` is a concise reusable
`CustomPaint` wrapper.

## 4. Exposed API

The core uses immutable Dart value types: `BitSquiggleDimensions`, `Edge`,
`BitSquiggleColor`, `VisualSpec`, `PixelGrid`, and `SmoothBlob`.

| Surface | Dart API |
| --- | --- |
| Core dimensions | `dimensions(width)` |
| Core diagnostics | `mix(width, input)`, `edges(width)`, class counts, `matchesMode(width, ...)`, `smoothBlobs(width, ...)` |
| Core output | `spec(width, input)`, `pixels(width, input)` |
| Identity adapter | `bip380ChecksumInput(checksum)` |
| Flutter derivation | `spec32()`, `spec40()`, `pixels32()`, `pixels40()` |
| Flutter rendering | `renderRaster32()`, `renderRaster40()`, `renderSmooth32()`, `renderSmooth40()`, `BitSquiggleView` |

Inputs and mixed values are unsigned Dart `int` values in the selected range.
The core uses exact `BigInt` intermediates where 40-bit multiplication or the
84-feature smooth working set would exceed a narrower integer representation.

Every result owns fresh immutable lists. Render operations consume caller-owned
Canvas, result, and size objects only for the duration of the call; the library
retains none of them.

## 5. Test conformance

With Dart on `PATH`, run from this directory:

```sh
flutter pub get
dart format --output=none --set-exit-if-changed bitsquiggles_core.dart bitsquiggles_renderer_flutter.dart test_bitsquiggles_core.dart test_bitsquiggles_web.dart test
dart run test_bitsquiggles_core.dart
dart compile js -O2 test_bitsquiggles_web.dart -o /tmp/bitsquiggles_web_test.js
node /tmp/bitsquiggles_web_test.js
flutter analyze bitsquiggles_core.dart bitsquiggles_renderer_flutter.dart test_bitsquiggles_core.dart test
flutter test
```

The dependency-free executable covers both generated fixtures, canonical
dimensions and edges, class counts, mixers, assignment, fallback, the 40-bit
marker and rotation separation, BIP380 conversion, colors, cells, exact raster
recovery, smooth blob ordering, validation, uniqueness samples, and ownership.
The non-published Flutter harness renders real Canvas images for both variants,
checks every exact source pixel, exercises smooth output, rejects cross-width
results, and verifies natural widget sizing.
The compile-to-JavaScript smoke test verifies exact 40-bit arithmetic and the
84-edge smooth working set in a web runtime.

## 6. Package / release notes

The Dart port is source-only and not published to pub.dev. It follows the
shared version policy in [RELEASING.md](../RELEASING.md). `pubspec.yaml` and
`pubspec.lock` exist only to make renderer validation reproducible.

## 7. Limitations and compatibility

| Surface | Target |
| --- | --- |
| Core | Dart 3 or newer; no Flutter or third-party imports |
| Renderer | Stable Flutter Canvas and widgets |
| Exact output | Integer logical-pixel scaling; device-pixel alignment remains the caller's responsibility |
| Smooth output | Presentation-only; exact conformance remains the 16×22 or 22×22 raster |

## 8. License

Grug 2-Clause License. See the repository-level [LICENSE](../LICENSE).
