# BitSquiggles for Python and MicroPython

← Back to the [BitSquiggles project overview](../README.md). Read the
[normative specification](../SPEC.md) for behavior shared by every port.

## 1. Status and scope

This guide covers the dependency-free BitSquiggles integrations for CPython
and MicroPython, including PyQt6, LVGL, and generic fill-rectangle framebuffer
renderers. **Use a selected renderer module as the normal application import**.
Each renderer exposes width-explicit derivation and drawing operations for the
supported input widths.

The port has one dependency-free implementation module,
`bitsquiggles_core.py`. It owns common presentation and raster behavior plus
the 32-bit and 40-bit encoders. Each optional renderer is one additional file
that imports this core directly; there is no intermediate façade or
width-specific implementation module.

Project purpose, safety boundaries, and release status live in the
[project overview](../README.md); shared behavior is in [SPEC.md](../SPEC.md).

## 2. Include / install

### 2.1 Renderer-first integration (primary)

For MicroPython, copy `bitsquiggles_core.py` and one selected renderer to the
device:

- `bitsquiggle_renderer_lvgl.py` for LVGL;
- `bitsquiggle_renderer_framebuffer.py` for any target exposing
  `fill_rect(x, y, width, height, color)`.

Import the selected renderer in application code. It provides `spec32()`,
`pixels32()`, `spec40()`, `pixels40()`, and matching render operations from one
module.

On RAM-constrained targets, freeze `bitsquiggles_core.py` and the selected
renderer into the firmware. The core's precomputed mode tables are immutable
`bytes` constants, so a frozen MicroPython module can read them directly from
flash rather than constructing variant dictionaries and edge tuples in heap
RAM at import time.

For CPython, install the core from a checkout:

```bash
python3 -m pip install .
```

No third-party runtime dependency is installed for core or framebuffer use.
The LVGL module imports `lvgl` only when that renderer is selected.

Install the optional PyQt6 renderer and its framework dependency with:

```bash
python3 -m pip install '.[pyqt6]'
```

### 2.2 Headless integration

Import `bitsquiggles_core` when an application does not draw or owns a
different renderer. Core operations select the input width explicitly:

```python
import bitsquiggles_core as bitsquiggles

raster32 = bitsquiggles.pixels(32, 0x12345678)
visual40 = bitsquiggles.spec(
  40,
  bitsquiggles.bip380_checksum_input("89f8spxm")
)
```

## 3. Render a BitSquiggle

Use the renderer entry point to derive canonical output and render it. For the
desired width `<X>`, call `pixels<X>()` before the matching `render_raster<X>()`
operation. Use `spec<X>()` before the matching `render_smooth<X>()` operation.
Render operations consume canonical values (i.e. the output of `pixels<X>()`
or `spec<X>()`), never a BitSquiggle input.

### 3.1 LVGL renderer

The LVGL renderer supports exact raster and smooth output:

```python
import bitsquiggle_renderer_lvgl as bitsquiggles

grid32 = bitsquiggles.pixels32(0x12345678, bitsquiggles.BLACK_AND_WHITE)
image32, descriptor32, pixel_buffer32 = bitsquiggles.render_raster32(
  parent, grid32, scale=2
)

input40 = bitsquiggles.bip380_checksum_input("89f8spxm")
visual40 = bitsquiggles.spec40(input40, bitsquiggles.HIGH_CONTRAST)
smooth40, draw_buffer40 = bitsquiggles.render_smooth40(
  parent, visual40, scale=4
)
```

The LVGL renderer retains no per-render resources. Raster operations return an
`(image, descriptor, pixel_buffer)` tuple. The descriptor points directly to
the returned RGB565 `bytearray` without copying it, so the caller must retain
all three values until the image is deleted.

Smooth operations return a `(wrapper, draw_buffer)` tuple. The caller must
retain both values while the wrapper is in use. To release smooth output,
delete the wrapper first, then call `draw_buffer.destroy()`. An application
widget can store the returned values as attributes and perform that cleanup in
its LVGL delete-event handler.

### 3.2 Fill-rectangle framebuffer renderer

The framebuffer renderer supports exact raster output:

```python
import bitsquiggle_renderer_framebuffer as bitsquiggles

def map_color(color):
    return 1 if color == "#ffffff" else 0

grid32 = bitsquiggles.pixels32(0x12345678, bitsquiggles.BLACK_AND_WHITE)
bitsquiggles.render_raster32(
  framebuffer,
  grid32,
  x=48,
  y=19,
  scale=2,
  color_mapper=map_color,
)

grid40 = bitsquiggles.pixels40(0x39527804DB, bitsquiggles.BLACK_AND_WHITE)
bitsquiggles.render_raster40(framebuffer, grid40, scale=2, color_mapper=map_color)
```

It paints each source pixel as a whole target pixel or integer-scaled square.
`color_mapper` adapts canonical `#rrggbb` colors to one-bit, indexed, RGB565,
or other native values. Smooth output is not provided by this renderer.

### 3.3 PyQt6 renderer

The PyQt6 renderer supports exact raster and smooth output as `QPixmap` values:

```python
import bitsquiggle_renderer_pyqt6 as bitsquiggles

visual32 = bitsquiggles.spec32(0x12345678, bitsquiggles.HIGH_CONTRAST)
smooth32 = bitsquiggles.render_smooth32(visual32, scale=4)

input40 = bitsquiggles.bip380_checksum_input("89f8spxm")
grid40 = bitsquiggles.pixels40(input40, bitsquiggles.BLACK_AND_WHITE)
raster40 = bitsquiggles.render_raster40(grid40, scale=2)
```

The smooth operations draw the canonical blob union with antialiasing. The
raster operations preserve every exact source pixel as an integer-scaled
rectangle without antialiasing.

## 4. Exposed API

Shared API semantics are defined in the [API contract](../spec/06-api.md). The
[presentation chapter](../spec/03-presentation.md) owns shared style, color, and
polarity rules.

### 4.1 Integration entry points

| Import | Canonical output | Rendering operations |
| --- | --- | --- |
| `bitsquiggles_core` | `spec(width, ...)`, `pixels(width, ...)` | None; use for headless or custom-rendered integrations |
| `bitsquiggle_renderer_lvgl` | `spec32/40()`, `pixels32/40()` | `render_raster32/40()`, `render_smooth32/40()` |
| `bitsquiggle_renderer_framebuffer` | `spec32/40()`, `pixels32/40()` | `render_raster32/40()` |
| `bitsquiggle_renderer_pyqt6` | `spec32/40()`, `pixels32/40()` | `render_raster32/40()`, `render_smooth32/40()` |

The core and all renderer entry points expose the styles `STANDARD`,
`HIGH_CONTRAST`, `MONOCHROME`, and `BLACK_AND_WHITE`, plus
`bip380_checksum_input()` for converting exactly eight BIP380
descriptor-checksum characters to a 40-bit BitSquiggle input.

### 4.2 Compatibility API

Each renderer retains unsuffixed `spec()`, `pixels()`, `render_raster()`, and,
where available, `render_smooth()` as deprecated wrappers for its 32-bit
operations. New integrations must use width-suffixed names. The wrappers emit
`DeprecationWarning` where the runtime provides `warnings` and will be removed
in a future release.

The former `bitsquiggle32`, `bitsquiggle40`, `bitsquiggles`, plural renderer,
and 32-bit framebuffer shim module paths are no longer part of this port. Use
the imports in the table above.

Mixers, edge construction, mode matching, capacity inspection, and smooth-blob
extraction are core implementation and conformance details rather than
application-facing renderer operations.

## 5. Test conformance

The normative [conformance chapter](../spec/07-conformance.md) is the basis for
every suite below. It defines the required checks, vectors, generated-fixture
ownership, and additional implementation evidence.

First verify that the committed packed mode tables match their canonical
design-time definitions:

```bash
cd micropython
python3 generate_packed_tables.py --check
```

Run `python3 generate_packed_tables.py` without `--check` after changing mode
geometry. The generator builds the readable canonical definitions, packs them,
fully decodes and compares them before writing the generated block in
`bitsquiggles_core.py`. The generator is a development tool and is not included
in the installed package.

Run the dependency-free core and framebuffer suites under CPython or
MicroPython:

```bash
python3 test_bitsquiggles_core.py
python3 test_bitsquiggle_renderer_framebuffer.py
```

`test_bitsquiggles_core.py` covers both widths, including encoding properties,
presentation behavior, exact raster recovery, smooth blobs, input validation,
and every shared fixture vector. The framebuffer suite uses a recording
`fill_rect` target to verify complete deterministic draw-call output for both
widths, including scaling, offsets, color mapping, and validation.

Run the LVGL renderer suite under CPython:

```bash
python3 test_bitsquiggle_renderer_lvgl.py
```

It supplies a recording LVGL fake and checks the public surface, width
dispatch, zero-copy RGB565 descriptors, caller-owned resources, smooth-canvas
construction, validation, and deprecated wrappers. It does not execute LVGL
itself or prove display-driver rasterization; validate exact and smooth output
on the target LVGL runtime and display as well.

With PyQt6 installed, run its focused renderer tests with:

```bash
python3 test_bitsquiggle_renderer_pyqt6.py
```

This suite uses real PyQt6 rendering through the offscreen platform plugin and
checks exact raster pixels, smooth output, both widths, validation, and
deprecated wrappers.

## 6. Package / release notes

`pyproject.toml` packages `bitsquiggles_core` and the three renderer modules for
CPython while retaining the same source files for MicroPython vendoring.
Renderer framework imports remain optional, so core and framebuffer use stay
dependency-free and MicroPython-compatible.

## 7. Limitations and compatibility

Native MicroPython targets vary in available RAM; reduce long sampled test loops
on constrained devices if needed. See the [shared input contract](../spec/01-overview.md)
for value semantics.

| Surface | Verified target | Notes |
| --- | --- | --- |
| CPython package | Python 3.8+ | Declared in package metadata; CI uses Python 3.12. |
| CPython conformance | Python 3.12 in CI | Consumes every shared fixture vector. |
| PyQt6 renderer | PyQt6 6.5+ | Real exact and smooth `QPixmap` output tested with the offscreen platform plugin. |
| MicroPython integration | Targets with `math` support | Vendor the core and one renderer module. |
| LVGL renderer | LVGL 9 MicroPython targets | CPython fake covers integration behavior; validate exact and smooth output on the target display. |
| Framebuffer renderer | `fill_rect` targets | Recording fake covers draw calls; validate target color mapping and display output. |

## 8. License

Grug 2-Clause License. See the repository-level [LICENSE](../LICENSE).
