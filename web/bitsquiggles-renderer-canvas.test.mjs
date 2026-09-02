import assert from "node:assert/strict";
import * as renderer from "./bitsquiggles-renderer-canvas.js";

class FakeContext {
  constructor() {
    this.operations = [];
    this.fillStyle = "";
  }

  beginPath() {
    this.operations.push({ type: "beginPath" });
  }

  clearRect(...values) {
    this.operations.push({ type: "clearRect", values });
  }

  roundRect(...values) {
    this.operations.push({ type: "roundRect", values });
  }

  fill() {
    this.operations.push({ type: "fill", color: this.fillStyle });
  }

  fillRect(...values) {
    this.operations.push({
      type: "fillRect",
      color: this.fillStyle,
      values,
    });
  }
}

class FakeCanvas {
  constructor(width, height, context = new FakeContext()) {
    this.width = width;
    this.height = height;
    this.context = context;
    this.requestedContexts = [];
  }

  getContext(kind) {
    this.requestedContexts.push(kind);
    return this.context;
  }
}

for (const name of [
  "STANDARD",
  "HIGH_CONTRAST",
  "MONOCHROME",
  "BLACK_AND_WHITE",
  "STYLES",
  "spec32",
  "spec40",
  "pixels32",
  "pixels40",
  "renderRaster32",
  "renderRaster40",
  "renderSmooth32",
  "renderSmooth40",
  "parseHex32",
  "parseHex40",
  "formatHex32",
  "formatHex40",
  "bip380ChecksumInput",
])
  assert.equal(typeof renderer[name] === "undefined", false, `${name} export`);

for (const diagnostic of [
  "mix32",
  "mix40",
  "edges",
  "matchesMode",
  "smoothBlobs",
])
  assert.equal(diagnostic in renderer, false, `${diagnostic} stays in core`);

for (const obsolete of ["spec", "pixels", "renderRaster", "renderSmooth"])
  assert.equal(obsolete in renderer, false, `no unsuffixed ${obsolete}`);

const variants = [
  {
    input: 0x89abcdef,
    width: 16,
    height: 22,
    spec: renderer.spec32,
    pixels: renderer.pixels32,
    renderRaster: renderer.renderRaster32,
    renderSmooth: renderer.renderSmooth32,
  },
  {
    input: 0x39527804dbn,
    width: 22,
    height: 22,
    spec: renderer.spec40,
    pixels: renderer.pixels40,
    renderRaster: renderer.renderRaster40,
    renderSmooth: renderer.renderSmooth40,
  },
];

for (const variant of variants) {
  const grid = variant.pixels(variant.input);
  const originalPixels = grid.pixels.slice();
  const rasterCanvas = new FakeCanvas(variant.width * 3, variant.height * 3);
  variant.renderRaster(rasterCanvas, grid);
  assert.deepEqual(rasterCanvas.requestedContexts, ["2d"]);
  const rectangles = rasterCanvas.context.operations.filter(
    ({ type }) => type === "fillRect",
  );
  assert.equal(rectangles.length, 1 + grid.pixels.filter(Boolean).length);
  assert.deepEqual(rectangles[0].values, [
    0,
    0,
    variant.width * 3,
    variant.height * 3,
  ]);
  assert.equal(rectangles[0].color, grid.background);
  const expectedForegroundRectangles = [];
  grid.pixels.forEach((pixel, index) => {
    if (pixel)
      expectedForegroundRectangles.push([
        (index % grid.width) * 3,
        Math.floor(index / grid.width) * 3,
        3,
        3,
      ]);
  });
  assert.deepEqual(
    rectangles.slice(1).map(({ values }) => values),
    expectedForegroundRectangles,
    "every foreground pixel keeps its canonical coordinate",
  );
  for (const rectangle of rectangles.slice(1)) {
    assert.equal(rectangle.color, grid.foreground);
    assert.equal(rectangle.values[0] % 3, 0);
    assert.equal(rectangle.values[1] % 3, 0);
    assert.deepEqual(rectangle.values.slice(2), [3, 3]);
  }
  assert.deepEqual(grid.pixels, originalPixels, "raster input is not mutated");

  const visual = variant.spec(variant.input);
  const originalConnections = visual.connections.slice();
  const smoothCanvas = new FakeCanvas(
    variant.width * 10,
    variant.height * 10,
  );
  variant.renderSmooth(smoothCanvas, visual);
  assert.deepEqual(smoothCanvas.requestedContexts, ["2d"]);
  const operations = smoothCanvas.context.operations;
  assert.equal(
    operations.filter(({ type }) => type === "beginPath").length,
    2,
  );
  assert.equal(operations.filter(({ type }) => type === "fill").length, 2);
  const rounded = operations.filter(({ type }) => type === "roundRect");
  assert.ok(rounded.length > 1, "background and foreground geometry drawn");
  assert.deepEqual(rounded[0].values, [
    0,
    0,
    variant.width * 10,
    variant.height * 10,
    20,
  ]);
  for (const rectangle of rounded.slice(1)) {
    const [x, y, width, height, radius] = rectangle.values;
    assert.ok(x >= 10 && y >= 10);
    assert.ok(x + width <= smoothCanvas.width - 10);
    assert.ok(y + height <= smoothCanvas.height - 10);
    assert.equal(radius, 10);
  }
  assert.deepEqual(
    visual.connections,
    originalConnections,
    "smooth input is not mutated",
  );
}

assert.throws(
  () => renderer.renderRaster32(new FakeCanvas(17, 22), renderer.pixels32(0)),
  RangeError,
);
assert.throws(
  () =>
    renderer.renderSmooth40(
      new FakeCanvas(220, 210),
      renderer.spec40(0n),
    ),
  RangeError,
);
assert.throws(
  () => renderer.renderRaster32(new FakeCanvas(16, 22), renderer.pixels40(0n)),
  RangeError,
);
assert.throws(
  () => renderer.renderSmooth40(new FakeCanvas(22, 22), renderer.spec32(0)),
  RangeError,
);
assert.throws(
  () => renderer.renderRaster32({ width: 16, height: 22 }, renderer.pixels32(0)),
  TypeError,
);
assert.throws(
  () =>
    renderer.renderSmooth32(
      new FakeCanvas(160, 220, {}),
      renderer.spec32(0),
    ),
  TypeError,
);

const invalidGrid = renderer.pixels32(0);
invalidGrid.pixels[0] = 2;
const untouchedRaster = new FakeCanvas(16, 22);
assert.throws(
  () => renderer.renderRaster32(untouchedRaster, invalidGrid),
  RangeError,
);
assert.deepEqual(untouchedRaster.context.operations, []);

const invalidVisual = renderer.spec40(0n);
invalidVisual.connections[0] = 2;
const untouchedSmooth = new FakeCanvas(22, 22);
assert.throws(
  () => renderer.renderSmooth40(untouchedSmooth, invalidVisual),
  RangeError,
);
assert.deepEqual(untouchedSmooth.context.operations, []);

console.log("BitSquiggles Canvas renderer tests passed");