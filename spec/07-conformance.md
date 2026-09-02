# BitSquiggles conformance

**Normative.** This chapter defines required behavior checks and the base
conformance vectors. Read [the API contract](06-api.md) and its linked output
chapters for the public and rendering requirements.

## Conformance requirements

A conforming implementation must satisfy the row for its variant:

| Requirement | BitSquiggle32 | BitSquiggle40 |
| --- | ---: | ---: |
| Canonical edges | 58 | 84 |
| Complete classes in mode order | `32, 31, 29, 33` | `45, 45, 42, 42` |
| Usable data classes in mode order | `32, 31, 29, 33` | `42, 42, 40, 40` |
| Selector bits | `31…30` | `39…38` |
| Payload bits | `29…0` | `37…0` |
| Exact raster | 16×22 | 22×22 |

Every conforming implementation must also satisfy all of the following:

- produce canonical edges in the [specified order](02-encoding.md#fixed-dimensions-values-and-ordering);
- implement its bijective mixer and cyclic bit reuse exactly;
- implement BitSquiggle32 half-turn capacity fallback when implementing that variant;
- reserve the four BitSquiggle40 center edges and apply marker values
  `(up,left,right,down) = (1,0,0,0)` after fallback selection;
- derive no connection-class values from a secondary pseudo-random source;
- accept a non-default mask only when it belongs to no earlier family;
- encode all fallback data masks in the variant's default encoding family;
- preserve one connection mask across all rendering styles;
- derive cells only from incident selected edges;
- produce the variant's exact raster with a background border;
- permit recovery of every connection from its bridge pixels;
- for BitSquiggle40, reject equality between any valid mask and a 90-, 180-, or
  270-degree rotation of any valid mask;
- implement the BitSquiggle40 BIP380 conversion helper and its validation
  independently of descriptor parsing;
- match the applicable conformance vector below.

Every implementation must produce the ordered smooth-blob decomposition defined
in [canonical blob extraction](05-smooth-output.md#canonical-blob-extraction)
and preserve the exact-raster and connection-mask results.

Implementations should additionally test one-bit diffusion, observe every
preferred mode, exercise fallback with targeted inputs, compare large sampled
sets for duplicate monochrome masks, and test invalid public inputs. Sampling
is implementation evidence, not the proof of complete-domain uniqueness.

## BitSquiggle32 conformance vector

For input `0x89abcdef` in Standard style:

```text
mixed         = 0x47ac5876
preferredMode = A-
actualMode    = A-
fallback      = false
luminance     = 2
background    = #140040
foreground    = #8d9200
connections   = 0001111010110001011000011101010010101100001010011011010011
```

The connection string uses the canonical 58-edge order from
[encoding](02-encoding.md#fixed-dimensions-values-and-ordering).

## BitSquiggle40 conformance vector

For input `0x39527804db` in Standard style:

```text
mixed         = 0x34b1a077c8
preferredMode = A|
actualMode    = A|
fallback      = false
luminance     = 0
background    = #010001
foreground    = #007a41
connections   = 001101001101001011010000110110101110001000000000000011101111111011100101000101000000
```

The input is the left-to-right concatenation of the eight 5-bit Bech32
character-set indices for the BIP380 checksum `89f8spxm`. This note identifies
the source of the test value; protocol parsing remains outside the core API.
The connection string uses the canonical 84-edge order. Its center marker has
only the edge from `(3,3)` to `(2,3)` selected.

The BIP380 conversion helper must satisfy:

| Checksum | Result |
| --- | ---: |
| `qqqqqqqq` | `0x0000000000` |
| `89f8spxm` | `0x39527804db` |
| `llllllll` | `0xffffffffff` |

It must reject fewer or more than eight characters, any `#` separator, any
uppercase character, and every other character outside the BIP380 checksum
character set.

The following BitSquiggle40 inputs have a zero payload in their mixed value.
Their all-zero preferred candidates belong to earlier data families, so
each must use left/right overlap fallback:

| Input | Mixed | Preferred mode | Actual mode |
| --- | --- | --- | --- |
| `0xa7912def7b` | `0x4000000000` | `A-` | `A\|` fallback |
| `0x08a1a65b0c` | `0x8000000000` | `A+` | `A\|` fallback |
| `0x500181b841` | `0xc000000000` | `A/` | `A\|` fallback |

## Generated fixture ownership

[fixtures/v1-32.json](../fixtures/v1-32.json),
[fixtures/v1-40.json](../fixtures/v1-40.json), and the example assets in
[docs/examples/](../docs/examples/) are Java-generated tracked outputs. After
a relevant change, validate them with the Java generators using `--check` as
described in the [Java guide](../java/README.md#6-test-conformance). Target
README files own their target-specific test commands.

## Related

- Public operations: [API contract](06-api.md)
- Canonical algorithm: [encoding](02-encoding.md)
- Exact format: [exact raster](04-exact-raster.md)
- Smooth output: [smooth output](05-smooth-output.md)
