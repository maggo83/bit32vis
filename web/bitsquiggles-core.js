// Shared dependency-free presentation primitives for BitSquiggle variants.
const UINT32_MAX = 0xffffffff;
const UINT40_MAX = 0xffffffffffn;
const CHECKSUM_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

export const STANDARD = "standard";
export const HIGH_CONTRAST = "high-contrast";
export const MONOCHROME = "monochrome";
export const BLACK_AND_WHITE = "black-and-white";
export const STYLES = Object.freeze([
  STANDARD,
  HIGH_CONTRAST,
  MONOCHROME,
  BLACK_AND_WHITE,
]);
export const MODES = Object.freeze(["A|", "A-", "A+", "A/"]);

export const ROWS32 = 7;
export const COLUMNS32 = 5;
export const EDGE_COUNT32 = 58;
export const PIXEL_WIDTH32 = 16;
export const PIXEL_HEIGHT32 = 22;
export const ROWS40 = 7;
export const COLUMNS40 = 7;
export const EDGE_COUNT40 = 84;
export const PIXEL_WIDTH40 = 22;
export const PIXEL_HEIGHT40 = 22;

function requireInput32(input) {
  if (!Number.isInteger(input) || input < 0 || input > UINT32_MAX)
    throw new RangeError("input must be an unsigned 32-bit integer");
}

function requireInput40(input) {
  if (typeof input !== "bigint" || input < 0n || input > UINT40_MAX)
    throw new RangeError("input must be an unsigned 40-bit bigint");
}

export function mix32(input) {
  requireInput32(input);
  let mixed = (input + 0x9e3779b9) >>> 0;
  mixed = Math.imul(mixed ^ (mixed >>> 16), 0x85ebca6b) >>> 0;
  mixed = Math.imul(mixed ^ (mixed >>> 13), 0xc2b2ae35) >>> 0;
  return (mixed ^ (mixed >>> 16)) >>> 0;
}

export function mix40(input) {
  requireInput40(input);
  let mixed = (input + 0xb97f4a7c15n) & UINT40_MAX;
  mixed = ((mixed ^ (mixed >> 20n)) * 0xd7ed558ccdn) & UINT40_MAX;
  mixed = ((mixed ^ (mixed >> 15n)) * 0xfe1a85ec53n) & UINT40_MAX;
  return (mixed ^ (mixed >> 20n)) & UINT40_MAX;
}

export function bip380ChecksumInput(checksum) {
  if (typeof checksum !== "string" || checksum.length !== 8)
    throw new RangeError("checksum must contain exactly eight characters");
  let input = 0n;
  for (const character of checksum) {
    const value = CHECKSUM_CHARSET.indexOf(character);
    if (value < 0) throw new RangeError("invalid BIP380 checksum character");
    input = (input << 5n) | BigInt(value);
  }
  return input;
}

function parseHex(value, digits, convert) {
  const normalized =
    typeof value === "string" ? value.trim().replace(/^0x/i, "") : "";
  if (!new RegExp(`^[0-9a-fA-F]{1,${digits}}$`).test(normalized))
    throw new RangeError(`enter 1-${digits} hexadecimal digits`);
  return convert(normalized);
}

export function parseHex32(value) {
  return parseHex(value, 8, (normalized) => Number.parseInt(normalized, 16));
}

export function parseHex40(value) {
  return parseHex(value, 10, (normalized) => BigInt(`0x${normalized}`));
}

export function formatHex32(input) {
  requireInput32(input);
  return input.toString(16).toUpperCase().padStart(8, "0");
}

export function formatHex40(input) {
  requireInput40(input);
  return input.toString(16).toUpperCase().padStart(10, "0");
}

function popcount(value) {
  let count = 0;
  while (value) {
    value &= value - 1n;
    count += 1;
  }
  return count;
}

function clamp01(value) {
  return Math.max(0, Math.min(1, value));
}
function srgbEncode(value) {
  value = clamp01(value);
  return value <= 0.0031308
    ? 12.92 * value
    : 1.055 * value ** (1 / 2.4) - 0.055;
}
function makeColor(lightness, chroma, hue) {
  const radians = (hue * Math.PI) / 180;
  const a = chroma * Math.cos(radians);
  const b = chroma * Math.sin(radians);
  const l = lightness + 0.3963377774 * a + 0.2158037573 * b;
  const m = lightness - 0.1055613458 * a - 0.0638541728 * b;
  const s = lightness - 0.0894841775 * a - 1.291485548 * b;
  const encode = (channel) =>
    Math.round(srgbEncode(channel) * 255)
      .toString(16)
      .padStart(2, "0");
  return (
    `#${encode(4.0767416621 * l ** 3 - 3.3077115913 * m ** 3 + 0.2309699292 * s ** 3)}` +
    `${encode(-1.2684380046 * l ** 3 + 2.6097574011 * m ** 3 - 0.3413193965 * s ** 3)}` +
    `${encode(-0.0041960863 * l ** 3 - 0.7034186147 * m ** 3 + 1.707614701 * s ** 3)}`
  );
}
function colors(mixed, input, style) {
  const asBigInt = typeof mixed === "bigint";
  const bits = (shift, mask) =>
    Number(
      asBigInt
        ? (mixed >> BigInt(shift)) & BigInt(mask)
        : (mixed >>> shift) & mask,
    );
  const hue = bits(12, 0xf) * 22.5;
  const chroma = 0.05 + bits(8, 0xf) * (0.2 / 15);
  const base = 0.5 + bits(0, 0x3) * (0.2 / 3);
  let foregroundL = base;
  let backgroundL = foregroundL - 0.5;
  let foregroundC = chroma;
  let backgroundC = chroma;
  if (style !== "standard") {
    foregroundL = style === "black-and-white" ? 1 : base + 0.3;
    backgroundL = style === "black-and-white" ? 0 : foregroundL - 0.8;
    foregroundC =
      style === "monochrome" || style === "black-and-white" ? 0 : chroma + 0.1;
    backgroundC = foregroundC;
  }
  foregroundL = clamp01(foregroundL);
  backgroundL = clamp01(backgroundL);
  const inputBits = typeof input === "bigint" ? input : BigInt(input >>> 0);
  if (popcount(inputBits) % 2)
    [foregroundL, backgroundL] = [backgroundL, foregroundL];
  return {
    background: makeColor(backgroundL, backgroundC, (hue + 180) % 360),
    foreground: makeColor(foregroundL, foregroundC, hue),
  };
}
function activeCells(connections, edges, rows, columns) {
  const cells = Array.from({ length: rows }, () => Array(columns).fill(0));
  connections.forEach((connected, index) => {
    if (!connected) return;
    const edge = edges[index];
    cells[edge.startRow][edge.startColumn] = 1;
    cells[edge.endRow][edge.endColumn] = 1;
  });
  return cells;
}
function generatePixels(connections, edges, rows, columns) {
  const width = columns * 3 + 1;
  const raster = new Uint8Array(width * (rows * 3 + 1));
  const cells = activeCells(connections, edges, rows, columns);
  const set = (x, y) => {
    raster[y * width + x] = 1;
  };
  cells.forEach((row, y) =>
    row.forEach((active, x) => {
      if (active) {
        const left = 1 + x * 3;
        const top = 1 + y * 3;
        set(left, top);
        set(left + 1, top);
        set(left, top + 1);
        set(left + 1, top + 1);
      }
    }),
  );
  connections.forEach((connected, index) => {
    if (!connected) return;
    const edge = edges[index];
    const x = 1 + edge.startColumn * 3;
    const y = 1 + edge.startRow * 3;
    if (edge.startRow === edge.endRow) {
      set(x + 2, y);
      set(x + 2, y + 1);
    } else {
      set(x, y + 2);
      set(x + 1, y + 2);
    }
  });
  const edgeMap = new Map(
    edges.map((edge, index) => [
      [edge.startRow, edge.startColumn, edge.endRow, edge.endColumn].join(),
      index,
    ]),
  );
  const selected = (...edge) => connections[edgeMap.get(edge.join())];
  for (let row = 0; row < rows - 1; row += 1)
    for (let column = 0; column < columns - 1; column += 1) {
      if (
        selected(row, column, row, column + 1) &&
        selected(row + 1, column, row + 1, column + 1) &&
        selected(row, column, row + 1, column) &&
        selected(row, column + 1, row + 1, column + 1)
      )
        set(3 + column * 3, 3 + row * 3);
    }
  return raster;
}

const TEMPLATES32 = [
  [
    "A B C B' A'",
    "D E F E' D'",
    "G H I H' G'",
    "J K L K' J'",
    "M N O N' M'",
    "P Q R Q' P'",
    "S T U T' S'",
  ],
  [
    "A B C D E",
    "F G H I J",
    "K L M N O",
    "P Q R S T",
    "K' L' M' N' O'",
    "F' G' H' I' J'",
    "A' B' C' D' E'",
  ],
  [
    "A B C D E",
    "F G H I J",
    "K L M N O",
    "P Q R Q' P'",
    "O' N' M' L' K'",
    "J' I' H' G' F'",
    "E' D' C' B' A'",
  ],
  [
    "A B C D E",
    "F G H I J",
    "K L M N I'",
    "O P Q M' H'",
    "R S P' L' G'",
    "T R' O' K' F'",
    "E' D' C' B' A'",
  ],
].map((rows) => rows.map((row) => row.split(" ")));

const TRANSFORMS40 = [
  (row, column) => [row, 6 - column],
  (row, column) => [6 - row, column],
  (row, column) => [6 - row, 6 - column],
  (row, column) => [6 - column, 6 - row],
];

function edgeKey(startRow, startColumn, endRow, endColumn) {
  return `${startRow},${startColumn},${endRow},${endColumn}`;
}

function edgeTuple(edge) {
  return [edge.startRow, edge.startColumn, edge.endRow, edge.endColumn];
}

function compareTuples(first, second) {
  for (let index = 0; index < first.length; index += 1) {
    if (first[index] !== second[index]) return first[index] - second[index];
  }
  return 0;
}

function canonicalEdge(firstRow, firstColumn, secondRow, secondColumn) {
  const first = [firstRow, firstColumn];
  const second = [secondRow, secondColumn];
  return compareTuples(first, second) < 0
    ? [...first, ...second]
    : [...second, ...first];
}

function createEdges(rows, columns) {
  const result = [];
  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      if (column + 1 < columns)
        result.push(
          Object.freeze({
            startRow: row,
            startColumn: column,
            endRow: row,
            endColumn: column + 1,
          }),
        );
      if (row + 1 < rows)
        result.push(
          Object.freeze({
            startRow: row,
            startColumn: column,
            endRow: row + 1,
            endColumn: column,
          }),
        );
    }
  }
  return Object.freeze(result);
}

function freezeDefinition(classes) {
  const frozenClasses = classes.map(({ occurrences, excluded = false }) =>
    Object.freeze({ occurrences: Object.freeze(occurrences), excluded }),
  );
  return Object.freeze({
    classes: Object.freeze(frozenClasses),
    usable: Object.freeze(frozenClasses.filter((entry) => !entry.excluded)),
  });
}

function createCopiedDefinition(edges, columns, template) {
  const references = [];
  const sources = new Map();
  template.forEach((row, rowIndex) =>
    row.forEach((token, columnIndex) => {
      const copied = token.endsWith("'");
      const name = copied ? token.slice(0, -1) : token;
      references[rowIndex * columns + columnIndex] = name;
      if (!copied) sources.set(name, rowIndex * columns + columnIndex);
    }),
  );

  const groups = new Map();
  edges.forEach((edge, index) => {
    const first = sources.get(
      references[edge.startRow * columns + edge.startColumn],
    );
    const second = sources.get(
      references[edge.endRow * columns + edge.endColumn],
    );
    const key = Math.min(first, second) * 64 + Math.max(first, second);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(index);
  });
  return freezeDefinition(
    [...groups.entries()]
      .sort(([first], [second]) => first - second)
      .map(([, occurrences]) => ({ occurrences })),
  );
}

function createGeometricDefinition(edges, markerIndexes, transform) {
  const groups = new Map();
  edges.forEach((edge, index) => {
    const first = transform(edge.startRow, edge.startColumn);
    const second = transform(edge.endRow, edge.endColumn);
    const original = edgeTuple(edge);
    const transformed = canonicalEdge(...first, ...second);
    const key =
      compareTuples(original, transformed) <= 0 ? original : transformed;
    const serialized = key.join(",");
    if (!groups.has(serialized)) groups.set(serialized, { key, occurrences: [] });
    groups.get(serialized).occurrences.push(index);
  });
  return freezeDefinition(
    [...groups.values()]
      .sort((first, second) => compareTuples(first.key, second.key))
      .map(({ occurrences }) => ({
        occurrences,
        excluded: occurrences.some((index) => markerIndexes.includes(index)),
      })),
  );
}

function createVariant32() {
  const edges = createEdges(ROWS32, COLUMNS32);
  return Object.freeze({
    width: 32,
    rows: ROWS32,
    columns: COLUMNS32,
    edgeCount: EDGE_COUNT32,
    pixelWidth: PIXEL_WIDTH32,
    pixelHeight: PIXEL_HEIGHT32,
    inputBits: 32,
    payloadBits: 30,
    edges,
    definitions: Object.freeze(
      TEMPLATES32.map((template) =>
        createCopiedDefinition(edges, COLUMNS32, template),
      ),
    ),
    markerIndexes: Object.freeze([]),
  });
}

function createVariant40() {
  const edges = createEdges(ROWS40, COLUMNS40);
  const indexes = new Map(
    edges.map((edge, index) => [edgeKey(...edgeTuple(edge)), index]),
  );
  const markerIndexes = Object.freeze([
    indexes.get(edgeKey(2, 3, 3, 3)),
    indexes.get(edgeKey(3, 2, 3, 3)),
    indexes.get(edgeKey(3, 3, 3, 4)),
    indexes.get(edgeKey(3, 3, 4, 3)),
  ]);
  return Object.freeze({
    width: 40,
    rows: ROWS40,
    columns: COLUMNS40,
    edgeCount: EDGE_COUNT40,
    pixelWidth: PIXEL_WIDTH40,
    pixelHeight: PIXEL_HEIGHT40,
    inputBits: 40,
    payloadBits: 38,
    edges,
    definitions: Object.freeze(
      TRANSFORMS40.map((transform) =>
        createGeometricDefinition(edges, markerIndexes, transform),
      ),
    ),
    markerIndexes,
  });
}

const VARIANT32 = createVariant32();
const VARIANT40 = createVariant40();

export const EDGES32 = VARIANT32.edges;
export const EDGES40 = VARIANT40.edges;

function variantFor(width) {
  if (width === 32) return VARIANT32;
  if (width === 40) return VARIANT40;
  throw new RangeError("width must be 32 or 40");
}

function modeIndex(mode) {
  const index = MODES.indexOf(mode);
  if (index < 0) throw new RangeError(`unknown mode: ${mode}`);
  return index;
}

function requireConnections(variant, connections) {
  if (!(connections instanceof Uint8Array) || connections.length !== variant.edgeCount)
    throw new RangeError(
      `connections must be a Uint8Array with ${variant.edgeCount} entries`,
    );
  for (const value of connections) {
    if (value !== 0 && value !== 1)
      throw new RangeError("connections must contain only zeroes and ones");
  }
}

function valueBit(value, bitWidth, classIndex) {
  const shift = bitWidth - 1 - (classIndex % bitWidth);
  return typeof value === "bigint"
    ? Number((value >> BigInt(shift)) & 1n)
    : (value >>> shift) & 1;
}

function encode(variant, mixed, preferredModeIndex) {
  const definition = variant.definitions[preferredModeIndex];
  const bitWidth = preferredModeIndex === 0 ? variant.inputBits : variant.payloadBits;
  const value =
    preferredModeIndex === 0
      ? mixed
      : typeof mixed === "bigint"
        ? mixed & 0x3fffffffffn
        : mixed & 0x3fffffff;
  const connections = new Uint8Array(variant.edgeCount);
  definition.usable.forEach((connectionClass, classIndex) => {
    const bit = valueBit(value, bitWidth, classIndex);
    for (const edgeIndex of connectionClass.occurrences)
      connections[edgeIndex] = bit;
  });
  return connections;
}

function matchesDefinition(connections, definition) {
  return definition.classes.every((connectionClass) => {
    const expected = connections[connectionClass.occurrences[0]];
    return (
      (!connectionClass.excluded || expected === 0) &&
      connectionClass.occurrences.every(
        (edgeIndex) => connections[edgeIndex] === expected,
      )
    );
  });
}

function conflictsWithEarlierMode(variant, connections, preferredModeIndex) {
  for (let index = 0; index < preferredModeIndex; index += 1) {
    if (matchesDefinition(connections, variant.definitions[index])) return true;
  }
  return false;
}

function hasCapacity(variant, mixed, preferredModeIndex) {
  return variant.width !== 32 || preferredModeIndex !== 2 || (mixed & 1) === 0;
}

function applyMarker(variant, connections) {
  if (variant.markerIndexes.length === 0) return;
  for (const index of variant.markerIndexes) connections[index] = 0;
  connections[variant.markerIndexes[0]] = 1;
}

function specFor(variant, input, style) {
  if (!STYLES.includes(style)) throw new RangeError(`unknown style: ${style}`);
  if (variant.width === 32) requireInput32(input);
  else requireInput40(input);
  const mixed = variant.width === 32 ? mix32(input) : mix40(input);
  const preferredModeIndex =
    variant.width === 32 ? mixed >>> 30 : Number(mixed >> 38n);
  const candidate = encode(variant, mixed, preferredModeIndex);
  const fallback =
    preferredModeIndex !== 0 &&
    (!hasCapacity(variant, mixed, preferredModeIndex) ||
      conflictsWithEarlierMode(variant, candidate, preferredModeIndex));
  const actualModeIndex = fallback ? 0 : preferredModeIndex;
  const connections = fallback ? encode(variant, mixed, 0) : candidate;
  applyMarker(variant, connections);
  return {
    input,
    mixed,
    connections,
    cells: activeCells(connections, variant.edges, variant.rows, variant.columns),
    style,
    preferredMode: MODES[preferredModeIndex],
    actualMode: MODES[actualModeIndex],
    fallback,
    luminanceIndex:
      typeof mixed === "bigint" ? Number(mixed & 3n) : mixed & 3,
    swapped: popcount(BigInt(input)) % 2 === 1,
    ...colors(mixed, input, style),
  };
}

function pixelsFor(variant, input, style) {
  const visual = specFor(variant, input, style);
  return {
    width: variant.pixelWidth,
    height: variant.pixelHeight,
    pixels: generatePixels(
      visual.connections,
      variant.edges,
      variant.rows,
      variant.columns,
    ),
    background: visual.background,
    foreground: visual.foreground,
    style,
  };
}

export function dimensions(width) {
  const variant = variantFor(width);
  return Object.freeze({
    rows: variant.rows,
    columns: variant.columns,
    edgeCount: variant.edgeCount,
    pixelWidth: variant.pixelWidth,
    pixelHeight: variant.pixelHeight,
  });
}

export function edges(width) {
  return variantFor(width).edges;
}

export function freeConnectionCount(width, mode) {
  return variantFor(width).definitions[modeIndex(mode)].classes.length;
}

export function usableConnectionCount(width, mode) {
  return variantFor(width).definitions[modeIndex(mode)].usable.length;
}

export function matchesMode(width, connections, mode) {
  const variant = variantFor(width);
  requireConnections(variant, connections);
  return matchesDefinition(connections, variant.definitions[modeIndex(mode)]);
}

export function spec32(input, style = STANDARD) {
  return specFor(VARIANT32, input, style);
}

export function spec40(input, style = STANDARD) {
  return specFor(VARIANT40, input, style);
}

export function pixels32(input, style = STANDARD) {
  return pixelsFor(VARIANT32, input, style);
}

export function pixels40(input, style = STANDARD) {
  return pixelsFor(VARIANT40, input, style);
}

function horizontalEdgeBit(variant, row, column) {
  const offset =
    row === variant.rows - 1
      ? row * (2 * variant.columns - 1) + column
      : row * (2 * variant.columns - 1) + 2 * column;
  return 1n << BigInt(offset);
}

function verticalEdgeBit(variant, row, column) {
  const offset =
    column === variant.columns - 1 ? 2 * variant.columns - 2 : 2 * column + 1;
  return 1n << BigInt(row * (2 * variant.columns - 1) + offset);
}

function junctionBit(variant, row, column) {
  return 1n << BigInt(row * (variant.columns - 1) + column);
}

function requiredEdgeMask(variant, connections) {
  requireConnections(variant, connections);
  let result = 0n;
  connections.forEach((selected, index) => {
    if (selected) result |= 1n << BigInt(index);
  });
  return result;
}

function connectedRectangleEdgeMask(
  variant,
  top,
  left,
  bottom,
  right,
  requiredEdges,
) {
  let result = 0n;
  for (let row = top; row <= bottom; row += 1) {
    for (let column = left; column <= right; column += 1) {
      if (column < right) {
        const edge = horizontalEdgeBit(variant, row, column);
        if ((requiredEdges & edge) === 0n) return 0n;
        result |= edge;
      }
      if (row < bottom) {
        const edge = verticalEdgeBit(variant, row, column);
        if ((requiredEdges & edge) === 0n) return 0n;
        result |= edge;
      }
    }
  }
  return result;
}

function requiredJunctionMask(variant, requiredEdges) {
  let result = 0n;
  for (let row = 0; row < variant.rows - 1; row += 1) {
    for (let column = 0; column < variant.columns - 1; column += 1) {
      if (
        connectedRectangleEdgeMask(
          variant,
          row,
          column,
          row + 1,
          column + 1,
          requiredEdges,
        ) !== 0n
      )
        result |= junctionBit(variant, row, column);
    }
  }
  return result;
}

function rectangleJunctionMask(
  variant,
  top,
  left,
  bottom,
  right,
  requiredJunctions,
) {
  let result = 0n;
  for (let row = top; row < bottom; row += 1) {
    for (let column = left; column < right; column += 1) {
      const junction = junctionBit(variant, row, column);
      if ((requiredJunctions & junction) !== 0n) result |= junction;
    }
  }
  return result;
}

function isBetterBlob(
  candidate,
  best,
  newEdges,
  newJunctions,
  bestNewEdges,
  bestNewJunctions,
) {
  const junctionCount = popcount(newJunctions);
  const bestJunctionCount = popcount(bestNewJunctions);
  if (junctionCount !== bestJunctionCount)
    return junctionCount > bestJunctionCount;
  const edgeCount = popcount(newEdges);
  const bestEdgeCount = popcount(bestNewEdges);
  if (edgeCount !== bestEdgeCount) return edgeCount > bestEdgeCount;
  const area =
    (candidate.bottomRow - candidate.topRow + 1) *
    (candidate.rightColumn - candidate.leftColumn + 1);
  const bestArea =
    (best.bottomRow - best.topRow + 1) *
    (best.rightColumn - best.leftColumn + 1);
  if (area !== bestArea) return area < bestArea;
  if (candidate.topRow !== best.topRow) return candidate.topRow < best.topRow;
  if (candidate.leftColumn !== best.leftColumn)
    return candidate.leftColumn < best.leftColumn;
  if (candidate.bottomRow !== best.bottomRow)
    return candidate.bottomRow < best.bottomRow;
  return candidate.rightColumn < best.rightColumn;
}

function firstUncovered(required, covered, count) {
  const remaining = required & ~covered;
  for (let index = 0; index < count; index += 1) {
    if ((remaining & (1n << BigInt(index))) !== 0n) return index;
  }
  return -1;
}

export function smoothBlobs(width, connections) {
  const variant = variantFor(width);
  const requiredEdges = requiredEdgeMask(variant, connections);
  const requiredJunctions = requiredJunctionMask(variant, requiredEdges);
  let coveredEdges = 0n;
  let coveredJunctions = 0n;
  const result = [];

  while (
    coveredEdges !== requiredEdges ||
    coveredJunctions !== requiredJunctions
  ) {
    const anchorEdge = firstUncovered(
      requiredEdges,
      coveredEdges,
      variant.edgeCount,
    );
    const anchorJunction =
      anchorEdge < 0
        ? firstUncovered(
            requiredJunctions,
            coveredJunctions,
            (variant.rows - 1) * (variant.columns - 1),
          )
        : -1;
    let anchorTop;
    let anchorLeft;
    let anchorBottom;
    let anchorRight;
    if (anchorEdge >= 0) {
      const edge = variant.edges[anchorEdge];
      anchorTop = edge.startRow;
      anchorLeft = edge.startColumn;
      anchorBottom = edge.endRow;
      anchorRight = edge.endColumn;
    } else {
      anchorTop = Math.floor(anchorJunction / (variant.columns - 1));
      anchorLeft = anchorJunction % (variant.columns - 1);
      anchorBottom = anchorTop + 1;
      anchorRight = anchorLeft + 1;
    }

    let best = null;
    let bestEdges = 0n;
    let bestJunctions = 0n;
    let leftInclusive = 0;
    for (let top = anchorTop; top >= 0; top -= 1) {
      for (let left = anchorLeft; left >= leftInclusive; left -= 1) {
        let rightExclusive = variant.columns;
        let stopLeftExpansion = false;
        for (let bottom = anchorBottom; bottom < variant.rows; bottom += 1) {
          for (
            let right = anchorRight;
            right < rightExclusive;
            right += 1
          ) {
            const rectangleEdges = connectedRectangleEdgeMask(
              variant,
              top,
              left,
              bottom,
              right,
              requiredEdges,
            );
            if (rectangleEdges === 0n) {
              if (bottom === anchorBottom && right === anchorRight) {
                leftInclusive = left + 1;
                stopLeftExpansion = true;
              } else {
                rightExclusive = right;
              }
              break;
            }
            const rectangleJunctions = rectangleJunctionMask(
              variant,
              top,
              left,
              bottom,
              right,
              requiredJunctions,
            );
            const newEdges = rectangleEdges & ~coveredEdges;
            const newJunctions = rectangleJunctions & ~coveredJunctions;
            if (newEdges === 0n && newJunctions === 0n) continue;
            const candidate = {
              topRow: top,
              leftColumn: left,
              bottomRow: bottom,
              rightColumn: right,
            };
            if (
              best === null ||
              isBetterBlob(
                candidate,
                best,
                newEdges,
                newJunctions,
                bestEdges & ~coveredEdges,
                bestJunctions & ~coveredJunctions,
              )
            ) {
              best = candidate;
              bestEdges = rectangleEdges;
              bestJunctions = rectangleJunctions;
            }
          }
          if (stopLeftExpansion || rightExclusive === anchorRight) break;
        }
        if (stopLeftExpansion) break;
      }
    }
    if (best === null) throw new Error("uncoverable smooth feature");
    result.push(best);
    coveredEdges |= bestEdges;
    coveredJunctions |= bestJunctions;
  }
  return result;
}
