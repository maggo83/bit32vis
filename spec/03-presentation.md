# BitSquiggles presentation

**Normative.** This chapter defines cells, rendering styles, and color
conversion after the final connection mask is known. It depends on
[encoding](02-encoding.md); exact geometry is in [exact raster](04-exact-raster.md).

## Cell and color derivation

After the final connection mask is known, mark both endpoints of every selected
edge as active. All other cells are inactive.

For both variants, extract color indices from `mixed`:

```text
hueIndex       = bits 15…12
chromaIndex    = bits 11…8
luminanceIndex = bits 1…0
```

Calculate:

```text
hue    = hueIndex × (360 / 16)
chroma = 0.05 + chromaIndex × ((0.25 - 0.05) / 15)
baseL  = 0.50 + luminanceIndex × ((0.70 - 0.50) / 3)
```

Derive ordered OKLCH components by style:

| Style | Foreground L | Background L | Foreground C | Background C |
| --- | --- | --- | --- | --- |
| Standard | `baseL` | `foregroundL - 0.50` | `chroma` | `chroma` |
| High contrast | `baseL + 0.30` | `foregroundL - 0.80` | `chroma + 0.10` | `chroma + 0.10` |
| Monochrome | `baseL + 0.30` | `foregroundL - 0.80` | `0` | `0` |
| Black and White | `1` | `0` | `0` | `0` |

Clamp both lightness values to `[0,1]`. Foreground hue is `hue`; background
hue is `(hue + 180) modulo 360`.

Calculate the XOR parity of all 32 or 40 bits in the original, unmixed input. If the
parity is odd, swap the foreground and background lightness values. Do not swap
hue or chroma. The returned `swapped` field reports this operation.

## OKLCH to sRGB

For `(L,C,h)`, with `h` in degrees:

```text
a  = C × cos(h × pi / 180)
b  = C × sin(h × pi / 180)
l_ = L + 0.3963377774 × a + 0.2158037573 × b
m_ = L - 0.1055613458 × a - 0.0638541728 × b
s_ = L - 0.0894841775 × a - 1.2914855480 × b

rLinear =  4.0767416621 × l_^3 - 3.3077115913 × m_^3 + 0.2309699292 × s_^3
gLinear = -1.2684380046 × l_^3 + 2.6097574011 × m_^3 - 0.3413193965 × s_^3
bLinear = -0.0041960863 × l_^3 - 0.7034186147 × m_^3 + 1.7076147010 × s_^3
```

Clamp each linear component to `[0,1]`, then encode it with:

```text
srgb(v) = 12.92 × v                            when v <= 0.0031308
          1.055 × v^(1 / 2.4) - 0.055         otherwise
```

Multiply by 255 and round to the nearest integer, rounding exact half values
upward. Text colors use lowercase `#rrggbb`.

## Related

- Prerequisite: [encoding](02-encoding.md)
- Next: [exact raster](04-exact-raster.md)
- Public output operations: [API contract](06-api.md)
