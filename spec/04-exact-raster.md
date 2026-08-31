# BitSquiggles exact raster

**Normative.** This chapter defines the lossless binary output format. It
depends on the canonical connection mask from [encoding](02-encoding.md).

## Exact binary renderer

The normative raster dimensions are:

```text
width  = 1 border + COLUMNS × 2 cell pixels + (COLUMNS - 1) × 1 gaps + 1 border
height = 1 border + 7 × 2 cell pixels + 6 × 1 gaps + 1 border = 22
```

This gives a 16×22 BitSquiggle32 raster and a 22×22 BitSquiggle40 raster.

Cell `(row,column)` begins at:

```text
x = 1 + 3 × column
y = 1 + 3 × row
```

Row 0 is the top row and column 0 is the left column. Renderers must not rotate,
reflect, or transpose the canonical raster.

Rendering rules:

1. initialize every pixel as background;
2. fill each active cell's 2×2 block with foreground;
3. for a selected horizontal edge, fill the 1×2 gap between its cells with foreground;
4. for a selected vertical edge, fill the 2×1 gap between its cells with foreground;
5. set the one-pixel junction between four cells (as foreground) only when all four
   perimeter edges of that cell square are selected;
6. leave every border pixel as background.

Each physical edge owns bridge pixels not owned by any other edge. Reading one
of those bridge pixels recovers that edge bit even when connected-component
membership alone would not reveal it.

## Related

- Prerequisite: [encoding](02-encoding.md)
- Smooth alternative: [smooth output](05-smooth-output.md)
- Required renderer behavior: [API contract](06-api.md)
- Required validation: [conformance](07-conformance.md)
