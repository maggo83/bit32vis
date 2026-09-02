import {
  BLACK_AND_WHITE,
  bip380ChecksumInput,
  pixels32,
  pixels40,
  spec32,
  spec40,
  type PixelGrid32,
  type PixelGrid40,
  type Style,
  type VisSpec32,
  type VisSpec40,
} from "bitsquiggles";
import {
  renderRaster32,
  renderRaster40,
  renderSmooth32,
  renderSmooth40,
} from "bitsquiggles/renderer-canvas";

declare const canvas: HTMLCanvasElement;

const style: Style = BLACK_AND_WHITE;
const visual32: VisSpec32 = spec32(0x89abcdef, style);
const visual40: VisSpec40 = spec40(bip380ChecksumInput("89f8spxm"), style);
const grid32: PixelGrid32 = pixels32(0x89abcdef, style);
const grid40: PixelGrid40 = pixels40(0x39527804dbn, style);

renderRaster32(canvas, grid32);
renderRaster40(canvas, grid40);
renderSmooth32(canvas, visual32);
renderSmooth40(canvas, visual40);

// @ts-expect-error BitSquiggle40 identities require bigint.
spec40(1);
// @ts-expect-error BitSquiggle32 identities require number.
spec32(1n);
// @ts-expect-error Renderers consume canonical output, not identities.
renderRaster32(canvas, 1);
// @ts-expect-error Raster renderers require a grid of the matching width.
renderRaster32(canvas, grid40);