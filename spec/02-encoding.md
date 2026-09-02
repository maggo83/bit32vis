# BitSquiggles encoding

**Normative.** This chapter defines the complete unsigned-integer-to-connection-
mask transformations for BitSquiggle32 and BitSquiggle40, including their
uniqueness proof. Read [the overview](01-overview.md) first. Presentation rules
begin in [presentation](03-presentation.md).

## Fixed dimensions, values, and ordering

Input and mixer arithmetic is modulo $2^w$, where $w$ is the variant's input
width.

| Item | BitSquiggle32 | BitSquiggle40 |
| --- | ---: | ---: |
| Input width | 32 | 40 |
| Grid rows | 7 | 7 |
| Grid columns | 5 | 7 |
| Horizontal edges | 28 | 42 |
| Vertical edges | 30 | 42 |
| Total edges | 58 | 84 |
| Exact raster width | 16 | 22 |
| Exact raster height | 22 | 22 |

Rows are numbered from `0` through `ROWS - 1` and columns from `0` through
`COLUMNS - 1`. A canonical edge is the tuple:

```text
(startRow, startColumn, endRow, endColumn)
```

Only orthogonal nearest neighbours are valid:

```text
(row, column, row,     column + 1)  horizontal
(row, column, row + 1, column    )  vertical
```

Edges are ordered lexicographically by the tuple. Equivalently, iterate cells
in row-major order and emit the right edge before the down edge whenever that
edge exists. Connection arrays and bit strings use this order.

A cell is active if and only if at least one selected edge is incident to it.
Cells do not carry independent data bits.

## BIP380 checksum conversion

BitSquiggle40 provides a conversion helper for the eight characters following
the `#` separator in a BIP380 output descriptor. The checksum character set and
its index order are:

```text
qpzry9x8gf2tvdw0s3jn54khce6mua7l
```

The helper accepts exactly eight characters from this set. It does not accept
the `#` separator or any descriptor text. Let `index(character)` be the
zero-based position in the character set above. Starting with `value = 0`,
process the checksum from left to right:

```text
value = (value << 5) OR index(character)
```

After eight characters, return `value` as the unsigned 40-bit BitSquiggle40
input. Leading `q` characters contribute zero-valued high bits and must not be
discarded during length validation. Reject every input whose length is not
exactly eight characters or which contains a character outside the set.

This helper converts an already-present checksum representation. It does not
calculate a checksum and cannot verify one without the associated descriptor.

## Bijective mixers

### BitSquiggle32 mixer

Given `input`, calculate modulo $2^{32}$:

```text
mixed = input + 0x9e3779b9
mixed = (mixed XOR (mixed >>> 16)) × 0x85ebca6b
mixed = (mixed XOR (mixed >>> 13)) × 0xc2b2ae35
mixed =  mixed XOR (mixed >>> 16)
```

`>>>` is a logical right shift. Mask intermediate results to 32 bits where the
implementation language does not overflow naturally.

Both multipliers are odd, addition is reversible, and each XOR-shift is
invertible. The complete mixer is therefore a permutation of the 32-bit domain.

### BitSquiggle40 mixer

Given `input`, calculate modulo $2^{40}$:

```text
mixed = input + 0xb97f4a7c15
mixed = (mixed XOR (mixed >>> 20)) × 0xd7ed558ccd
mixed = (mixed XOR (mixed >>> 15)) × 0xfe1a85ec53
mixed =  mixed XOR (mixed >>> 20)
```

Both multipliers are odd, addition is reversible, and each XOR-shift is
invertible. The complete mixer is therefore a permutation of the 40-bit domain.
Mask intermediate results to the variant width where the implementation
language does not overflow at that width naturally.

## Connection classes and copy templates

Each BitSquiggle32 template labels the 7×5 physical cells. An unprimed label
identifies a source cell; a primed label is a copy of that source.

For every physical edge:

1. remove primes from both endpoint labels;
2. locate the unprimed source coordinate for each endpoint;
3. sort the two source coordinates lexicographically;
4. use the coordinate pair as the connection-class key.

Sort connection classes by that key. All physical edges with one key receive
the same free connection bit.

### Left/right (`A|`)

```text
A B C B' A'
D E F E' D'
G H I H' G'
J K L K' J'
M N O N' M'
P Q R Q' P'
S T U T' S'
```

Free connection classes: **32**.

### Top/bottom (`A-`)

```text
A  B  C  D  E
F  G  H  I  J
K  L  M  N  O
P  Q  R  S  T
K' L' M' N' O'
F' G' H' I' J'
A' B' C' D' E'
```

Free connection classes: **31**.

### Half-turn (`A+`)

```text
A  B  C  D  E
F  G  H  I  J
K  L  M  N  O
P  Q  R  Q' P'
O' N' M' L' K'
J' I' H' G' F'
E' D' C' B' A'
```

Free connection classes: **29**.

### Slash copy (`A/`)

```text
A  B  C  D  E
F  G  H  I  J
K  L  M  N  I'
O  P  Q  M' H'
R  S  P' L' G'
T  R' O' K' F'
E' D' C' B' A'
```

Free connection classes: **33**. The final row extends the copied diagonal
sequence and mirrors the top row in reverse. This remains a copy template, not
a geometric reflection of the non-square rectangle.

### BitSquiggle40 geometric templates

BitSquiggle40 defines each template by an involution on a cell coordinate
`(row, column)`. For each physical edge, transform both endpoints and restore
canonical endpoint order. The connection-class key is the lexicographically
smaller of the original canonical edge and the transformed canonical edge.
Sort connection classes by that key.

| Mode | Cell transform | Free classes |
| --- | --- | ---: |
| Left/right (`A\|`) | `(row, 6 - column)` | 45 |
| Top/bottom (`A-`) | `(6 - row, column)` | 45 |
| Half-turn (`A+`) | `(6 - row, 6 - column)` | 42 |
| Slash reflection (`A/`) | `(6 - column, 6 - row)` | 42 |

The slash transform is reflection across the geometric diagonal running from
the lower-left cell to the upper-right cell.

### BitSquiggle40 orientation marker and usable classes

BitSquiggle40 reserves the four edges incident to the center cell `(3,3)`:

```text
up    = (2,3,3,3)
left  = (3,2,3,3)
right = (3,3,3,4)
down  = (3,3,4,3)
```

The `up` edge is selected in every final mask. The `left`, `right`, and `down`
edges are clear. These four values are the orientation marker and do not carry
input bits.

For each mode, exclude every connection class containing at least one reserved
edge from data assignment. Set every physical edge in an excluded class clear
before applying the orientation marker. Sort the remaining usable classes by
the same connection-class key as the complete family.

| Mode | Complete classes | Excluded classes | Usable classes |
| --- | ---: | ---: | ---: |
| Left/right (`A\|`) | 45 | 3 | 42 |
| Top/bottom (`A-`) | 45 | 3 | 42 |
| Half-turn (`A+`) | 42 | 2 | 40 |
| Slash reflection (`A/`) | 42 | 2 | 40 |

The orientation marker is applied only after candidate construction,
canonical overlap handling, and fallback selection. Family membership and
canonical overlap tests operate on the data mask before the marker is applied.

## Preferred modes and class-bit assignment

### BitSquiggle32 assignment

Bits `31…30` of `mixed` select one of four preferred modes:

| Index | Mode | Free classes |
| ---: | --- | ---: |
| 0 | Left/right (`A\|`) | 32 |
| 1 | Top/bottom (`A-`) | 31 |
| 2 | Half-turn (`A+`) | 29 |
| 3 | Slash copy (`A/`) | 33 |

Bits `29…0` are the 30-bit payload. Assign them most significant first to the
first 30 sorted connection classes. If a template has further classes, wrap
around and reuse payload bits from the beginning:

```text
classBit[i] = payloadBit[29 - (i modulo 30)]
```

Thus top/bottom class 30 repeats class 0. Slash-copy classes 30…32 repeat
classes 0…2. This extends visible relationships between the template's upper
and lower regions without deriving any class value from a secondary
pseudo-random source.

The half-turn family has only 29 classes and cannot injectively represent all
$2^{30}$ payloads. If payload bit 0 is zero, assign payload bits `29…1` most
significant first to its 29 classes. If payload bit 0 is one, do not accept the
candidate: encode the complete mixed value in the default family and set
`fallback` to true. The omitted bit is therefore represented by the choice
between an eligible half-turn candidate and fallback rather than being lost.

Mode 0 is different: assign all 32 bits of `mixed`, most significant first,
directly to the 32 left/right classes. This is a bijection onto the complete
default family and is also the fallback encoding.

### BitSquiggle40 assignment

Bits `39…38` of `mixed` select the preferred mode in the same mode order.
Bits `37…0` are the 38-bit payload.

For preferred modes 1 through 3, assign payload bits most significant first and
repeat from the beginning for remaining classes:

```text
classBit[i] = payloadBit[37 - (i modulo 38)]
```

Every non-default family has at least 38 usable classes, so every payload is
represented injectively. No capacity fallback is needed.

For preferred mode 0 and every fallback, assign all 40 bits of `mixed` most
significant first and repeat from the beginning for any remaining usable classes:

```text
classBit[i] = mixedBit[39 - (i modulo 40)]
```

The first 40 usable classes preserve the complete mixed value injectively. The
two repeated left/right classes are deterministic geometry and carry no
additional information. The half-turn and slash-reflection families have 40
usable classes, two more than their 38-bit payloads.

## Family membership, canonical priority, and fallback

Mode families overlap. A family label cannot disambiguate them because it is
not part of the visible geometry. Apply this priority rule to every candidate
with preferred mode `i > 0`:

1. expand its classes into the variant's canonical edge mask;
2. test that mask against every earlier encoding family: the complete family
   for BitSquiggle32 or the restricted data family for BitSquiggle40;
3. accept it only when no earlier family matches;
4. otherwise encode the complete `mixed` value using the variant's mode-0
   assignment and set `fallback` to true.

An accepted candidate has `actualMode = preferredMode` and `fallback = false`.
A rejected candidate has `actualMode = A|` and `fallback = true`. Preferred
mode 0 directly uses `A|` and is not considered fallback.

BitSquiggle32 applies its capacity fallback in addition to this overlap rule.
BitSquiggle40 has no capacity fallback.

### Exact membership test

For a mode, let:

- `class(e)` identify the connection class of physical edge `e`;

Expansion is:

```text
edge[e] = classBit[class(e)]
```

For BitSquiggle32, the mask belongs to that complete family if and only if all
occurrences in each connection class are equal. For a BitSquiggle40 data mask,
every edge in an excluded class must be clear and all occurrences in each usable
class must be equal. One representative occurrence can be compared with all
remaining occurrences. Singleton classes impose no constraint. The test is
exact and linear in the variant's physical edge count.

It is necessary to test all earlier families, not only an adjacent or
higher-index family: the earliest family owns every overlap in which it
participates.

### Uniqueness proof

Let `M` be the variant's bijective mixer and `Fi` its encoding family for mode
`i`: the complete mask family for BitSquiggle32 or the restricted data-mask
family for BitSquiggle40.

1. Mode-0 and fallback outputs encode every mixed bit injectively in `F0`.
2. Every accepted non-default output is explicitly outside `F0`, so it cannot
   equal a mode-0 or fallback output.
3. Within every non-default family with sufficient capacity, different inputs
   selecting that mode have different payloads, all of which are present in the
   first payload-width classes.
4. BitSquiggle32 half-turn candidates use the capacity rule above: accepted
   candidates preserve bits `29…1`, while candidates with payload bit 0 equal
   to one use the injective default fallback.
5. For accepted modes `i < j`, every mode-`i` output belongs to `Fi`, while
   mode `j` rejects every candidate belonging to `Fi`. Their accepted outputs
   cannot coincide.

Because `M` is bijective, these cases establish:

```text
input1 != input2  =>  edgeMask1 != edgeMask2
```

The exact raster is also injective because [the exact raster](04-exact-raster.md)
assigns dedicated bridge pixels from which every edge bit can be recovered.
Color and metadata are not used in either argument.

For BitSquiggle40, let `R` rotate an edge mask clockwise by 90 degrees. Every
valid final mask has marker values `(up,left,right,down) = (1,0,0,0)`. For
`k` equal to 1, 2, or 3, `R^k` moves the selected marker edge to another
reserved position, so the rotated mask cannot equal any valid final mask.
Together with fixed-orientation injectivity, this establishes:

```text
input1 != input2  =>  edgeMask1 != rotate(edgeMask2, k × 90°)
```

for `k` equal to 0, 1, 2, or 3.

For symbolic analysis, each encoding family may equivalently be represented as
the affine binary map:

```text
edgeMask = Ai × classBits XOR bi
```

Intersections can then be counted by solving
`Ai × x XOR Aj × y = bi XOR bj` over `GF(2)`. Runtime encoding does not need
those counts; concrete membership tests are sufficient.

## Related

- Prerequisite: [overview](01-overview.md)
- Next: [presentation](03-presentation.md)
- Exact rendering: [exact raster](04-exact-raster.md)
- Public operations and helpers: [API contract](06-api.md)
- Required checks: [conformance](07-conformance.md)
