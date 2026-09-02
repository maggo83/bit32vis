# BitSquiggles API and implementation contract

**Normative.** This chapter distinguishes the capabilities required to verify
a conforming implementation from the smaller public interface required by an
application. Shared semantics depend on [encoding](02-encoding.md),
[presentation](03-presentation.md), and the output chapters. Each port guide
owns target-language signatures, containers, validation, ownership, and
renderer details.

## Conformance implementation contract

Every port must implement the capabilities in this section for each supported
width. They must be reachable by the port's conformance harness, but they do
not have to be public application APIs. A port may keep all of them internal or
make some of them public when that is simpler and does not add duplicated,
complicated, or bloated code.

The operation names below are descriptive rather than mandatory public names.
Where the target language permits it efficiently, shared core operations
should accept the width as a parameter or variant descriptor instead of
duplicating the width in function names. Width-specific internal functions are
appropriate when required by numeric semantics, toolchain constraints, or a
measured size or runtime benefit.

### Required capabilities

| Capability | Result |
| --- | --- |
| `mix(width, input)` | bijective mixed value at the selected width |
| `edges(width)` | 58 or 84 canonical edges in [encoding order](02-encoding.md#fixed-dimensions-values-and-ordering) |
| `freeConnectionCount(width, mode)` | independent connection-class count |
| `usableConnectionCount(width, mode)` | data-bearing connection-class count |
| `matchesMode(width, connections, mode)` | complete-family membership |
| `spec(width, input[, style])` | canonical visual specification |
| `pixels(width, input[, style])` | exact pixel grid and its colors |
| `smoothBlobs(width, connections)` | ordered canonical smooth blobs |
| `bip380ChecksumInput(checksum)` | BitSquiggle40 input from exactly eight BIP380 checksum characters |

The implementation must also provide the selected variant's rows, columns,
edge count, pixel width, and pixel height; the four styles Standard, High
contrast, Monochrome, and Black and white; and mode labels `A|`, `A-`, `A+`,
and `A/`.

`spec()` is pure: it derives the mixed input, connections, active cells,
colors, style, preferred and actual modes, fallback state, luminance index,
and polarity metadata. `pixels()` is also pure and derives the variant's exact
binary raster and colors from the same input and style. `smoothBlobs()` is pure
and returns the presentation-only ordered decomposition from
[smooth output](05-smooth-output.md#canonical-blob-extraction). None of these
operations draws anything.

The BIP380 helper has the validation and conversion behavior defined in
[BIP380 checksum conversion](02-encoding.md#bip380-checksum-conversion). It
does not accept a complete descriptor and does not verify or derive its
checksum. It is required only for implementations that support BitSquiggle40.

## Application renderer contract

An application uses a selected renderer as its only BitSquiggles import or
include. The renderer may depend on an internal or separately compiled core,
but an integrator must not need another BitSquiggles import or include to name
styles and result types or to perform the supported workflow. A renderer may
provide those types by re-export, inheritance, an included declaration, or
another efficient mechanism conventional for the target language; it should
not duplicate model definitions solely to satisfy this rule.

### Required public surface

A renderer that supports both widths exposes these width-explicit operations:

| Operation | Requirement | Input | Result or constraint |
| --- | --- | --- | --- |
| `spec32`, `spec40` | Required | Identity and optional style | Return the canonical visual specification. |
| `pixels32`, `pixels40` | Required | Identity and optional style | Return the exact pixel grid and its colors. |
| `renderRaster32`, `renderRaster40` | Required when exact rendering is supported | Canonical pixel grid | Paint every grid element as an exact whole target pixel or integer-scaled square. It must not accept an identity input. |
| `renderSmooth32`, `renderSmooth40` | Required when smooth rendering is supported | Canonical visual specification | Render according to [smooth output](05-smooth-output.md); antialiasing must not close an unselected connection. |
| `bip380ChecksumInput` | Required for BitSquiggle40 | Eight BIP380 checksum characters | Return the corresponding 40-bit input. |

Use the target language's conventional spelling while preserving the `32` and
`40` suffixes. The BIP380 helper is deliberately unsuffixed because its input
domain already identifies it as BitSquiggle40-only. A renderer for only one
width exposes the applicable suffixed operations.

Every renderer must provide at least exact or smooth rendering for each width
it supports. Diagnostic operations such as mixers, edge enumeration, family
matching, capacities, dimensions, and smooth-blob extraction are not part of
the required application surface. They may remain internal or be available
through a separately public core when that is useful and inexpensive.

The renderer entry point owns identity adaptation, canonical output, and
rendering. This does not change the requirement that `renderRaster32/40`
consume a canonical pixel grid and `renderSmooth32/40` consume a canonical
visual specification rather than accepting identity inputs directly.

Each port guide owns exact exported signatures, container types, input
validation, ownership, and target-specific helpers.

## Related

- Core transformation: [encoding](02-encoding.md)
- Exact output: [exact raster](04-exact-raster.md)
- Smooth output: [smooth output](05-smooth-output.md)
- Required validation: [conformance](07-conformance.md)
