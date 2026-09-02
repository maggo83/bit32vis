// BitSquiggles dependency-free Dart core.
// Grug 2-Clause License: do what want; not sue grug.

import 'dart:math' as math;

const _checksumCharset = 'qpzry9x8gf2tvdw0s3jn54khce6mua7l';
const _masks = <int>[0xffffffff, 0xffffffffff];
const _increments = <int>[0x9e3779b9, 0xb97f4a7c15];
const _firstMultipliers = <int>[0x85ebca6b, 0xd7ed558ccd];
const _secondMultipliers = <int>[0xc2b2ae35, 0xfe1a85ec53];
const _firstShifts = <int>[16, 20];
const _secondShifts = <int>[13, 15];
const _thirdShifts = <int>[16, 20];

int _variantIndex(int width) => switch (width) {
      32 => 0,
      40 => 1,
      _ => throw ArgumentError.value(width, 'width', 'must be 32 or 40'),
    };

/// Return the selected variant's bijective mixed value.
int mix(int width, int input) {
  final index = _variantIndex(width);
  if (input < 0 || input > _masks[index]) {
    throw RangeError.range(input, 0, _masks[index], 'input');
  }
  final mask = BigInt.from(_masks[index]);
  var value = (BigInt.from(input) + BigInt.from(_increments[index])) & mask;
  value = ((value ^ (value >> _firstShifts[index])) *
          BigInt.from(_firstMultipliers[index])) &
      mask;
  value = ((value ^ (value >> _secondShifts[index])) *
          BigInt.from(_secondMultipliers[index])) &
      mask;
  return ((value ^ (value >> _thirdShifts[index])) & mask).toInt();
}

enum BitSquiggleStyle {
  standard('standard'),
  highContrast('high-contrast'),
  monochrome('monochrome'),
  blackAndWhite('black-and-white');

  const BitSquiggleStyle(this.label);

  final String label;
}

enum BitSquiggleMode {
  leftRight('A|'),
  topBottom('A-'),
  halfTurn('A+'),
  diagonalSlash('A/');

  const BitSquiggleMode(this.label);

  final String label;
}

final class BitSquiggleDimensions {
  const BitSquiggleDimensions({
    required this.rows,
    required this.columns,
    required this.edgeCount,
    required this.pixelWidth,
    required this.pixelHeight,
  });

  final int rows;
  final int columns;
  final int edgeCount;
  final int pixelWidth;
  final int pixelHeight;
}

final class Edge {
  const Edge(this.startRow, this.startColumn, this.endRow, this.endColumn);

  final int startRow;
  final int startColumn;
  final int endRow;
  final int endColumn;

  @override
  bool operator ==(Object other) =>
      other is Edge &&
      startRow == other.startRow &&
      startColumn == other.startColumn &&
      endRow == other.endRow &&
      endColumn == other.endColumn;

  @override
  int get hashCode => Object.hash(startRow, startColumn, endRow, endColumn);

  @override
  String toString() => 'Edge($startRow, $startColumn, $endRow, $endColumn)';
}

final class BitSquiggleColor {
  const BitSquiggleColor({
    required this.lightness,
    required this.chroma,
    required this.hue,
    required this.hex,
  });

  final double lightness;
  final double chroma;
  final double hue;
  final String hex;

  @override
  bool operator ==(Object other) =>
      other is BitSquiggleColor &&
      lightness == other.lightness &&
      chroma == other.chroma &&
      hue == other.hue &&
      hex == other.hex;

  @override
  int get hashCode => Object.hash(lightness, chroma, hue, hex);
}

final class VisualSpec {
  VisualSpec({
    required this.bitWidth,
    required this.input,
    required this.mixed,
    required List<int> connections,
    required List<List<int>> cells,
    required this.background,
    required this.foreground,
    required this.style,
    required this.preferredMode,
    required this.actualMode,
    required this.fallback,
    required this.luminanceIndex,
    required this.swapped,
  })  : connections = List.unmodifiable(connections),
        cells = List.unmodifiable(
          cells.map((row) => List<int>.unmodifiable(row)),
        );

  final int bitWidth;
  final int input;
  final int mixed;
  final List<int> connections;
  final List<List<int>> cells;
  final BitSquiggleColor background;
  final BitSquiggleColor foreground;
  final BitSquiggleStyle style;
  final BitSquiggleMode preferredMode;
  final BitSquiggleMode actualMode;
  final bool fallback;
  final int luminanceIndex;
  final bool swapped;
}

final class PixelGrid {
  PixelGrid({
    required this.bitWidth,
    required this.width,
    required this.height,
    required List<int> pixels,
    required this.background,
    required this.foreground,
    required this.style,
  }) : pixels = List.unmodifiable(pixels);

  final int bitWidth;
  final int width;
  final int height;
  final List<int> pixels;
  final BitSquiggleColor background;
  final BitSquiggleColor foreground;
  final BitSquiggleStyle style;
}

final class SmoothBlob {
  const SmoothBlob(
    this.topRow,
    this.leftColumn,
    this.bottomRow,
    this.rightColumn,
  );

  final int topRow;
  final int leftColumn;
  final int bottomRow;
  final int rightColumn;

  @override
  bool operator ==(Object other) =>
      other is SmoothBlob &&
      topRow == other.topRow &&
      leftColumn == other.leftColumn &&
      bottomRow == other.bottomRow &&
      rightColumn == other.rightColumn;

  @override
  int get hashCode => Object.hash(topRow, leftColumn, bottomRow, rightColumn);

  @override
  String toString() =>
      'SmoothBlob($topRow, $leftColumn, $bottomRow, $rightColumn)';
}

double _clamp(double value) => value.clamp(0.0, 1.0);
double _srgb(double value) {
  value = _clamp(value);
  return value <= .0031308
      ? 12.92 * value
      : 1.055 * math.pow(value, 1 / 2.4) - .055;
}

BitSquiggleColor _color(double lightness, double chroma, double hue) {
  final radians = hue * math.pi / 180,
      a = chroma * math.cos(radians),
      b = chroma * math.sin(radians),
      l = lightness + .3963377774 * a + .2158037573 * b,
      m = lightness - .1055613458 * a - .0638541728 * b,
      s = lightness - .0894841775 * a - 1.291485548 * b;
  String channel(num value) => (_srgb(value.toDouble()) * 255 + .5)
      .floor()
      .toRadixString(16)
      .padLeft(2, '0');
  final red = channel(
    4.0767416621 * l * l * l -
        3.3077115913 * m * m * m +
        .2309699292 * s * s * s,
  );
  final green = channel(
    -1.2684380046 * l * l * l +
        2.6097574011 * m * m * m -
        .3413193965 * s * s * s,
  );
  final blue = channel(
    -.0041960863 * l * l * l -
        .7034186147 * m * m * m +
        1.707614701 * s * s * s,
  );
  return BitSquiggleColor(
    lightness: lightness,
    chroma: chroma,
    hue: hue,
    hex: '#$red$green$blue',
  );
}

int _popcount(int value) {
  var count = 0;
  while (value != 0) {
    value &= value - 1;
    count++;
  }
  return count;
}

(BitSquiggleColor, BitSquiggleColor) _deriveColors(
  int mixed,
  int input,
  BitSquiggleStyle style,
) {
  final hue = ((mixed >> 12) & 15) * 22.5,
      chroma = .05 + ((mixed >> 8) & 15) * (.2 / 15),
      base = .5 + (mixed & 3) * (.2 / 3);
  var foregroundL = base,
      backgroundL = foregroundL - .5,
      foregroundC = chroma,
      backgroundC = chroma;
  if (style != BitSquiggleStyle.standard) {
    foregroundL = style == BitSquiggleStyle.blackAndWhite ? 1 : base + .3;
    backgroundL =
        style == BitSquiggleStyle.blackAndWhite ? 0 : foregroundL - .8;
    foregroundC = style == BitSquiggleStyle.monochrome ||
            style == BitSquiggleStyle.blackAndWhite
        ? 0
        : chroma + .1;
    backgroundC = foregroundC;
  }
  foregroundL = _clamp(foregroundL);
  backgroundL = _clamp(backgroundL);
  if (_popcount(input).isOdd) {
    final temporary = foregroundL;
    foregroundL = backgroundL;
    backgroundL = temporary;
  }
  return (
    _color(backgroundL, backgroundC, (hue + 180) % 360),
    _color(foregroundL, foregroundC, hue),
  );
}

const _templates32 = <List<String>>[
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
    'A B C D E',
    'F G H I J',
    'K L M N O',
    'P Q R S T',
    "K' L' M' N' O'",
    "F' G' H' I' J'",
    "A' B' C' D' E'",
  ],
  [
    'A B C D E',
    'F G H I J',
    'K L M N O',
    "P Q R Q' P'",
    "O' N' M' L' K'",
    "J' I' H' G' F'",
    "E' D' C' B' A'",
  ],
  [
    'A B C D E',
    'F G H I J',
    "K L M N I'",
    "O P Q M' H'",
    "R S P' L' G'",
    "T R' O' K' F'",
    "E' D' C' B' A'",
  ],
];

typedef _Transform = (int, int) Function(int row, int column);

final _transforms40 = <_Transform>[
  (row, column) => (row, 6 - column),
  (row, column) => (6 - row, column),
  (row, column) => (6 - row, 6 - column),
  (row, column) => (6 - column, 6 - row),
];

final class _ConnectionClass {
  _ConnectionClass(List<int> occurrences, {this.excluded = false})
      : occurrences = List.unmodifiable(occurrences);

  final List<int> occurrences;
  final bool excluded;
}

final class _ModeDefinition {
  _ModeDefinition(List<_ConnectionClass> classes)
      : classes = List.unmodifiable(classes),
        usable = List.unmodifiable(classes.where((entry) => !entry.excluded));

  final List<_ConnectionClass> classes;
  final List<_ConnectionClass> usable;
}

final class _Variant {
  _Variant({
    required this.width,
    required this.rows,
    required this.columns,
    required this.edges,
    required this.definitions,
    required this.markerIndexes,
  }) : dimensions = BitSquiggleDimensions(
          rows: rows,
          columns: columns,
          edgeCount: edges.length,
          pixelWidth: columns * 3 + 1,
          pixelHeight: rows * 3 + 1,
        );

  final int width;
  final int rows;
  final int columns;
  final List<Edge> edges;
  final List<_ModeDefinition> definitions;
  final List<int> markerIndexes;
  final BitSquiggleDimensions dimensions;

  int get payloadBits => width - 2;
}

List<Edge> _createEdges(int rows, int columns) {
  final result = <Edge>[];
  for (var row = 0; row < rows; row++) {
    for (var column = 0; column < columns; column++) {
      if (column + 1 < columns) {
        result.add(Edge(row, column, row, column + 1));
      }
      if (row + 1 < rows) {
        result.add(Edge(row, column, row + 1, column));
      }
    }
  }
  return List.unmodifiable(result);
}

_ModeDefinition _createCopiedDefinition(
  List<Edge> edges,
  int columns,
  List<String> templateRows,
) {
  final references = List<String>.filled(templateRows.length * columns, '');
  final sources = <String, int>{};
  for (var row = 0; row < templateRows.length; row++) {
    final tokens = templateRows[row].split(' ');
    for (var column = 0; column < columns; column++) {
      final token = tokens[column];
      final copied = token.endsWith("'");
      final name = copied ? token.substring(0, token.length - 1) : token;
      references[row * columns + column] = name;
      if (!copied) sources[name] = row * columns + column;
    }
  }

  final groups = <int, List<int>>{};
  for (var index = 0; index < edges.length; index++) {
    final edge = edges[index];
    final first =
        sources[references[edge.startRow * columns + edge.startColumn]]!;
    final second = sources[references[edge.endRow * columns + edge.endColumn]]!;
    final key = (first < second ? first : second) * 64 +
        (first > second ? first : second);
    (groups[key] ??= <int>[]).add(index);
  }
  final keys = groups.keys.toList()..sort();
  return _ModeDefinition(
    keys.map((key) => _ConnectionClass(groups[key]!)).toList(),
  );
}

int _compareCoordinates(List<int> first, List<int> second) {
  for (var index = 0; index < first.length; index++) {
    if (first[index] != second[index]) return first[index] - second[index];
  }
  return 0;
}

List<int> _edgeCoordinates(Edge edge) => [
      edge.startRow,
      edge.startColumn,
      edge.endRow,
      edge.endColumn,
    ];

List<int> _canonicalEdge(
  int firstRow,
  int firstColumn,
  int secondRow,
  int secondColumn,
) {
  final first = [firstRow, firstColumn];
  final second = [secondRow, secondColumn];
  return _compareCoordinates(first, second) < 0
      ? [...first, ...second]
      : [...second, ...first];
}

_ModeDefinition _createGeometricDefinition(
  List<Edge> edges,
  List<int> markerIndexes,
  _Transform transform,
) {
  final groups = <String, ({List<int> key, List<int> occurrences})>{};
  for (var index = 0; index < edges.length; index++) {
    final edge = edges[index];
    final first = transform(edge.startRow, edge.startColumn);
    final second = transform(edge.endRow, edge.endColumn);
    final original = _edgeCoordinates(edge);
    final transformed = _canonicalEdge(
      first.$1,
      first.$2,
      second.$1,
      second.$2,
    );
    final key = _compareCoordinates(original, transformed) <= 0
        ? original
        : transformed;
    final serialized = key.join(',');
    final group = groups[serialized];
    if (group == null) {
      groups[serialized] = (key: key, occurrences: [index]);
    } else {
      group.occurrences.add(index);
    }
  }
  final ordered = groups.values.toList()
    ..sort((first, second) => _compareCoordinates(first.key, second.key));
  return _ModeDefinition(
    ordered
        .map(
          (group) => _ConnectionClass(
            group.occurrences,
            excluded: group.occurrences.any(markerIndexes.contains),
          ),
        )
        .toList(),
  );
}

_Variant _createVariant32() {
  final edges = _createEdges(7, 5);
  return _Variant(
    width: 32,
    rows: 7,
    columns: 5,
    edges: edges,
    definitions: List.unmodifiable(
      _templates32.map(
        (template) => _createCopiedDefinition(edges, 5, template),
      ),
    ),
    markerIndexes: const [],
  );
}

_Variant _createVariant40() {
  final edges = _createEdges(7, 7);
  int indexOf(int startRow, int startColumn, int endRow, int endColumn) =>
      edges.indexOf(Edge(startRow, startColumn, endRow, endColumn));
  final markerIndexes = List<int>.unmodifiable([
    indexOf(2, 3, 3, 3),
    indexOf(3, 2, 3, 3),
    indexOf(3, 3, 3, 4),
    indexOf(3, 3, 4, 3),
  ]);
  return _Variant(
    width: 40,
    rows: 7,
    columns: 7,
    edges: edges,
    definitions: List.unmodifiable(
      _transforms40.map(
        (transform) =>
            _createGeometricDefinition(edges, markerIndexes, transform),
      ),
    ),
    markerIndexes: markerIndexes,
  );
}

final _variant32 = _createVariant32();
final _variant40 = _createVariant40();

_Variant _variantFor(int width) => switch (width) {
      32 => _variant32,
      40 => _variant40,
      _ => throw ArgumentError.value(width, 'width', 'must be 32 or 40'),
    };

/// Return the selected variant's immutable dimensions.
BitSquiggleDimensions dimensions(int width) => _variantFor(width).dimensions;

/// Return the selected variant's canonical edges in encoding order.
List<Edge> edges(int width) => _variantFor(width).edges;

int freeConnectionCount(int width, BitSquiggleMode mode) =>
    _variantFor(width).definitions[mode.index].classes.length;

int usableConnectionCount(int width, BitSquiggleMode mode) =>
    _variantFor(width).definitions[mode.index].usable.length;

void _validateConnections(_Variant variant, List<int> connections) {
  if (connections.length != variant.edges.length) {
    throw ArgumentError.value(
      connections.length,
      'connections',
      'must contain ${variant.edges.length} entries',
    );
  }
  if (connections.any((value) => value != 0 && value != 1)) {
    throw ArgumentError.value(
      connections,
      'connections',
      'must contain only zeroes and ones',
    );
  }
}

bool _matchesDefinition(List<int> connections, _ModeDefinition definition) {
  for (final connectionClass in definition.classes) {
    final expected = connections[connectionClass.occurrences.first];
    if (connectionClass.excluded && expected != 0) return false;
    for (final edgeIndex in connectionClass.occurrences.skip(1)) {
      if (connections[edgeIndex] != expected) return false;
    }
  }
  return true;
}

bool matchesMode(int width, List<int> connections, BitSquiggleMode mode) {
  final variant = _variantFor(width);
  _validateConnections(variant, connections);
  return _matchesDefinition(connections, variant.definitions[mode.index]);
}

int _valueBit(BigInt value, int bitWidth, int classIndex) =>
    ((value >> (bitWidth - 1 - classIndex % bitWidth)) & BigInt.one).toInt();

List<int> _encode(_Variant variant, int mixed, int modeIndex) {
  final definition = variant.definitions[modeIndex];
  final bitWidth = modeIndex == 0 ? variant.width : variant.payloadBits;
  final value = BigInt.from(
    modeIndex == 0 ? mixed : mixed & ((1 << variant.payloadBits) - 1),
  );
  final result = List<int>.filled(variant.edges.length, 0);
  for (var classIndex = 0;
      classIndex < definition.usable.length;
      classIndex++) {
    final bit = _valueBit(value, bitWidth, classIndex);
    for (final edgeIndex in definition.usable[classIndex].occurrences) {
      result[edgeIndex] = bit;
    }
  }
  return result;
}

bool _conflictsWithEarlierMode(
  _Variant variant,
  List<int> connections,
  int modeIndex,
) {
  for (var earlier = 0; earlier < modeIndex; earlier++) {
    if (_matchesDefinition(connections, variant.definitions[earlier])) {
      return true;
    }
  }
  return false;
}

bool _hasCapacity(_Variant variant, int mixed, int modeIndex) =>
    variant.width != 32 || modeIndex != 2 || mixed & 1 == 0;

void _applyMarker(_Variant variant, List<int> connections) {
  if (variant.markerIndexes.isEmpty) return;
  for (final index in variant.markerIndexes) {
    connections[index] = 0;
  }
  connections[variant.markerIndexes.first] = 1;
}

List<List<int>> _activeCells(_Variant variant, List<int> connections) {
  final result = List.generate(
    variant.rows,
    (_) => List<int>.filled(variant.columns, 0),
  );
  for (var index = 0; index < variant.edges.length; index++) {
    if (connections[index] == 0) continue;
    final edge = variant.edges[index];
    result[edge.startRow][edge.startColumn] = 1;
    result[edge.endRow][edge.endColumn] = 1;
  }
  return result;
}

/// Derive the selected variant's canonical immutable visual specification.
VisualSpec spec(
  int width,
  int input, [
  BitSquiggleStyle style = BitSquiggleStyle.standard,
]) {
  final variant = _variantFor(width);
  final mixed = mix(width, input);
  final preferredModeIndex = mixed >> variant.payloadBits;
  final candidate = _encode(variant, mixed, preferredModeIndex);
  final fallback = preferredModeIndex != 0 &&
      (!_hasCapacity(variant, mixed, preferredModeIndex) ||
          _conflictsWithEarlierMode(variant, candidate, preferredModeIndex));
  final actualModeIndex = fallback ? 0 : preferredModeIndex;
  final connections = fallback ? _encode(variant, mixed, 0) : candidate;
  _applyMarker(variant, connections);
  final colors = _deriveColors(mixed, input, style);
  return VisualSpec(
    bitWidth: width,
    input: input,
    mixed: mixed,
    connections: connections,
    cells: _activeCells(variant, connections),
    background: colors.$1,
    foreground: colors.$2,
    style: style,
    preferredMode: BitSquiggleMode.values[preferredModeIndex],
    actualMode: BitSquiggleMode.values[actualModeIndex],
    fallback: fallback,
    luminanceIndex: mixed & 3,
    swapped: _popcount(input).isOdd,
  );
}

int _horizontalEdgeIndex(_Variant variant, int row, int column) =>
    row == variant.rows - 1
        ? row * (2 * variant.columns - 1) + column
        : row * (2 * variant.columns - 1) + 2 * column;

int _verticalEdgeIndex(_Variant variant, int row, int column) =>
    row * (2 * variant.columns - 1) +
    (column == variant.columns - 1 ? 2 * variant.columns - 2 : 2 * column + 1);

List<int> _generatePixels(_Variant variant, List<int> connections) {
  final raster = List<int>.filled(
    variant.dimensions.pixelWidth * variant.dimensions.pixelHeight,
    0,
  );
  final cells = _activeCells(variant, connections);
  void setPixel(int x, int y) =>
      raster[y * variant.dimensions.pixelWidth + x] = 1;
  for (var row = 0; row < variant.rows; row++) {
    for (var column = 0; column < variant.columns; column++) {
      if (cells[row][column] == 0) continue;
      final x = 1 + column * 3;
      final y = 1 + row * 3;
      setPixel(x, y);
      setPixel(x + 1, y);
      setPixel(x, y + 1);
      setPixel(x + 1, y + 1);
    }
  }
  for (var index = 0; index < variant.edges.length; index++) {
    if (connections[index] == 0) continue;
    final edge = variant.edges[index];
    final x = 1 + edge.startColumn * 3;
    final y = 1 + edge.startRow * 3;
    if (edge.startRow == edge.endRow) {
      setPixel(x + 2, y);
      setPixel(x + 2, y + 1);
    } else {
      setPixel(x, y + 2);
      setPixel(x + 1, y + 2);
    }
  }
  for (var row = 0; row < variant.rows - 1; row++) {
    for (var column = 0; column < variant.columns - 1; column++) {
      if (connections[_horizontalEdgeIndex(variant, row, column)] == 1 &&
          connections[_horizontalEdgeIndex(variant, row + 1, column)] == 1 &&
          connections[_verticalEdgeIndex(variant, row, column)] == 1 &&
          connections[_verticalEdgeIndex(variant, row, column + 1)] == 1) {
        setPixel(3 + column * 3, 3 + row * 3);
      }
    }
  }
  return raster;
}

/// Derive the selected variant's exact bordered binary raster and colors.
PixelGrid pixels(
  int width,
  int input, [
  BitSquiggleStyle style = BitSquiggleStyle.standard,
]) {
  final variant = _variantFor(width);
  final visual = spec(width, input, style);
  return PixelGrid(
    bitWidth: width,
    width: variant.dimensions.pixelWidth,
    height: variant.dimensions.pixelHeight,
    pixels: _generatePixels(variant, visual.connections),
    background: visual.background,
    foreground: visual.foreground,
    style: style,
  );
}

/// Convert exactly eight BIP380 checksum characters to a 40-bit input.
int bip380ChecksumInput(String checksum) {
  if (checksum.length != 8) {
    throw ArgumentError.value(
      checksum,
      'checksum',
      'must contain exactly eight characters',
    );
  }
  var result = 0;
  for (final character in checksum.split('')) {
    final value = _checksumCharset.indexOf(character);
    if (value < 0) {
      throw ArgumentError.value(
        checksum,
        'checksum',
        'contains an invalid BIP380 checksum character',
      );
    }
    result = result << 5 | value;
  }
  return result;
}

BigInt _horizontalEdgeBit(_Variant variant, int row, int column) =>
    BigInt.one << _horizontalEdgeIndex(variant, row, column);

BigInt _verticalEdgeBit(_Variant variant, int row, int column) =>
    BigInt.one << _verticalEdgeIndex(variant, row, column);

BigInt _junctionBit(_Variant variant, int row, int column) =>
    BigInt.one << (row * (variant.columns - 1) + column);

BigInt _requiredEdgeMask(_Variant variant, List<int> connections) {
  _validateConnections(variant, connections);
  var result = BigInt.zero;
  for (var index = 0; index < connections.length; index++) {
    if (connections[index] == 1) result |= BigInt.one << index;
  }
  return result;
}

BigInt _connectedRectangleEdgeMask(
  _Variant variant,
  int top,
  int left,
  int bottom,
  int right,
  BigInt requiredEdges,
) {
  var result = BigInt.zero;
  for (var row = top; row <= bottom; row++) {
    for (var column = left; column <= right; column++) {
      if (column < right) {
        final edge = _horizontalEdgeBit(variant, row, column);
        if (requiredEdges & edge == BigInt.zero) return BigInt.zero;
        result |= edge;
      }
      if (row < bottom) {
        final edge = _verticalEdgeBit(variant, row, column);
        if (requiredEdges & edge == BigInt.zero) return BigInt.zero;
        result |= edge;
      }
    }
  }
  return result;
}

BigInt _requiredJunctionMask(_Variant variant, BigInt requiredEdges) {
  var result = BigInt.zero;
  for (var row = 0; row < variant.rows - 1; row++) {
    for (var column = 0; column < variant.columns - 1; column++) {
      if (_connectedRectangleEdgeMask(
            variant,
            row,
            column,
            row + 1,
            column + 1,
            requiredEdges,
          ) !=
          BigInt.zero) {
        result |= _junctionBit(variant, row, column);
      }
    }
  }
  return result;
}

BigInt _rectangleJunctionMask(
  _Variant variant,
  int top,
  int left,
  int bottom,
  int right,
  BigInt requiredJunctions,
) {
  var result = BigInt.zero;
  for (var row = top; row < bottom; row++) {
    for (var column = left; column < right; column++) {
      final junction = _junctionBit(variant, row, column);
      if (requiredJunctions & junction != BigInt.zero) result |= junction;
    }
  }
  return result;
}

int _bigPopcount(BigInt value) {
  var count = 0;
  while (value != BigInt.zero) {
    value &= value - BigInt.one;
    count++;
  }
  return count;
}

bool _isBetterBlob(
  SmoothBlob candidate,
  SmoothBlob best,
  BigInt newEdges,
  BigInt newJunctions,
  BigInt bestNewEdges,
  BigInt bestNewJunctions,
) {
  final junctionCount = _bigPopcount(newJunctions);
  final bestJunctionCount = _bigPopcount(bestNewJunctions);
  if (junctionCount != bestJunctionCount) {
    return junctionCount > bestJunctionCount;
  }
  final edgeCount = _bigPopcount(newEdges);
  final bestEdgeCount = _bigPopcount(bestNewEdges);
  if (edgeCount != bestEdgeCount) return edgeCount > bestEdgeCount;
  final area = (candidate.bottomRow - candidate.topRow + 1) *
      (candidate.rightColumn - candidate.leftColumn + 1);
  final bestArea = (best.bottomRow - best.topRow + 1) *
      (best.rightColumn - best.leftColumn + 1);
  if (area != bestArea) return area < bestArea;
  if (candidate.topRow != best.topRow) {
    return candidate.topRow < best.topRow;
  }
  if (candidate.leftColumn != best.leftColumn) {
    return candidate.leftColumn < best.leftColumn;
  }
  if (candidate.bottomRow != best.bottomRow) {
    return candidate.bottomRow < best.bottomRow;
  }
  return candidate.rightColumn < best.rightColumn;
}

int _firstUncovered(BigInt required, BigInt covered, int count) {
  final remaining = required & ~covered;
  for (var index = 0; index < count; index++) {
    if (remaining & (BigInt.one << index) != BigInt.zero) return index;
  }
  return -1;
}

/// Return the selected variant's ordered canonical smooth rectangles.
List<SmoothBlob> smoothBlobs(int width, List<int> connections) {
  final variant = _variantFor(width);
  final requiredEdges = _requiredEdgeMask(variant, connections);
  final requiredJunctions = _requiredJunctionMask(variant, requiredEdges);
  var coveredEdges = BigInt.zero;
  var coveredJunctions = BigInt.zero;
  final result = <SmoothBlob>[];

  while (
      coveredEdges != requiredEdges || coveredJunctions != requiredJunctions) {
    final anchorEdge = _firstUncovered(
      requiredEdges,
      coveredEdges,
      variant.edges.length,
    );
    final anchorJunction = anchorEdge < 0
        ? _firstUncovered(
            requiredJunctions,
            coveredJunctions,
            (variant.rows - 1) * (variant.columns - 1),
          )
        : -1;
    late int anchorTop;
    late int anchorLeft;
    late int anchorBottom;
    late int anchorRight;
    if (anchorEdge >= 0) {
      final edge = variant.edges[anchorEdge];
      anchorTop = edge.startRow;
      anchorLeft = edge.startColumn;
      anchorBottom = edge.endRow;
      anchorRight = edge.endColumn;
    } else {
      anchorTop = anchorJunction ~/ (variant.columns - 1);
      anchorLeft = anchorJunction % (variant.columns - 1);
      anchorBottom = anchorTop + 1;
      anchorRight = anchorLeft + 1;
    }

    SmoothBlob? best;
    var bestEdges = BigInt.zero;
    var bestJunctions = BigInt.zero;
    var leftInclusive = 0;
    for (var top = anchorTop; top >= 0; top--) {
      for (var left = anchorLeft; left >= leftInclusive; left--) {
        var rightExclusive = variant.columns;
        var stopLeftExpansion = false;
        for (var bottom = anchorBottom; bottom < variant.rows; bottom++) {
          for (var right = anchorRight; right < rightExclusive; right++) {
            final rectangleEdges = _connectedRectangleEdgeMask(
              variant,
              top,
              left,
              bottom,
              right,
              requiredEdges,
            );
            if (rectangleEdges == BigInt.zero) {
              if (bottom == anchorBottom && right == anchorRight) {
                leftInclusive = left + 1;
                stopLeftExpansion = true;
              } else {
                rightExclusive = right;
              }
              break;
            }
            final rectangleJunctions = _rectangleJunctionMask(
              variant,
              top,
              left,
              bottom,
              right,
              requiredJunctions,
            );
            final newEdges = rectangleEdges & ~coveredEdges;
            final newJunctions = rectangleJunctions & ~coveredJunctions;
            if (newEdges == BigInt.zero && newJunctions == BigInt.zero) {
              continue;
            }
            final candidate = SmoothBlob(top, left, bottom, right);
            if (best == null ||
                _isBetterBlob(
                  candidate,
                  best,
                  newEdges,
                  newJunctions,
                  bestEdges & ~coveredEdges,
                  bestJunctions & ~coveredJunctions,
                )) {
              best = candidate;
              bestEdges = rectangleEdges;
              bestJunctions = rectangleJunctions;
            }
          }
          if (stopLeftExpansion || rightExclusive == anchorRight) break;
        }
        if (stopLeftExpansion) break;
      }
    }
    if (best == null) throw StateError('uncoverable smooth feature');
    result.add(best);
    coveredEdges |= bestEdges;
    coveredJunctions |= bestJunctions;
  }
  return List.unmodifiable(result);
}
