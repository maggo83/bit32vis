# BitSquiggle32 and BitSquiggle40 for JavaScript and TypeScript

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This guide covers the dependency-free ESM core and the bundled Canvas 2D
renderer. **Import the Canvas renderer entry point for a browser visual**: it
provides both width-explicit application APIs and is the normal application
entry point.
Importing only the core is an option for consumers that deliberately provide a
different renderer or do not render.

Project purpose, safety boundaries, and release status live in the
[project overview](../README.md). Shared behavior is defined by the
[specification](../SPEC.md).

## 2. Include / install

### 2.1 Renderer-first integration (primary)

After publication, install the package:

```bash
npm install bitsquiggles
```

For a local checkout before publication:

```bash
npm install /path/to/BitSquiggles/web
```

Import the Canvas entry point, not the bare core. It provides styles, result
types, identity adapters, canonical output, and both drawing modes from one
module:

```js
import {
  pixels32,
  pixels40,
  renderRaster32,
  renderRaster40,
  renderSmooth32,
  renderSmooth40,
  spec32,
  spec40,
} from "bitsquiggles/renderer-canvas";
```

The package has no runtime dependencies or build step. TypeScript declarations
are included. The live playground is available at
[https://maggo83.github.io/BitSquiggles/](https://maggo83.github.io/BitSquiggles/).

The playground's 32/40 switch preserves the selected variant in share URLs.
Previously shared `?value=...` links remain valid and are interpreted as
BitSquiggle32 links.

### 2.2 Core-only option

Use the bare `bitsquiggles` entry point only when an application does not draw
or supplies a different renderer. It exposes no Canvas APIs:

```js
import { pixels32, pixels40, spec32, spec40 } from "bitsquiggles";
```

## 3. Render a BitSquiggle

A renderer consumes canonical output: call `pixels32()` or `pixels40()` before
the matching `renderRaster32()` or `renderRaster40()`, and call `spec32()` or
`spec40()` before the matching smooth operation. Drawing operations do not
accept identity inputs directly.

### 3.1 Canvas 2D renderer

The Canvas renderer supports both required drawing modes:

```js
import {
  BLACK_AND_WHITE,
  HIGH_CONTRAST,
  pixels32,
  renderRaster32,
  renderSmooth32,
  spec32,
} from "bitsquiggles/renderer-canvas";

const input = 0x12345678;

const raster = pixels32(input, BLACK_AND_WHITE);
renderRaster32(rasterCanvas, raster);

const visual = spec32(input, HIGH_CONTRAST);
renderSmooth32(smoothCanvas, visual);
```

BitSquiggle32 inputs are integer JavaScript `number` values from `0` through
`0xffffffff`. BitSquiggle40 inputs are `bigint` values from `0n` through
`0xffffffffffn`:

```js
import {
  bip380ChecksumInput,
  pixels40,
  renderRaster40,
  renderSmooth40,
  spec40,
} from "bitsquiggles/renderer-canvas";

const input = bip380ChecksumInput("89f8spxm");
renderRaster40(rasterCanvas, pixels40(input));
renderSmooth40(smoothCanvas, spec40(input));
```

Exact canvases must use the native $16\times22$ or $22\times22$ dimensions, or
one uniform positive integer multiple. Smooth canvases may use any positive
scale that preserves the variant's raster aspect ratio. Drawing is immediate:
the renderer does not retain the canvas, context, visual specification, or
pixel grid after a call returns. Smooth output is presentation only; the
[exact raster](../spec/04-exact-raster.md) remains the lossless baseline.

## 4. Exposed API

Shared API semantics are defined in the [API contract](../spec/06-api.md).
`STYLES` includes `standard`, `high-contrast`, `monochrome`, and
`black-and-white`. The [presentation chapter](../spec/03-presentation.md)
owns their shared color and polarity rules.

### 4.1 Renderer entry point

| Export | Use |
| --- | --- |
| `spec32`, `spec40` | Derive canonical visual specifications. |
| `pixels32`, `pixels40` | Derive exact binary pixel grids. |
| `renderRaster32`, `renderRaster40` | Paint matching canonical grids at native or integer scale. |
| `renderSmooth32`, `renderSmooth40` | Paint matching canonical visual specifications. |
| `parseHex32`, `parseHex40` | Parse one through eight or ten hexadecimal digits. |
| `formatHex32`, `formatHex40` | Format fixed-width uppercase hexadecimal values. |
| `bip380ChecksumInput` | Convert exactly eight BIP380 checksum characters to a 40-bit input. |
| Style constants and `STYLES` | Select Standard, High contrast, Monochrome, or Black and white. |

The TypeScript declaration re-exports `Style`, `Mode`, `PixelGrid`,
`VisSpec32`, and `VisSpec40` from the same renderer entry point.

### 4.2 Core API

| Function | JavaScript result |
| --- | --- |
| `mix32(input)`, `mix40(input)` | Width-specific bijective mixed value. |
| `dimensions(width)`, `edges(width)` | Immutable variant metadata and canonical edges. |
| `freeConnectionCount(width, mode)` | Complete connection-class count. |
| `usableConnectionCount(width, mode)` | Data-bearing connection-class count. |
| `matchesMode(width, connections, mode)` | Restricted data-family membership. |
| `smoothBlobs(width, connections)` | Ordered canonical smooth rectangles. |
| Application derivation and adapters | The same width-explicit functions exposed by the renderer. |

The core also exports width-suffixed dimensions and edge arrays. Public inputs
are validated without numeric coercion. Derivation returns fresh connection,
cell, and pixel containers; static dimensions and edge definitions are frozen.
`smoothBlobs()` accepts a binary 58- or 84-entry `Uint8Array` selected by its
width argument and throws `RangeError` for invalid masks.

## 5. Test conformance

```bash
npm ci
npm test
npm run pack:check
```

The suite compiles the TypeScript package contract, checks both Java-generated
fixtures, exercises conformance properties and Canvas behavior, and runs the
playground DOM smoke test. See
[conformance](../spec/07-conformance.md) for shared requirements.

## 6. Package / release notes

The package exposes the core at `bitsquiggles` and the Canvas facade at
`bitsquiggles/renderer-canvas`; both include TypeScript declarations. The
package has no runtime dependencies and requires no build step. TypeScript is
a development-only dependency used to validate the declarations.

## 7. Limitations and compatibility

The core is standard ESM and uses native `BigInt` for 40-bit arithmetic.
Consumers without ESM or `BigInt` support need a target-specific integration
layer. See the [shared input contract](../spec/01-overview.md) for value
semantics.

| Surface | Verified target | Notes |
| --- | --- | --- |
| ESM core, declarations, and package tests | Node.js 22 and TypeScript in CI | Checks every shared fixture vector. |
| Canvas renderer | Modern browser with Canvas 2D and `roundRect()` | One renderer-first entry point for both widths. |
| Playground | Modern browser with ESM, Canvas 2D, Web Crypto, and Clipboard APIs | Node smoke tests cover DOM wiring with fakes. |

## 8. License

Grug 2-Clause License. See [LICENSE](LICENSE).
