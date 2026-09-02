# BitSquiggle32 and BitSquiggle40 for Java 17+

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This guide covers the Java 17+ implementation and the bundled Swing/Java2D and
JavaFX renderers for both variants. `BitSquigglesCore` is a dependency-free,
generic internal implementation. Applications use one toolkit renderer; each
renderer exposes the complete public operation set. Width-dependent operations
use explicit `32` or `40` suffixes and return neutral `BitSquiggles` model
types:

| Variant | Java input | Swing entry point | JavaFX entry point |
| --- | --- | --- | --- |
| 32-bit | unsigned bit pattern in an `int` | `BitSquigglesRendererSwing.*32(...)` | `BitSquigglesRendererJavaFX.*32(...)` |
| 40-bit | unsigned value in a `long` | `BitSquigglesRendererSwing.*40(...)` | `BitSquigglesRendererJavaFX.*40(...)` |

Use the selected toolkit renderer as the application entry point. The core is
not a public integration surface. Each renderer inherits the neutral model
types, so application source needs only the selected renderer import.

Project purpose, safety boundaries, and release status live in the
[project overview](../README.md); shared behavior is in [SPEC.md](../SPEC.md).

## 2. Include / install

### 2.1 Renderer-first integration (primary)

This port is source-only. Compile the shared model/internal implementation
module and the renderer selected by the application. The Swing renderer uses
standard JDK `java.desktop`; the JavaFX renderer needs the JavaFX SDK
`javafx.graphics` module.

```bash
mkdir -p out/core out/renderer-swing
javac -d out/core java/core/module-info.java \
    java/core/bitsquiggles/BitSquiggles.java \
    java/core/bitsquiggles/internal/BitSquigglesCore.java
javac -d out/renderer-swing --module-path out/core \
    java/renderer-swing/module-info.java \
    java/renderer-swing/bitsquiggles/renderer/swing/BitSquigglesRendererSwing.java
```

For JavaFX, replace the last command with the JavaFX compilation command in
[Test conformance](#5-test-conformance). Import the chosen renderer class in
application code; it exposes both width-specific derivation and drawing
operations.

### 2.2 Shared model and internal core

The shared module publicly exports only the neutral `bitsquiggles.BitSquiggles`
model used in renderer signatures. Its `bitsquiggles.internal` package is
qualified to the bundled renderer modules and is not an application API.

## 3. Render a BitSquiggle

Pass canonical output to the matching renderer operation: call `pixels32()` or
`pixels40()`
before `renderRaster32()` or `renderRaster40()`, and call `spec32()` or
`spec40()` before
`renderSmooth32()` or `renderSmooth40()`. Use `int` for BitSquiggle32 and
`long` for BitSquiggle40; renderer methods validate the selected variant's
unsigned domain.

### 3.1 Swing and Java2D renderer

```java
import bitsquiggles.renderer.swing.BitSquigglesRendererSwing;

int input32 = (int) Long.parseLong("89abcdef", 16);
var raster32 = BitSquigglesRendererSwing.pixels32(
    input32,
    BitSquigglesRendererSwing.Style.BLACK_AND_WHITE
);
BitSquigglesRendererSwing.renderRaster32(graphics, raster32, 4);

long input40 = Long.parseLong("39527804db", 16);
var visual40 = BitSquigglesRendererSwing.spec40(
    input40,
    BitSquigglesRendererSwing.Style.HIGH_CONTRAST
);
BitSquigglesRendererSwing.renderSmooth40(graphics, visual40, 220, 220);
```

The raster operations paint whole `pixelSize` squares. The smooth operations
draw the canonical smooth union into the available width and height.

### 3.2 JavaFX renderer

```java
import bitsquiggles.renderer.javafx.BitSquigglesRendererJavaFX;

int input32 = (int) Long.parseLong("89abcdef", 16);
var visual32 = BitSquigglesRendererJavaFX.spec32(
    input32,
    BitSquigglesRendererJavaFX.Style.HIGH_CONTRAST
);
BitSquigglesRendererJavaFX.renderSmooth32(graphics, visual32, 160, 220);

long input40 = Long.parseLong("39527804db", 16);
var raster40 = BitSquigglesRendererJavaFX.pixels40(
    input40,
    BitSquigglesRendererJavaFX.Style.BLACK_AND_WHITE
);
BitSquigglesRendererJavaFX.renderRaster40(graphics, raster40, 4);
```

The [exact raster](../spec/04-exact-raster.md) is the lossless baseline. Smooth
output is presentation-only and follows [smooth output](../spec/05-smooth-output.md).

## 4. Exposed API

Shared API semantics are defined in the [API contract](../spec/06-api.md). The
[presentation chapter](../spec/03-presentation.md) owns shared style, color,
and polarity rules.

### 4.1 Renderer entry points

| Toolkit | Renderer class | Rendering operations |
| --- | --- | --- |
| Swing/Java2D | `BitSquigglesRendererSwing` | `renderRaster32(...)`, `renderSmooth32(...)`, `renderRaster40(...)`, `renderSmooth40(...)` |
| JavaFX | `BitSquigglesRendererJavaFX` | `renderRaster32(...)`, `renderSmooth32(...)`, `renderRaster40(...)`, `renderSmooth40(...)` |

Renderer methods accept their inherited `PixelGrid` or `VisSpec` type and
reject a result created for the other width.
Each renderer class also exposes `spec32()`, `spec40()`, `pixels32()`,
`pixels40()`, and the user-facing `bip380ChecksumInput()` adapter.

### 4.2 Public model

`BitSquiggles` supplies the framework-neutral types shared by both renderer
modules. Applications access them through the selected renderer, for example
`BitSquigglesRendererSwing.Style` or
`BitSquigglesRendererJavaFX.PixelGrid`, without importing `BitSquiggles`:

| Type | Purpose |
| --- | --- |
| `Width` | Distinguishes 32-bit and 40-bit results. |
| `Style`, `Mode` | Shared presentation selections and result metadata. |
| `OklchColor` | Canonical color metadata plus an sRGB hex value. |
| `VisSpec` | Canonical smooth-presentation input returned by `spec32/40()`. |
| `PixelGrid` | Exact raster and colors returned by `pixels32/40()`. |

Arrays in returned records are mutable; treat them as immutable or copy before
sharing.

## 5. Test conformance

From the repository root:

```bash
mkdir -p out/core out/renderer-swing out/test
javac -d out/core java/core/module-info.java \
    java/core/bitsquiggles/BitSquiggles.java \
    java/core/bitsquiggles/internal/BitSquigglesCore.java \
    java/core/bitsquiggles/GalleryGenerator.java \
    java/core/bitsquiggles/ConformanceFixtureGenerator.java
javac -d out/renderer-swing --module-path out/core java/renderer-swing/module-info.java \
    java/renderer-swing/bitsquiggles/renderer/swing/BitSquigglesRendererSwing.java
javac -d out/test -cp out/core:out/renderer-swing \
    java/core/bitsquiggles/BitSquigglesCoreTest.java \
    java/core/bitsquiggles/BitSquigglesDemo.java \
    java/renderer-swing/bitsquiggles/renderer/swing/BitSquigglesRendererSwingTest.java
java -Xmx512m -cp out/core:out/renderer-swing:out/test bitsquiggles.BitSquigglesCoreTest
java -cp out/core:out/renderer-swing:out/test bitsquiggles.renderer.swing.BitSquigglesRendererSwingTest
java --module-path out/core --module io.github.maggo83.bitsquiggles/bitsquiggles.GalleryGenerator --check
java --module-path out/core --module io.github.maggo83.bitsquiggles/bitsquiggles.ConformanceFixtureGenerator --check
```

Compile JavaFX independently by pointing `JAVAFX_LIB` at the SDK `lib` directory:

```bash
javac -d out/renderer-javafx --module-path "out/core:$JAVAFX_LIB" \
    java/renderer-javafx/module-info.java \
    java/renderer-javafx/bitsquiggles/renderer/javafx/BitSquigglesRendererJavaFX.java
```

The generator checks validate all README SVGs and both versioned fixtures. See
[conformance](../spec/07-conformance.md) for common requirements.

## 6. Package / release notes

Consume this port as source today. The shared model/internal implementation
module has no third-party dependency; the Swing renderer uses standard JDK
APIs, and the JavaFX renderer uses JavaFX Graphics. Select a renderer module
for application integrations.

## 7. Limitations and compatibility

This implementation requires Java 17 or later because it uses records and
modern switch syntax. See the [shared input contract](../spec/01-overview.md)
for the 32-bit `int` and 40-bit `long` input semantics.

| Surface | Verified target | Notes |
| --- | --- | --- |
| Shared model and internal generic core | Java 17+ | JPMS dependency closure is `java.base` only. |
| Swing renderer and demo | Java 17+ with `java.desktop` | Both widths in one renderer class. |
| JavaFX renderer | Java 17+ with JavaFX Graphics | Both widths in one optional renderer class. |
| Unified core and Swing suites | Java 17+ | Check both widths, renderers, generated artifacts, and fixtures. |

## 8. License

Grug 2-Clause License. See the repository-level [LICENSE](../LICENSE).
