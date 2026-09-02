// Dependency-free Canvas 2D application facade for both BitSquiggles variants.
import {
  EDGE_COUNT32,
  EDGE_COUNT40,
  PIXEL_HEIGHT32,
  PIXEL_HEIGHT40,
  PIXEL_WIDTH32,
  PIXEL_WIDTH40,
  smoothBlobs,
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
} from "./bitsquiggles-core.js";

function contextFor(canvas) {
  if (
    canvas == null ||
    !Number.isInteger(canvas.width) ||
    !Number.isInteger(canvas.height) ||
    canvas.width <= 0 ||
    canvas.height <= 0 ||
    typeof canvas.getContext !== "function"
  )
    throw new TypeError("canvas must be a positive-size Canvas element");
  const context = canvas.getContext("2d");
  if (context == null) throw new TypeError("canvas must provide a 2D context");
  return context;
}

function requireColor(value, name) {
  if (typeof value !== "string") throw new TypeError(`${name} must be a color`);
}

function renderRaster(canvas, grid, expectedWidth, expectedHeight) {
  if (
    grid == null ||
    grid.width !== expectedWidth ||
    grid.height !== expectedHeight ||
    !(grid.pixels instanceof Uint8Array) ||
    grid.pixels.length !== expectedWidth * expectedHeight
  )
    throw new RangeError(
      `grid must contain a ${expectedWidth} by ${expectedHeight} Uint8Array`,
    );
  for (const pixel of grid.pixels) {
    if (pixel !== 0 && pixel !== 1)
      throw new RangeError("grid pixels must contain only zeroes and ones");
  }
  requireColor(grid.background, "grid background");
  requireColor(grid.foreground, "grid foreground");
  const context = contextFor(canvas);
  const scaleX = canvas.width / expectedWidth;
  const scaleY = canvas.height / expectedHeight;
  if (scaleX !== scaleY || !Number.isInteger(scaleX))
    throw new RangeError("exact canvas dimensions must use one integer scale");

  context.fillStyle = grid.background;
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = grid.foreground;
  grid.pixels.forEach((pixel, index) => {
    if (pixel)
      context.fillRect(
        (index % expectedWidth) * scaleX,
        Math.floor(index / expectedWidth) * scaleX,
        scaleX,
        scaleX,
      );
  });
}

function renderSmooth(
  canvas,
  visual,
  width,
  edgeCount,
  pixelWidth,
  pixelHeight,
) {
  if (
    visual == null ||
    !(visual.connections instanceof Uint8Array) ||
    visual.connections.length !== edgeCount
  )
    throw new RangeError(
      `visual must contain a ${edgeCount}-entry connection Uint8Array`,
    );
  const blobs = smoothBlobs(width, visual.connections);
  requireColor(visual.background, "visual background");
  requireColor(visual.foreground, "visual foreground");
  const context = contextFor(canvas);
  if (typeof context.roundRect !== "function")
    throw new TypeError("Canvas 2D context must provide roundRect");
  const scaleX = canvas.width / pixelWidth;
  const scaleY = canvas.height / pixelHeight;
  if (!Number.isFinite(scaleX) || scaleX <= 0 || scaleX !== scaleY)
    throw new RangeError("smooth canvas dimensions must preserve raster aspect ratio");

  context.clearRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = visual.background;
  context.beginPath();
  context.roundRect(0, 0, canvas.width, canvas.height, 2 * scaleX);
  context.fill();

  context.fillStyle = visual.foreground;
  context.beginPath();
  for (const blob of blobs) {
    context.roundRect(
      scaleX + blob.leftColumn * 3 * scaleX,
      scaleX + blob.topRow * 3 * scaleX,
      2 * scaleX + (blob.rightColumn - blob.leftColumn) * 3 * scaleX,
      2 * scaleX + (blob.bottomRow - blob.topRow) * 3 * scaleX,
      scaleX,
    );
  }
  context.fill();
}

export function renderRaster32(canvas, grid) {
  renderRaster(canvas, grid, PIXEL_WIDTH32, PIXEL_HEIGHT32);
}

export function renderRaster40(canvas, grid) {
  renderRaster(canvas, grid, PIXEL_WIDTH40, PIXEL_HEIGHT40);
}

export function renderSmooth32(canvas, visual) {
  renderSmooth(
    canvas,
    visual,
    32,
    EDGE_COUNT32,
    PIXEL_WIDTH32,
    PIXEL_HEIGHT32,
  );
}

export function renderSmooth40(canvas, visual) {
  renderSmooth(
    canvas,
    visual,
    40,
    EDGE_COUNT40,
    PIXEL_WIDTH40,
    PIXEL_HEIGHT40,
  );
}
