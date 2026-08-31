# BitSquiggles overview

**Normative.** This chapter defines the externally observable contract and the
coarse processing model. Read [the specification index](../SPEC.md) first;
read [encoding](02-encoding.md) for the exact transformation.

## Scope and contract

BitSquiggle32 and BitSquiggle40 map one unsigned integer of the named width to a
deterministic visual specification. The identity-bearing output is a binary mask
over the canonical connections of a fixed cell grid. An exact binary pixel
rendering preserves that mask without loss.

| Variant | Input | Grid | Connections | Exact raster |
| --- | ---: | ---: | ---: | ---: |
| BitSquiggle32 | 32 bits | 7×5 | 58 | 16×22 |
| BitSquiggle40 | 40 bits | 7×7 | 84 | 22×22 |

For conforming implementations:

```text
equal inputs   => equal connection masks and exact rasters
unequal inputs => unequal connection masks and exact rasters
```

The guarantee applies to the abstract connection mask and the exact binary
raster. It does not assert that every pair will be easy for a person to
distinguish after arbitrary scaling, smoothing, display degradation, or brief
observation. Raster orientation is fixed: row 0 is at the top and column 0 is
at the left. BitSquiggle40 additionally guarantees that rotating one final mask
by 90, 180, or 270 degrees cannot make it equal to a valid mask for a different
input.

The caller supplies the integer. BitSquiggles does not define how a Bitcoin
fingerprint, descriptor checksum, or any other protocol value is derived.
Hexadecimal notation is most-significant-digit first; `89abcdef` denotes the
BitSquiggle32 integer `0x89abcdef`, and `39527804db` denotes the BitSquiggle40
integer `0x39527804db`. The BitSquiggle40 API includes a narrow helper that
converts exactly eight BIP380 descriptor-checksum characters into this integer
form. It does not derive or verify the checksum. The encoder consumes an integer
and otherwise defines no byte order.

## Observable outputs

An abstract visual specification contains:

- the original and mixed values at the variant's input width;
- a canonical connection mask at the variant's edge count;
- the active state of each cell, derived from the mask;
- foreground and background colors;
- the requested rendering style;
- preferred-mode, actual-mode, fallback, luminance, and polarity metadata.

The exact renderer contains:

- a binary foreground/background raster at the variant's exact dimensions;
- the same foreground and background colors;
- the requested style.

The mode metadata is diagnostic. It is not rendered into the geometry and is
not required to decode or compare the identity-bearing mask.

## Coarse processing model

A conforming encoder performs these conceptual stages:

1. **Normalize the input.** Interpret it as an unsigned integer of the variant's width.
2. **Diffuse it.** Apply the variant's bijective mixer so nearby inputs normally
   affect many output features without creating collisions.
3. **Construct a candidate.** Use mixed bits to choose a copy family and fill
   its independent connection classes.
4. **Canonicalize overlaps.** Reject a non-default candidate that also belongs
   to an earlier family and encode the complete mixed value in the default
   family instead.
5. **Apply orientation.** For BitSquiggle40, add the fixed center orientation
   marker after candidate or fallback selection.
6. **Derive presentation.** Mark cells incident to selected connections and
   derive optional color cues. Geometry is identical in every style.
7. **Render if requested.** Place cells, bridges, and closed junctions in the
   exact binary raster.

The two-bit selector chooses one of four preferred families. The default family
has capacity for the complete mixed input and is also the fallback. Non-default
families preserve the remaining payload directly when they have sufficient
capacity. BitSquiggle32's 29-class half-turn family accepts only the half of its
30-bit payloads that fit and sends the others to fallback. Every BitSquiggle40
family has capacity for its 38-bit payload. Canonical priority makes accepted
family ranges disjoint.

## Related

- Next: [encoding](02-encoding.md)
- Output data and styles: [presentation](03-presentation.md)
- Exact display format: [exact raster](04-exact-raster.md)
