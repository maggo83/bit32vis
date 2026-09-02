import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import * as core from "./bitsquiggles-core.js";

const variants = [
  {
    width: 32,
    fixturePath: "../fixtures/v1-32.json",
    parseInput: (value) => Number.parseInt(value, 16),
    format: core.formatHex32,
    spec: core.spec32,
    pixels: core.pixels32,
    edgeLimit: 82,
  },
  {
    width: 40,
    fixturePath: "../fixtures/v1-40.json",
    parseInput: (value) => BigInt(`0x${value}`),
    format: core.formatHex40,
    spec: core.spec40,
    pixels: core.pixels40,
    edgeLimit: 120,
  },
];

let checks = 0;

function check(condition, message) {
  assert.ok(condition, message);
  checks += 1;
}

function equal(actual, expected, message) {
  assert.equal(actual, expected, message);
  checks += 1;
}

function deepEqual(actual, expected, message) {
  assert.deepEqual(actual, expected, message);
  checks += 1;
}

function throws(operation, message) {
  assert.throws(operation, RangeError, message);
  checks += 1;
}

function edgeKey(startRow, startColumn, endRow, endColumn) {
  return `${startRow},${startColumn},${endRow},${endColumn}`;
}

function edgeIndexMap(edges) {
  return new Map(
    edges.map((edge, index) => [
      edgeKey(
        edge.startRow,
        edge.startColumn,
        edge.endRow,
        edge.endColumn,
      ),
      index,
    ]),
  );
}

function markerIndexes(edges) {
  const indexes = edgeIndexMap(edges);
  return [
    indexes.get(edgeKey(2, 3, 3, 3)),
    indexes.get(edgeKey(3, 2, 3, 3)),
    indexes.get(edgeKey(3, 3, 3, 4)),
    indexes.get(edgeKey(3, 3, 4, 3)),
  ];
}

function clearMarker(connections, edges) {
  const result = connections.slice();
  for (const index of markerIndexes(edges)) result[index] = 0;
  return result;
}

function rotateConnections(connections, edges) {
  const indexes = edgeIndexMap(edges);
  const result = new Uint8Array(connections.length);
  edges.forEach((edge, index) => {
    if (!connections[index]) return;
    const first = [edge.startColumn, 6 - edge.startRow];
    const second = [edge.endColumn, 6 - edge.endRow];
    const [start, end] =
      first[0] < second[0] ||
      (first[0] === second[0] && first[1] < second[1])
        ? [first, second]
        : [second, first];
    result[indexes.get(edgeKey(...start, ...end))] = 1;
  });
  return result;
}

function expectedCells(connections, edges, rows, columns) {
  const result = Array.from({ length: rows }, () => Array(columns).fill(0));
  connections.forEach((selected, index) => {
    if (!selected) return;
    const edge = edges[index];
    result[edge.startRow][edge.startColumn] = 1;
    result[edge.endRow][edge.endColumn] = 1;
  });
  return result;
}

function verifyRaster(grid, connections, edges) {
  const at = (x, y) => grid.pixels[y * grid.width + x];
  for (let x = 0; x < grid.width; x += 1) {
    equal(at(x, 0), 0, "top raster border");
    equal(at(x, grid.height - 1), 0, "bottom raster border");
  }
  for (let y = 0; y < grid.height; y += 1) {
    equal(at(0, y), 0, "left raster border");
    equal(at(grid.width - 1, y), 0, "right raster border");
  }
  edges.forEach((edge, index) => {
    const x = 1 + edge.startColumn * 3;
    const y = 1 + edge.startRow * 3;
    const bridge =
      edge.startRow === edge.endRow ? at(x + 2, y) : at(x, y + 2);
    equal(bridge, connections[index], "bridge pixel recovers edge");
  });
}

function bitCount(value) {
  let count = 0;
  while (value) {
    value &= value - 1n;
    count += 1;
  }
  return count;
}

function verifyDiffusion(width, mix) {
  const base = mix(width === 32 ? 0 : 0n);
  let changedBits = 0;
  for (let bit = 0; bit < width; bit += 1) {
    const input = width === 32 ? 2 ** bit : 1n << BigInt(bit);
    const difference = BigInt(base) ^ BigInt(mix(input));
    check(difference !== 0n, `${width}-bit mixer changes one-bit neighbor`);
    changedBits += bitCount(difference);
  }
  check(
    changedBits / width >= width / 3,
    `${width}-bit mixer has useful one-bit diffusion`,
  );
}

function connectionMask(edges, ...endpoints) {
  const indexes = edgeIndexMap(edges);
  const result = new Uint8Array(edges.length);
  for (let offset = 0; offset < endpoints.length; offset += 4) {
    result[indexes.get(edgeKey(...endpoints.slice(offset, offset + 4)))] = 1;
  }
  return result;
}

function verifyBlobCoverage(variant, connections) {
  const dimensions = core.dimensions(variant.width);
  const edges = core.edges(variant.width);
  const indexes = edgeIndexMap(edges);
  const activeCells = expectedCells(
    connections,
    edges,
    dimensions.rows,
    dimensions.columns,
  );
  const blobs = core.smoothBlobs(variant.width, connections);
  check(blobs.length <= variant.edgeLimit, "bounded smooth blob count");
  deepEqual(
    blobs,
    core.smoothBlobs(variant.width, connections),
    "smooth blobs are deterministic",
  );

  const covered = new Uint8Array(edges.length);
  for (const blob of blobs) {
    check(
      blob.topRow >= 0 &&
        blob.leftColumn >= 0 &&
        blob.bottomRow < dimensions.rows &&
        blob.rightColumn < dimensions.columns &&
        blob.topRow <= blob.bottomRow &&
        blob.leftColumn <= blob.rightColumn,
      "smooth blob bounds",
    );
    check(
      blob.topRow < blob.bottomRow || blob.leftColumn < blob.rightColumn,
      "smooth blob has an internal edge",
    );
    for (let row = blob.topRow; row <= blob.bottomRow; row += 1) {
      for (let column = blob.leftColumn; column <= blob.rightColumn; column += 1) {
        equal(activeCells[row][column], 1, "smooth blob contains active cells");
        if (column < blob.rightColumn) {
          const index = indexes.get(edgeKey(row, column, row, column + 1));
          equal(connections[index], 1, "blob preserves horizontal connection");
          covered[index] = 1;
        }
        if (row < blob.bottomRow) {
          const index = indexes.get(edgeKey(row, column, row + 1, column));
          equal(connections[index], 1, "blob preserves vertical connection");
          covered[index] = 1;
        }
      }
    }
  }
  deepEqual(covered, connections, "smooth blobs cover every selected edge");
  for (let row = 0; row < dimensions.rows - 1; row += 1) {
    for (let column = 0; column < dimensions.columns - 1; column += 1) {
      const perimeter = [
        indexes.get(edgeKey(row, column, row, column + 1)),
        indexes.get(edgeKey(row + 1, column, row + 1, column + 1)),
        indexes.get(edgeKey(row, column, row + 1, column)),
        indexes.get(edgeKey(row, column + 1, row + 1, column + 1)),
      ];
      if (!perimeter.every((index) => connections[index])) continue;
      check(
        blobs.some(
          (blob) =>
            blob.topRow <= row &&
            blob.leftColumn <= column &&
            blob.bottomRow >= row + 1 &&
            blob.rightColumn >= column + 1,
        ),
        "smooth blobs cover every required junction",
      );
    }
  }
}

function verifyCanonicalEdges(variant) {
  const dimensions = core.dimensions(variant.width);
  const edges = core.edges(variant.width);
  const expected = [];
  for (let row = 0; row < dimensions.rows; row += 1) {
    for (let column = 0; column < dimensions.columns; column += 1) {
      if (column + 1 < dimensions.columns)
        expected.push({
          startRow: row,
          startColumn: column,
          endRow: row,
          endColumn: column + 1,
        });
      if (row + 1 < dimensions.rows)
        expected.push({
          startRow: row,
          startColumn: column,
          endRow: row + 1,
          endColumn: column,
        });
    }
  }
  deepEqual(edges, expected, `${variant.width}-bit canonical edge order`);
  check(Object.isFrozen(edges), "edge collection is immutable");
  check(edges.every(Object.isFrozen), "edge values are immutable");
}

async function verifyFixture(variant) {
  const fixture = JSON.parse(
    await readFile(new URL(variant.fixturePath, import.meta.url), "utf8"),
  );
  const dimensions = core.dimensions(variant.width);
  const edges = core.edges(variant.width);
  deepEqual(dimensions, {
    rows: fixture.dimensions.rows,
    columns: fixture.dimensions.columns,
    edgeCount: fixture.dimensions.edges,
    pixelWidth: fixture.dimensions.pixelWidth,
    pixelHeight: fixture.dimensions.pixelHeight,
  });
  check(Object.isFrozen(dimensions), "dimension value is immutable");
  const masks = new Set();
  const preferredModes = new Set();
  let sawFallback = false;

  for (const vector of fixture.vectors) {
    const input = variant.parseInput(vector.input);
    const visual = variant.spec(input);
    const grid = variant.pixels(input);
    const connectionString = [...visual.connections].join("");
    equal(variant.format(visual.mixed).toLowerCase(), vector.mixed);
    equal(connectionString, vector.connections);
    equal([...grid.pixels].join(""), vector.pixels);
    equal(visual.preferredMode, vector.preferredMode);
    equal(visual.actualMode, vector.actualMode);
    equal(visual.fallback, vector.fallback);
    deepEqual(
      visual.cells,
      expectedCells(visual.connections, edges, dimensions.rows, dimensions.columns),
      "active cells derive from selected edges",
    );
    check(!masks.has(connectionString), "sampled masks are unique");
    masks.add(connectionString);
    preferredModes.add(visual.preferredMode);
    sawFallback ||= visual.fallback;

    const dataConnections =
      variant.width === 40
        ? clearMarker(visual.connections, edges)
        : visual.connections;
    check(
      core.matchesMode(variant.width, dataConnections, visual.actualMode),
      "actual mode membership",
    );
    const actualIndex = core.MODES.indexOf(visual.actualMode);
    for (let index = 0; index < actualIndex; index += 1) {
      check(
        !core.matchesMode(variant.width, dataConnections, core.MODES[index]),
        "accepted mode excludes earlier families",
      );
    }

    for (const [style, colors] of Object.entries(vector.styles)) {
      const styled = variant.spec(input, style);
      equal(styled.background, colors.background);
      equal(styled.foreground, colors.foreground);
      deepEqual(styled.connections, visual.connections, "style preserves geometry");
    }
  }

  deepEqual(preferredModes, new Set(core.MODES), "all preferred modes observed");
  check(sawFallback, `${variant.width}-bit overlap fallback observed`);

  for (const vector of fixture.vectors.slice(0, 32)) {
    const input = variant.parseInput(vector.input);
    const visual = variant.spec(input);
    verifyRaster(variant.pixels(input), visual.connections, edges);
  }
  for (const vector of fixture.vectors.slice(0, 200)) {
    verifyBlobCoverage(variant, variant.spec(variant.parseInput(vector.input)).connections);
  }

  return { fixture, masks };
}

deepEqual(core.MODES.map((mode) => core.freeConnectionCount(32, mode)), [32, 31, 29, 33]);
deepEqual(core.MODES.map((mode) => core.usableConnectionCount(32, mode)), [32, 31, 29, 33]);
deepEqual(core.MODES.map((mode) => core.freeConnectionCount(40, mode)), [45, 45, 42, 42]);
deepEqual(core.MODES.map((mode) => core.usableConnectionCount(40, mode)), [42, 42, 40, 40]);

for (const variant of variants) verifyCanonicalEdges(variant);

equal(core.mix32(0x89abcdef), 0x47ac5876, "32-bit golden mixer");
equal(core.mix40(0x39527804dbn), 0x34b1a077c8n, "40-bit golden mixer");
verifyDiffusion(32, core.mix32);
verifyDiffusion(40, core.mix40);

const results = [];
for (const variant of variants) results.push(await verifyFixture(variant));

const marker = markerIndexes(core.EDGES40);
for (const vector of results[1].fixture.vectors) {
  const visual = core.spec40(BigInt(`0x${vector.input}`));
  deepEqual(marker.map((index) => visual.connections[index]), [1, 0, 0, 0]);
  let rotated = visual.connections;
  for (let turn = 1; turn < 4; turn += 1) {
    rotated = rotateConnections(rotated, core.EDGES40);
    check(
      !results[1].masks.has([...rotated].join("")),
      "rotated 40-bit mask is outside the sampled valid set",
    );
  }
}

for (const input of [0xa7912def7bn, 0x08a1a65b0cn, 0x500181b841n]) {
  const visual = core.spec40(input);
  check(visual.fallback, "targeted 40-bit overlap fallback");
  equal(visual.actualMode, "A|", "40-bit fallback uses default mode");
}

equal(core.bip380ChecksumInput("qqqqqqqq"), 0n);
equal(core.bip380ChecksumInput("89f8spxm"), 0x39527804dbn);
equal(core.bip380ChecksumInput("llllllll"), 0xffffffffffn);
for (const checksum of [null, "89f8spx", "89f8spxmq", "89F8spxm", "89f8#pxm", "iiiiiiii"])
  throws(() => core.bip380ChecksumInput(checksum), "reject invalid BIP380 checksum");

equal(core.parseHex32("0xFFFFFFFF"), 0xffffffff);
equal(core.formatHex32(0xffffffff), "FFFFFFFF");
equal(core.parseHex40("0xFFFFFFFFFF"), 0xffffffffffn);
equal(core.formatHex40(0xffffffffffn), "FFFFFFFFFF");
for (const value of [null, "", "xyz", "100000000", -1])
  throws(() => core.parseHex32(value), "reject invalid 32-bit hexadecimal input");
for (const value of [null, "", "xyz", "10000000000", 1])
  throws(() => core.parseHex40(value), "reject invalid 40-bit hexadecimal input");
for (const value of [-1, 0x100000000, 1.5, "1", null])
  throws(() => core.spec32(value), "reject invalid 32-bit input");
for (const value of [-1n, 0x10000000000n, 1, "1", null])
  throws(() => core.spec40(value), "reject invalid 40-bit input");
throws(() => core.spec32(0, "invalid"), "reject invalid style");
throws(() => core.dimensions(33), "reject invalid width");
throws(() => core.freeConnectionCount(32, "invalid"), "reject invalid mode");
throws(() => core.matchesMode(32, new Uint8Array(57), "A|"), "reject short mask");
throws(() => core.matchesMode(40, Array(84).fill(0), "A|"), "reject non-typed mask");
const invalidMask = new Uint8Array(84);
invalidMask[0] = 2;
throws(() => core.smoothBlobs(40, invalidMask), "reject non-binary mask");

for (const variant of variants) {
  const edges = core.edges(variant.width);
  deepEqual(core.smoothBlobs(variant.width, new Uint8Array(edges.length)), []);
  deepEqual(
    core.smoothBlobs(
      variant.width,
      connectionMask(edges, 0, 0, 0, 1),
    ),
    [{ topRow: 0, leftColumn: 0, bottomRow: 0, rightColumn: 1 }],
  );
  deepEqual(
    core.smoothBlobs(
      variant.width,
      connectionMask(edges, 0, 0, 0, 1, 0, 1, 0, 2),
    ),
    [{ topRow: 0, leftColumn: 0, bottomRow: 0, rightColumn: 2 }],
    "edge-count priority extends a connected row",
  );
  const squareWithTail = connectionMask(
    edges,
    0, 0, 0, 1,
    0, 1, 0, 2,
    0, 2, 0, 3,
    0, 3, 0, 4,
    1, 0, 1, 1,
    0, 0, 1, 0,
    0, 1, 1, 1,
  );
  deepEqual(
    core.smoothBlobs(variant.width, squareWithTail),
    [
      { topRow: 0, leftColumn: 0, bottomRow: 1, rightColumn: 1 },
      { topRow: 0, leftColumn: 1, bottomRow: 0, rightColumn: 4 },
    ],
    "junction priority precedes the longer edge-only candidate",
  );
  verifyBlobCoverage(variant, squareWithTail);
  deepEqual(
    core.smoothBlobs(variant.width, new Uint8Array(edges.length).fill(1)),
    [
      {
        topRow: 0,
        leftColumn: 0,
        bottomRow: 6,
        rightColumn: core.dimensions(variant.width).columns - 1,
      },
    ],
  );
}

const firstVisual = core.spec32(1);
const secondVisual = core.spec32(1);
check(firstVisual.connections !== secondVisual.connections, "spec returns a fresh mask");
check(firstVisual.cells !== secondVisual.cells, "spec returns fresh cell rows");
const firstGrid = core.pixels40(1n);
const secondGrid = core.pixels40(1n);
check(firstGrid.pixels !== secondGrid.pixels, "pixels returns a fresh raster");
firstVisual.connections.fill(0);
check(secondVisual.connections.some(Boolean), "caller mutation cannot affect later specs");

for (const obsolete of ["spec", "pixels", "parseHex", "formatHex"])
  check(!(obsolete in core), `unified core omits unsuffixed ${obsolete}`);

console.log(`BitSquiggles JavaScript core tests passed (${checks} checks)`);