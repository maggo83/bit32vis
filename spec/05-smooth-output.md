# BitSquiggles smooth output

**Normative.** This chapter defines the presentation-only smooth output that
preserves the canonical connection mask. Read [encoding](02-encoding.md)
first. Exact conformance output remains [the exact raster](04-exact-raster.md).

## Smooth renderer

Smooth rendering is presentation rather than the binary conformance format,
but it must preserve selected and unselected connections. The direct procedure
below is a simple baseline that always works: it draws every active cell,
selected bridge, and required junction. It is useful for minimal renderers.

The intended normal path is [canonical smooth blobs](#canonical-smooth-blobs):
core preprocessing produces fewer rounded rectangles with the same smooth
foreground union, and renderers draw those rectangles. Every core provides the
blob helper; renderers should use it unless the direct baseline is specifically
needed.

The direct procedure uses the variant's exact-raster coordinate system at any
scale:

1. draw the complete background tile with rounded outer corners;
2. represent every active 2×2 cell with corner radii equal to half the cell
   width, giving terminal cells semicircular caps;
3. overlay rectangular bridges for selected horizontal and vertical edges;
4. fill a four-cell center junction only when all four perimeter edges are
   selected;
5. combine all foreground cells, bridges, and junctions into one geometric
   union before antialiased rasterization.

The union avoids hairline background seams between overlapping components.
Smoothing must not close an unselected connection.

## Canonical smooth blobs

Canonical smooth blobs combine fully connected cells into fewer rounded
rectangles. Their purpose is to reduce foreground polygons submitted by a
smooth renderer while preserving the selected/unselected connection topology
and intended smooth union. Canonical output gives every port the same compact
geometry. Every core exposes this helper. It preserves the identity-bearing
connection mask, active cells, and exact raster.

Write a blob as $B=[r_0,r_1]×[c_0,c_1]$, with
$0\,\leq\,r_0\,\leq\,r_1<ROWS$ and
$0\,\leq\,c_0\,\leq\,c_1<COLUMNS$. It is valid when it has at least one
internal edge, every cell in $B$ is active, and every internal grid adjacency
of $B$ is selected.

Let $F=E\cup J$, where $E$ is the selected-edge set and $J$ is the set of
2×2 cell squares with all four perimeter edges selected (*required junctions*).
$B$ covers its internal edges and junctions in $J$. Ordered blobs cover $F$;
they may overlap because coverage is by features, not cells.

For a blob `(r0, c0, r1, c1)`, its unscaled smooth rectangle in the exact
raster coordinate system is:

```text
x = 1 + 3 × c0
y = 1 + 3 × r0
w = 2 + 3 × (c1 - c0)
h = 2 + 3 × (r1 - r0)
```

At scale `s`, multiply `x`, `y`, `w`, and `h` by `s` and use a corner radius
of `s`, half the physical cell width. Renderers must union blobs geometrically
or overlap their foreground drawing sufficiently before antialiasing; drawing
separate edge-to-edge antialiased rectangles can create seams.

## Canonical blob extraction

Extraction favors deterministic, portable geometry with a small bounded working
set suitable for embedded targets. It greedily enumerates candidates on demand,
retaining only feature masks, the current best candidate, and the blob output.

1. Derive the required edge set from the selected canonical connections.
2. Derive required junctions from every 2×2 square in row-major order.
3. Select the first uncovered feature—edges in canonical order, then junctions
   in row-major order—and use its endpoint rectangle or 2×2 square as anchor.
4. Consider each rectangle containing the anchor; reject any rectangle that
   fails the [canonical smooth blobs](#canonical-smooth-blobs) conditions.
5. Choose the candidate with this tie-break order:

   1. newly covered required junction count, descending;
   2. newly covered required edge count, descending;
   3. cell area, ascending;
   4. `topRow`, `leftColumn`, `bottomRow`, `rightColumn`, each ascending.

6. Append it, mark its features covered, and repeat.

An uncovered edge has a valid 1×2 or 2×1 candidate; an uncovered junction has
a valid 2×2 candidate. These cases guarantee progress through the required
features.

Implementations can short-circuit at a missing internal edge and prune
supersets retaining that edge when the tie-break winner remains unchanged. The
fixed grids cap BitSquiggle32 output at 82 blobs (58 edges plus 24 junctions)
and BitSquiggle40 output at 120 blobs (84 edges plus 36 junctions).

## Related

- Prerequisite: [encoding](02-encoding.md)
- Exact alternative: [exact raster](04-exact-raster.md)
- Renderer requirements: [API contract](06-api.md)
- Required checks: [conformance](07-conformance.md)
