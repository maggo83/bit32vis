# BitSquiggles for C99

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This port provides a dependency-free C99 implementation core and a generic
fill-rectangle framebuffer renderer. Applications include the renderer header
as their only BitSquiggles header. It exposes width-suffixed specification,
pixel, and raster operations plus the unsuffixed BIP380 helper.

The core depends only on the C standard library and the standard math library
for color conversion. Project purpose, safety boundaries, and release status
live in the [project overview](../README.md); shared behavior is in
[SPEC.md](../SPEC.md).

Both variants use one implementation core. Each conformance operation receives
a `BitsquigglesWidth`, so the selected input width remains explicit without
duplicating algorithms or result types:

| Variant | Width | Accepted input range |
| --- | --- | --- |
| BitSquiggle32 | `BITSQUIGGLES_32` | `0` through `UINT32_MAX` |
| BitSquiggle40 | `BITSQUIGGLES_40` | `0` through `BITSQUIGGLES_40_MASK` |

The BitSquiggle40 API also includes the BIP380 checksum adapter.

## 2. Include / install

### 2.1 Renderer-first integration (primary)

Copy `bitsquiggles_core.h`, `bitsquiggles_core.c`,
`bitsquiggles_renderer_framebuffer.h`, and
`bitsquiggles_renderer_framebuffer.c` into the project. Application source
includes only the renderer header; that header makes the shared styles and
result structs available without duplicating them:

```sh
cc -std=c99 -Wall -Wextra -Werror -pedantic \
    bitsquiggles_core.c bitsquiggles_renderer_framebuffer.c app.c \
    -lm -o app
```

The application supplies one callback matching `BitsquigglesFillRect`. This
adapts canonical colors and rectangles to its display, framebuffer, or GUI
without adding a platform dependency to the renderer.

### 2.2 Core-only option

Conformance harnesses, headless tools, and applications that intentionally own
a different renderer may include `bitsquiggles_core.h` directly. Copy the core
header and source into the project, then link with `-lm`:

```sh
cc -std=c99 -Wall -Wextra -Werror -pedantic bitsquiggles_core.c app.c -lm -o app
```

No allocator, operating-system API, renderer, or third-party library is
required by the core. Embedded builds that use only one variant can enable
function/data sections and linker garbage collection using their toolchain's
equivalents of `-ffunction-sections -fdata-sections -Wl,--gc-sections` so the
unreferenced width data can be omitted. Link-time optimization may reduce the
result further.

## 3. Render a BitSquiggle

Derive canonical output and draw it through the selected renderer facade. The
render operation consumes a pixel grid rather than an identity input.

### 3.1 Fill-rectangle framebuffer renderer

The renderer calls a target-owned `BitsquigglesFillRect` implementation. The
callback returns `0` on success and nonzero on failure:

```c
#include "bitsquiggles_renderer_framebuffer.h"

static int display_fill_rect(void *context, unsigned int x, unsigned int y,
                             unsigned int width, unsigned int height,
                             const BitsquigglesColor *color) {
    /* Fill the rectangle using color->hex or its numeric OKLCH fields. */
    return 0;
}

BitsquigglesPixelGrid raster;
uint64_t input;

if (bitsquiggles_bip380_checksum_input("89f8spxm", &input) == 0 &&
    bitsquiggles_pixels40(input, BITSQUIGGLES_BLACK_AND_WHITE, &raster) == 0) {
    bitsquiggles_render_raster40(display_fill_rect, display, &raster,
                                 0u, 0u, 2u);
}
```

The last three arguments are the output origin and a positive integer scale.
The renderer clears the complete output rectangle with the background color,
then batches foreground pixels into vertical runs without crossing background
pixels. A callback failure, invalid grid, width mismatch, coordinate overflow,
or zero scale produces `-1`.

The renderer allocates and retains nothing. The pixel grid, callback context,
and color pointers only need to remain valid for the duration of the call.

### 3.2 Core-only output

Core-only integrations can produce either exact raster for a target-owned
drawing layer:

```c
#include "bitsquiggles_core.h"

BitsquigglesPixelGrid raster32;
if (bitsquiggles_pixels(BITSQUIGGLES_32, UINT32_C(0x12345678),
    BITSQUIGGLES_BLACK_AND_WHITE, &raster32) == 0) {
    /* Render raster32.pixels using its background and foreground. */
}

BitsquigglesPixelGrid raster40;
if (bitsquiggles_pixels(BITSQUIGGLES_40, UINT64_C(0x39527804db),
    BITSQUIGGLES_BLACK_AND_WHITE, &raster40) == 0) {
    /* Render raster40.pixels using its background and foreground. */
}
```

The target layer must draw every source pixel as a whole pixel or integer-scaled
square according to the [exact raster](../spec/04-exact-raster.md). For smooth
output, obtain a `BitsquigglesSpec` and pass its `connections` field with the
same width to `bitsquiggles_smooth_blobs()`.

## 4. Exposed API

Shared API semantics are defined in the [API contract](../spec/06-api.md). The
[presentation chapter](../spec/03-presentation.md) owns shared style, color,
and polarity rules.

### 4.1 Framebuffer renderer API

| Operation | Function |
| --- | --- |
| Visual specification | `bitsquiggles_spec32(input, style, output)`, `bitsquiggles_spec40(input, style, output)` |
| Exact raster | `bitsquiggles_pixels32(input, style, output)`, `bitsquiggles_pixels40(input, style, output)` |
| Exact drawing | `bitsquiggles_render_raster32(fill_rect, context, grid, x, y, scale)`, `bitsquiggles_render_raster40(fill_rect, context, grid, x, y, scale)` |
| BIP380 adapter | `bitsquiggles_bip380_checksum_input(checksum, output)` |

The BIP380 name is deliberately unsuffixed because the checksum input is
inherently BitSquiggle40-only. The renderer header includes the shared type and
style declarations, so application code needs no separate BitSquiggles include.
This renderer provides exact output, not smooth drawing.

### 4.2 Core API

| Operation | Function |
| --- | --- |
| Mixer | `bitsquiggles_mix(variant, input, output)` |
| Canonical edges | `bitsquiggles_edges(variant, output)` |
| Class capacity | `bitsquiggles_free_connection_count(variant, mode)`, `bitsquiggles_usable_connection_count(variant, mode)` |
| Family membership | `bitsquiggles_matches_mode(variant, connections, mode)` |
| Mode label | `bitsquiggles_mode_label(mode)` |
| Visual specification | `bitsquiggles_spec(variant, input, style, output)` |
| Exact raster | `bitsquiggles_pixels(variant, input, style, output)` |
| Smooth geometry | `bitsquiggles_smooth_blobs(variant, connections, output)` |
| BIP380 adapter | `bitsquiggles_bip380_checksum_input(checksum, output)` |

`BitsquigglesSpec` and `BitsquigglesPixelGrid` reserve enough space for either
width. The selected width's constants define the active connection and cell
ranges; a pixel grid reports its active `width` and `height`. Unused trailing
storage is zeroed. This keeps the API allocation-free and avoids width-specific
result types.

The smooth-blob function consumes a binary connection array with 58 or 84
entries and writes inclusive-coordinate `BitsquigglesSmoothBlob` values.
Caller-owned output arrays must contain `BITSQUIGGLES_32_MAX_SMOOTH_BLOBS` (82)
or `BITSQUIGGLES_40_MAX_SMOOTH_BLOBS` (120) entries for the selected width.
It returns the blob count, or `-1` for invalid arguments; no allocation is
performed.

## 5. Test conformance

From this directory, run:

```sh
make test
```

The core harness compiles with strict C99 warnings and checks edge ordering,
family capacity, golden vectors, style-independent geometry, lossless raster
bridges, and the generated fixtures for both variants. The framebuffer harness
uses only the renderer header and checks both width-specific facades, exact
scaled output, validation, and callback failures. From the repository root,
the built harnesses can also be run directly:

```sh
c/build/test_bitsquiggles_core fixtures/v1-32.json fixtures/v1-40.json
c/build/test_bitsquiggles_renderer_framebuffer
```

When no fixture arguments are supplied, those repository-root paths are the
defaults. See
[conformance](../spec/07-conformance.md) for common requirements.

The mode tables embedded in `bitsquiggles_core.c` are generated. To regenerate
or verify them independently:

```sh
python3 generate_packed_tables.py
python3 generate_packed_tables.py --check
```

## 6. Package / release notes

The C99 port is source-only. It follows the shared version policy in
[RELEASING.md](../RELEASING.md) and is not yet published as a package or archive.

## 7. Limitations and compatibility

The API uses caller-provided output structs. It accepts inputs as `uint64_t` and
validates them against the selected width: BitSquiggle32 accepts values
through `UINT32_MAX`, and BitSquiggle40 accepts values through
`BITSQUIGGLES_40_MASK`. Width, style, input-range, and output-pointer
failures are reported by the selected function as documented in
`bitsquiggles_core.h`. See the [shared input contract](../spec/01-overview.md).

| Surface | Verified target | Notes |
| --- | --- | --- |
| Both core variants | ISO C99 compiler with C math library | No allocation or non-standard dependency. |
| Core test harness | C99 compiler with `make` | CI uses strict warnings and generated fixtures for both variants. |
| Framebuffer renderer | C99 compiler and target-owned fill callback | Exact integer-scaled drawing for both variants; no allocation or retained resources. |

## 8. License

Grug 2-Clause License. See the repository-level [LICENSE](../LICENSE).
