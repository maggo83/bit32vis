// Dependency-free executable BitSquiggles conformance tests.
// Grug 2-Clause License: do what want; not sue grug.

import 'dart:convert';
import 'dart:io';

import 'bitsquiggles_core.dart' as bit;

var _checks = 0;

void check(bool condition, String message) {
  _checks++;
  if (!condition) throw StateError('check failed: $message');
}

void equal(Object? actual, Object? expected, String message) {
  _checks++;
  if (actual != expected) {
    throw StateError('check failed: $message ($actual != $expected)');
  }
}

void listEqual<T>(List<T> actual, List<T> expected, String message) {
  _checks++;
  if (actual.length != expected.length) {
    throw StateError(
      'check failed: $message (${actual.length} != ${expected.length})',
    );
  }
  for (var index = 0; index < actual.length; index++) {
    if (actual[index] != expected[index]) {
      throw StateError(
        'check failed: $message at $index '
        '(${actual[index]} != ${expected[index]})',
      );
    }
  }
}

void nestedListEqual(
  List<List<int>> actual,
  List<List<int>> expected,
  String message,
) {
  _checks++;
  if (actual.length != expected.length) {
    throw StateError('check failed: $message row count');
  }
  for (var row = 0; row < actual.length; row++) {
    if (actual[row].length != expected[row].length) {
      throw StateError('check failed: $message row $row length');
    }
    for (var column = 0; column < actual[row].length; column++) {
      if (actual[row][column] != expected[row][column]) {
        throw StateError('check failed: $message at $row,$column');
      }
    }
  }
}

void expectFailure(void Function() operation, String message) {
  _checks++;
  try {
    operation();
  } on ArgumentError {
    return;
  } on UnsupportedError {
    return;
  }
  throw StateError('expected failure: $message');
}

String bits(Iterable<int> values) => values.join();

String hex(int value, int width) =>
    value.toRadixString(16).padLeft(width ~/ 4, '0');

final _variants = <({int width, String fixturePath, int maxBlobs})>[
  (width: 32, fixturePath: '../fixtures/v1-32.json', maxBlobs: 82),
  (width: 40, fixturePath: '../fixtures/v1-40.json', maxBlobs: 120),
];

Map<bit.Edge, int> edgeIndexMap(List<bit.Edge> edges) => {
      for (var index = 0; index < edges.length; index++) edges[index]: index,
    };

List<int> markerIndexes(List<bit.Edge> edges) {
  final indexes = edgeIndexMap(edges);
  return [
    indexes[const bit.Edge(2, 3, 3, 3)]!,
    indexes[const bit.Edge(3, 2, 3, 3)]!,
    indexes[const bit.Edge(3, 3, 3, 4)]!,
    indexes[const bit.Edge(3, 3, 4, 3)]!,
  ];
}

List<int> clearMarker(List<int> connections, List<bit.Edge> edges) {
  final result = connections.toList();
  for (final index in markerIndexes(edges)) {
    result[index] = 0;
  }
  return result;
}

List<int> rotateConnections(List<int> connections, List<bit.Edge> edges) {
  final indexes = edgeIndexMap(edges);
  final result = List<int>.filled(connections.length, 0);
  for (var index = 0; index < edges.length; index++) {
    if (connections[index] == 0) continue;
    final edge = edges[index];
    final first = (edge.startColumn, 6 - edge.startRow);
    final second = (edge.endColumn, 6 - edge.endRow);
    final ordered =
        first.$1 < second.$1 || first.$1 == second.$1 && first.$2 < second.$2
            ? (first, second)
            : (second, first);
    result[indexes[bit.Edge(
      ordered.$1.$1,
      ordered.$1.$2,
      ordered.$2.$1,
      ordered.$2.$2,
    )]!] = 1;
  }
  return result;
}

List<List<int>> expectedCells(
  List<int> connections,
  List<bit.Edge> edges,
  int rows,
  int columns,
) {
  final result = List.generate(rows, (_) => List<int>.filled(columns, 0));
  for (var index = 0; index < edges.length; index++) {
    if (connections[index] == 0) continue;
    final edge = edges[index];
    result[edge.startRow][edge.startColumn] = 1;
    result[edge.endRow][edge.endColumn] = 1;
  }
  return result;
}

void verifyRaster(
  bit.PixelGrid grid,
  List<int> connections,
  List<bit.Edge> edges,
) {
  int at(int x, int y) => grid.pixels[y * grid.width + x];
  for (var x = 0; x < grid.width; x++) {
    equal(at(x, 0), 0, 'top raster border');
    equal(at(x, grid.height - 1), 0, 'bottom raster border');
  }
  for (var y = 0; y < grid.height; y++) {
    equal(at(0, y), 0, 'left raster border');
    equal(at(grid.width - 1, y), 0, 'right raster border');
  }
  for (var index = 0; index < edges.length; index++) {
    final edge = edges[index];
    final x = 1 + edge.startColumn * 3;
    final y = 1 + edge.startRow * 3;
    final bridge = edge.startRow == edge.endRow ? at(x + 2, y) : at(x, y + 2);
    equal(bridge, connections[index], 'bridge pixel recovers edge');
  }
}

int bitCount(BigInt value) {
  var count = 0;
  while (value != BigInt.zero) {
    value &= value - BigInt.one;
    count++;
  }
  return count;
}

void verifyDiffusion(int width) {
  final base = bit.mix(width, 0);
  var changedBits = 0;
  for (var bitIndex = 0; bitIndex < width; bitIndex++) {
    final difference =
        BigInt.from(base) ^ BigInt.from(bit.mix(width, 1 << bitIndex));
    check(difference != BigInt.zero, '$width-bit one-bit neighbor changes');
    changedBits += bitCount(difference);
  }
  check(
    changedBits / width >= width / 3,
    '$width-bit mixer has useful one-bit diffusion',
  );
}

List<int> connectionMask(List<bit.Edge> edges, List<bit.Edge> selected) {
  final indexes = edgeIndexMap(edges);
  final result = List<int>.filled(edges.length, 0);
  for (final edge in selected) {
    result[indexes[edge]!] = 1;
  }
  return result;
}

void verifyBlobCoverage(
  ({int width, String fixturePath, int maxBlobs}) variant,
  List<int> connections,
) {
  final dimensions = bit.dimensions(variant.width);
  final edges = bit.edges(variant.width);
  final indexes = edgeIndexMap(edges);
  final active = expectedCells(
    connections,
    edges,
    dimensions.rows,
    dimensions.columns,
  );
  final blobs = bit.smoothBlobs(variant.width, connections);
  check(blobs.length <= variant.maxBlobs, 'bounded smooth blob count');
  listEqual(
    blobs,
    bit.smoothBlobs(variant.width, connections),
    'smooth blobs are deterministic',
  );

  final covered = List<int>.filled(edges.length, 0);
  for (final blob in blobs) {
    check(
      blob.topRow >= 0 &&
          blob.leftColumn >= 0 &&
          blob.bottomRow < dimensions.rows &&
          blob.rightColumn < dimensions.columns &&
          blob.topRow <= blob.bottomRow &&
          blob.leftColumn <= blob.rightColumn,
      'smooth blob bounds',
    );
    check(
      blob.topRow < blob.bottomRow || blob.leftColumn < blob.rightColumn,
      'smooth blob has an internal edge',
    );
    for (var row = blob.topRow; row <= blob.bottomRow; row++) {
      for (var column = blob.leftColumn; column <= blob.rightColumn; column++) {
        equal(active[row][column], 1, 'smooth blob contains active cells');
        if (column < blob.rightColumn) {
          final index = indexes[bit.Edge(row, column, row, column + 1)]!;
          equal(connections[index], 1, 'blob preserves horizontal edge');
          covered[index] = 1;
        }
        if (row < blob.bottomRow) {
          final index = indexes[bit.Edge(row, column, row + 1, column)]!;
          equal(connections[index], 1, 'blob preserves vertical edge');
          covered[index] = 1;
        }
      }
    }
  }
  listEqual(covered, connections, 'smooth blobs cover every selected edge');

  for (var row = 0; row < dimensions.rows - 1; row++) {
    for (var column = 0; column < dimensions.columns - 1; column++) {
      final perimeter = [
        indexes[bit.Edge(row, column, row, column + 1)]!,
        indexes[bit.Edge(row + 1, column, row + 1, column + 1)]!,
        indexes[bit.Edge(row, column, row + 1, column)]!,
        indexes[bit.Edge(row, column + 1, row + 1, column + 1)]!,
      ];
      if (!perimeter.every((index) => connections[index] == 1)) continue;
      check(
        blobs.any(
          (blob) =>
              blob.topRow <= row &&
              blob.leftColumn <= column &&
              blob.bottomRow >= row + 1 &&
              blob.rightColumn >= column + 1,
        ),
        'smooth blobs cover every required junction',
      );
    }
  }
}

void verifyCanonicalEdges(
  ({int width, String fixturePath, int maxBlobs}) variant,
) {
  final dimensions = bit.dimensions(variant.width);
  final expected = <bit.Edge>[];
  for (var row = 0; row < dimensions.rows; row++) {
    for (var column = 0; column < dimensions.columns; column++) {
      if (column + 1 < dimensions.columns) {
        expected.add(bit.Edge(row, column, row, column + 1));
      }
      if (row + 1 < dimensions.rows) {
        expected.add(bit.Edge(row, column, row + 1, column));
      }
    }
  }
  listEqual(
    bit.edges(variant.width),
    expected,
    '${variant.width}-bit canonical edge order',
  );
}

({Map<String, dynamic> fixture, Set<String> masks}) verifyFixture(
  ({int width, String fixturePath, int maxBlobs}) variant,
) {
  final fixture = jsonDecode(
    File.fromUri(Platform.script.resolve(variant.fixturePath))
        .readAsStringSync(),
  ) as Map<String, dynamic>;
  equal(fixture['schema'], 'bitsquiggles-conformance', 'fixture schema');
  equal(fixture['version'], 1, 'fixture version');
  final fixtureDimensions = fixture['dimensions'] as Map<String, dynamic>;
  final dimensions = bit.dimensions(variant.width);
  equal(dimensions.rows, fixtureDimensions['rows'], 'fixture rows');
  equal(dimensions.columns, fixtureDimensions['columns'], 'fixture columns');
  equal(dimensions.edgeCount, fixtureDimensions['edges'], 'fixture edges');
  equal(
    dimensions.pixelWidth,
    fixtureDimensions['pixelWidth'],
    'fixture pixel width',
  );
  equal(
    dimensions.pixelHeight,
    fixtureDimensions['pixelHeight'],
    'fixture pixel height',
  );

  final vectors = fixture['vectors'] as List<dynamic>;
  final masks = <String>{};
  final preferredModes = <bit.BitSquiggleMode>{};
  var sawFallback = false;
  for (final raw in vectors) {
    final vector = raw as Map<String, dynamic>;
    final input = int.parse(vector['input'] as String, radix: 16);
    final visual = bit.spec(variant.width, input);
    final grid = bit.pixels(variant.width, input);
    final connectionString = bits(visual.connections);
    equal(hex(visual.mixed, variant.width), vector['mixed'], 'fixture mixed');
    equal(connectionString, vector['connections'], 'fixture connections');
    equal(bits(grid.pixels), vector['pixels'], 'fixture pixels');
    equal(
      visual.preferredMode.label,
      vector['preferredMode'],
      'fixture preferred mode',
    );
    equal(
      visual.actualMode.label,
      vector['actualMode'],
      'fixture actual mode',
    );
    equal(visual.fallback, vector['fallback'], 'fixture fallback');
    nestedListEqual(
      visual.cells,
      expectedCells(
        visual.connections,
        bit.edges(variant.width),
        dimensions.rows,
        dimensions.columns,
      ),
      'active cells derive from selected edges',
    );
    check(masks.add(connectionString), 'sampled masks are unique');
    preferredModes.add(visual.preferredMode);
    sawFallback |= visual.fallback;

    final dataConnections = variant.width == 40
        ? clearMarker(visual.connections, bit.edges(40))
        : visual.connections;
    check(
      bit.matchesMode(variant.width, dataConnections, visual.actualMode),
      'actual mode membership',
    );
    for (var index = 0; index < visual.actualMode.index; index++) {
      check(
        !bit.matchesMode(
          variant.width,
          dataConnections,
          bit.BitSquiggleMode.values[index],
        ),
        'accepted mode excludes earlier families',
      );
    }

    final fixtureStyles = vector['styles'] as Map<String, dynamic>;
    for (final style in bit.BitSquiggleStyle.values) {
      final styled = bit.spec(variant.width, input, style);
      final colors = fixtureStyles[style.label] as Map<String, dynamic>;
      equal(styled.background.hex, colors['background'], 'fixture background');
      equal(styled.foreground.hex, colors['foreground'], 'fixture foreground');
      listEqual(
        styled.connections,
        visual.connections,
        'style preserves geometry',
      );
    }
  }
  equal(preferredModes.length, 4, 'all preferred modes observed');
  check(sawFallback, '${variant.width}-bit fallback observed');

  for (final raw in vectors.take(32)) {
    final vector = raw as Map<String, dynamic>;
    final input = int.parse(vector['input'] as String, radix: 16);
    final visual = bit.spec(variant.width, input);
    verifyRaster(
      bit.pixels(variant.width, input),
      visual.connections,
      bit.edges(variant.width),
    );
  }
  for (final raw in vectors.take(200)) {
    final vector = raw as Map<String, dynamic>;
    verifyBlobCoverage(
      variant,
      bit
          .spec(
            variant.width,
            int.parse(vector['input'] as String, radix: 16),
          )
          .connections,
    );
  }
  return (fixture: fixture, masks: masks);
}

void testSmoothRules() {
  for (final variant in _variants) {
    final edges = bit.edges(variant.width);
    listEqual(
      bit.smoothBlobs(variant.width, List.filled(edges.length, 0)),
      const [],
      'empty smooth mask',
    );
    listEqual(
      bit.smoothBlobs(
        variant.width,
        connectionMask(edges, [const bit.Edge(0, 0, 0, 1)]),
      ),
      const [bit.SmoothBlob(0, 0, 0, 1)],
      'single-edge blob',
    );
    listEqual(
      bit.smoothBlobs(
        variant.width,
        connectionMask(edges, [
          const bit.Edge(0, 0, 0, 1),
          const bit.Edge(0, 1, 0, 2),
        ]),
      ),
      const [bit.SmoothBlob(0, 0, 0, 2)],
      'edge-count priority extends a connected row',
    );
    final squareWithTail = connectionMask(edges, [
      const bit.Edge(0, 0, 0, 1),
      const bit.Edge(0, 1, 0, 2),
      const bit.Edge(0, 2, 0, 3),
      const bit.Edge(0, 3, 0, 4),
      const bit.Edge(1, 0, 1, 1),
      const bit.Edge(0, 0, 1, 0),
      const bit.Edge(0, 1, 1, 1),
    ]);
    listEqual(
      bit.smoothBlobs(variant.width, squareWithTail),
      const [bit.SmoothBlob(0, 0, 1, 1), bit.SmoothBlob(0, 1, 0, 4)],
      'junction priority then smaller-area tie-break',
    );
    verifyBlobCoverage(variant, squareWithTail);
    listEqual(
      bit.smoothBlobs(variant.width, List.filled(edges.length, 1)),
      [bit.SmoothBlob(0, 0, 6, bit.dimensions(variant.width).columns - 1)],
      'complete-grid blob',
    );
  }
}

void testValidationAndOwnership() {
  expectFailure(() => bit.mix(33, 0), 'unsupported width');
  expectFailure(() => bit.mix(32, -1), 'negative 32-bit input');
  expectFailure(() => bit.spec(32, 0x100000000), 'oversized 32-bit input');
  expectFailure(() => bit.spec(40, 0x10000000000), 'oversized 40-bit input');
  expectFailure(
    () => bit.matchesMode(
      32,
      List.filled(57, 0),
      bit.BitSquiggleMode.leftRight,
    ),
    'short connection mask',
  );
  expectFailure(
    () => bit.smoothBlobs(40, List.filled(84, 2)),
    'nonbinary connection mask',
  );
  for (final checksum in [
    '89f8spx',
    '89f8spxmq',
    '89F8spxm',
    '89f8#pxm',
    'iiiiiiii',
  ]) {
    expectFailure(
      () => bit.bip380ChecksumInput(checksum),
      'invalid BIP380 checksum',
    );
  }

  final firstVisual = bit.spec(32, 1);
  final secondVisual = bit.spec(32, 1);
  check(
    !identical(firstVisual.connections, secondVisual.connections),
    'spec returns a fresh connection list',
  );
  check(
    !identical(firstVisual.cells, secondVisual.cells),
    'spec returns fresh cell rows',
  );
  final firstGrid = bit.pixels(40, 1);
  final secondGrid = bit.pixels(40, 1);
  check(
    !identical(firstGrid.pixels, secondGrid.pixels),
    'pixels returns a fresh raster',
  );
  expectFailure(
    () => firstVisual.connections[0] = 1,
    'connections are immutable',
  );
  expectFailure(() => firstVisual.cells[0][0] = 1, 'cells are immutable');
  expectFailure(() => firstGrid.pixels[0] = 1, 'pixels are immutable');
  expectFailure(
    () => bit.edges(32).add(const bit.Edge(0, 0, 0, 1)),
    'edges are immutable',
  );
}

void main() {
  listEqual(
    bit.BitSquiggleMode.values
        .map((mode) => bit.freeConnectionCount(32, mode))
        .toList(),
    const [32, 31, 29, 33],
    '32-bit complete class counts',
  );
  listEqual(
    bit.BitSquiggleMode.values
        .map((mode) => bit.usableConnectionCount(32, mode))
        .toList(),
    const [32, 31, 29, 33],
    '32-bit usable class counts',
  );
  listEqual(
    bit.BitSquiggleMode.values
        .map((mode) => bit.freeConnectionCount(40, mode))
        .toList(),
    const [45, 45, 42, 42],
    '40-bit complete class counts',
  );
  listEqual(
    bit.BitSquiggleMode.values
        .map((mode) => bit.usableConnectionCount(40, mode))
        .toList(),
    const [42, 42, 40, 40],
    '40-bit usable class counts',
  );
  for (final variant in _variants) {
    verifyCanonicalEdges(variant);
  }

  equal(bit.mix(32, 0x89abcdef), 0x47ac5876, '32-bit golden mixer');
  equal(bit.mix(40, 0x39527804db), 0x34b1a077c8, '40-bit golden mixer');
  verifyDiffusion(32);
  verifyDiffusion(40);

  final results = _variants.map(verifyFixture).toList();
  final edges40 = bit.edges(40);
  final marker = markerIndexes(edges40);
  final vectors40 = results[1].fixture['vectors'] as List<dynamic>;
  for (final raw in vectors40) {
    final vector = raw as Map<String, dynamic>;
    final visual = bit.spec(
      40,
      int.parse(vector['input'] as String, radix: 16),
    );
    listEqual(
      marker.map((index) => visual.connections[index]).toList(),
      const [1, 0, 0, 0],
      '40-bit center marker',
    );
    var rotated = visual.connections;
    for (var turn = 1; turn < 4; turn++) {
      rotated = rotateConnections(rotated, edges40);
      check(
        !results[1].masks.contains(bits(rotated)),
        'rotated 40-bit mask is outside sampled valid masks',
      );
    }
  }

  for (final input in [0xa7912def7b, 0x08a1a65b0c, 0x500181b841]) {
    final visual = bit.spec(40, input);
    check(visual.fallback, 'targeted 40-bit overlap fallback');
    equal(
      visual.actualMode,
      bit.BitSquiggleMode.leftRight,
      '40-bit fallback uses default mode',
    );
  }

  equal(bit.bip380ChecksumInput('qqqqqqqq'), 0, 'zero checksum');
  equal(
    bit.bip380ChecksumInput('89f8spxm'),
    0x39527804db,
    'golden checksum',
  );
  equal(
    bit.bip380ChecksumInput('llllllll'),
    0xffffffffff,
    'maximum checksum',
  );
  testSmoothRules();
  testValidationAndOwnership();

  stdout.writeln('BitSquiggles Dart core tests passed ($_checks checks)');
}
