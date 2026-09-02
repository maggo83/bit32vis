import type {
  PixelGrid32,
  PixelGrid40,
  VisSpec32,
  VisSpec40,
} from "./bitsquiggles-core.js";

export {
  BLACK_AND_WHITE,
  HIGH_CONTRAST,
  MONOCHROME,
  MODES,
  STANDARD,
  STYLES,
  bip380ChecksumInput,
  formatHex32,
  formatHex40,
  parseHex32,
  parseHex40,
  pixels32,
  pixels40,
  spec32,
  spec40,
  type Mode,
  type PixelGrid,
  type PixelGrid32,
  type PixelGrid40,
  type Style,
  type VisSpec,
  type VisSpec32,
  type VisSpec40,
} from "./bitsquiggles-core.js";

export function renderRaster32(
  canvas: HTMLCanvasElement,
  grid: PixelGrid32,
): void;
export function renderRaster40(
  canvas: HTMLCanvasElement,
  grid: PixelGrid40,
): void;
export function renderSmooth32(
  canvas: HTMLCanvasElement,
  visual: VisSpec32,
): void;
export function renderSmooth40(
  canvas: HTMLCanvasElement,
  visual: VisSpec40,
): void;