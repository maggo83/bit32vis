import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  EDGE_COUNT, EDGES, formatHex, freeConnectionCount, matchesMode, mix32,
  MODES, parseHex, pixels, smoothBlobs, spec
} from "./bitsquiggle32.js";
import * as canvasRenderer from "./bitsquiggle32-renderer-canvas.js";
import * as core from "./bitsquiggle32.js";

const fixtures = JSON.parse(await readFile(new URL("../fixtures/v1-32.json", import.meta.url), "utf8"));
assert.equal(fixtures.schema, "bitsquiggles-conformance", "known fixture schema");
assert.equal(fixtures.version, 1, "known fixture version");
assert.deepEqual(fixtures.dimensions, { rows: 7, columns: 5, edges: 58, pixelWidth: 16, pixelHeight: 22 }, "known dimensions");
assert.equal(EDGES.length, 58, "canonical edge count");
assert.equal(EDGE_COUNT, EDGES.length, "exported edge count");
for (const [name, value] of Object.entries(core)) {
  assert.equal(canvasRenderer[name], value, `Canvas renderer re-exports ${name}`);
}
assert.equal(typeof canvasRenderer.renderRaster, "function", "Canvas raster renderer export");
assert.equal(typeof canvasRenderer.renderSmooth, "function", "Canvas smooth renderer export");

for (const vector of fixtures.vectors) {
  const input = Number.parseInt(vector.input, 16) | 0;
  const visual = spec(input);
  const raster = pixels(input);
  assert.equal(formatHex(visual.mixed).toLowerCase(), vector.mixed, `mixed value for ${vector.input}`);
  assert.equal([...visual.connections].join(""), vector.connections, `connections for ${vector.input}`);
  assert.equal(visual.preferredMode, vector.preferredMode, `preferred mode for ${vector.input}`);
  assert.equal(visual.actualMode, vector.actualMode, `actual mode for ${vector.input}`);
  assert.equal(visual.fallback, vector.fallback, `fallback state for ${vector.input}`);
  assert.equal([...raster.pixels].join(""), vector.pixels, `raster for ${vector.input}`);
  for (const [style, { background, foreground }] of Object.entries(vector.styles)) {
    const styled = spec(input, style);
    assert.equal(styled.background, background, `${style} background for ${vector.input}`);
    assert.equal(styled.foreground, foreground, `${style} foreground for ${vector.input}`);
    assert.deepEqual([...styled.connections], [...visual.connections], `${style} preserves geometry for ${vector.input}`);
  }
}

assert.equal(parseHex("0xFFFFFFFF"), -1, "parse unsigned maximum");
assert.equal(formatHex(-1), "FFFFFFFF", "format unsigned maximum");
assert.throws(() => parseHex("not hex"), /hexadecimal/, "reject invalid hexadecimal");
assert.equal(mix32(0x89abcdef), 0x47ac5876, "public mixed value");
assert.deepEqual(MODES.map(freeConnectionCount), [32, 31, 29, 33], "public free class counts");
assert.equal(matchesMode(spec(0x89abcdef).connections, "A-"), true, "public mode membership");

for (let input = 0; input < 10_000; input += 1) {
  const visual = spec(input, "black-and-white");
  assert.match(visual.background, /^#(?:000000|ffffff)$/, "binary black-and-white background");
  assert.match(visual.foreground, /^#(?:000000|ffffff)$/, "binary black-and-white foreground");
  assert.notEqual(visual.background, visual.foreground, "opposed black-and-white colors");
  assert.equal(visual.foreground === "#000000", visual.swapped, "parity controls foreground polarity");
}

function connections(...endpoints) {
  const result = new Uint8Array(EDGE_COUNT);
  for (let offset = 0; offset < endpoints.length; offset += 4) {
    const target = endpoints.slice(offset, offset + 4);
    const edgeIndex = EDGES.findIndex((edge) => edge.startRow === target[0]
      && edge.startColumn === target[1] && edge.endRow === target[2]
      && edge.endColumn === target[3]);
    result[edgeIndex] = 1;
  }
  return result;
}

assert.deepEqual(smoothBlobs(new Uint8Array(EDGE_COUNT)), [], "empty mask has no blobs");
assert.deepEqual(smoothBlobs(connections(0, 0, 0, 1)), [
  { topRow: 0, leftColumn: 0, bottomRow: 0, rightColumn: 1 }
], "one edge has one blob");
assert.deepEqual(smoothBlobs(connections(0, 0, 0, 1, 0, 1, 0, 2)), [
  { topRow: 0, leftColumn: 0, bottomRow: 0, rightColumn: 2 }
], "connected row merges into one blob");
assert.deepEqual(smoothBlobs(connections(
  0, 0, 0, 1,
  1, 0, 1, 1,
  0, 0, 1, 0,
  0, 1, 1, 1
)), [{ topRow: 0, leftColumn: 0, bottomRow: 1, rightColumn: 1 }], "junction merges into one blob");
assert.deepEqual(smoothBlobs(new Uint8Array(EDGE_COUNT).fill(1)), [
  { topRow: 0, leftColumn: 0, bottomRow: 6, rightColumn: 4 }
], "complete grid merges into one blob");
for (let input = 0; input < 2000; input += 1) {
  assert.ok(smoothBlobs(spec(input).connections).length <= 82, `bounded blob count for ${input}`);
}
assert.throws(() => smoothBlobs(new Uint8Array(57)), /58 entries/, "reject short blob mask");
assert.throws(() => smoothBlobs(new Uint8Array(EDGE_COUNT).fill(2)), /zeroes and ones/, "reject non-binary blob mask");
console.log(`BitSquiggles browser tests passed (${fixtures.vectors.length} Java parity vectors)`);