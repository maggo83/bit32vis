// Dependency-free executable BitSquiggle32 conformance tests.
// Grug 2-Clause License: do what want; not sue grug.

import 'dart:convert';
import 'dart:io';

import 'bitsquiggle32.dart' as bit;

var _checks = 0;

void check(bool condition, String message) {
  _checks++;
  if (!condition) throw StateError('check failed: $message');
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
String hex32(int value) => value.toRadixString(16).padLeft(8, '0');

void testDimensionsEdgesAndClasses() {
  check(bit.rows == 7 && bit.columns == 5, 'canonical dimensions');
  check(bit.edgeCount == 58, 'declared edge count');
  check(bit.pixelWidth == 16 && bit.pixelHeight == 22, 'pixel dimensions');
  final edges = bit.edges();
  check(edges.length == bit.edgeCount, '58 ordered edges');
  for (var index = 0; index < edges.length; index++) {
    final edge = edges[index];
    check(
      edge.endRow - edge.startRow + edge.endColumn - edge.startColumn == 1,
      'edge $index is an orthogonal nearest neighbor',
    );
    if (index > 0) {
      final previous = edges[index - 1];
      final previousKey =
          '${previous.startRow}${previous.startColumn}${previous.endRow}${previous.endColumn}';
      final key =
          '${edge.startRow}${edge.startColumn}${edge.endRow}${edge.endColumn}';
      check(previousKey.compareTo(key) < 0, 'edge $index is ordered');
    }
  }
  check(
    bit.BitSquiggleMode.values.map(bit.freeConnectionCount).join(',') ==
        '32,31,29,33',
    'free connection class counts',
  );
  check(
    bit.BitSquiggleStyle.values.map((style) => style.label).join(',') ==
        'standard,high-contrast,monochrome,black-and-white',
    'four ordered styles',
  );
  check(
    bit.BitSquiggleMode.values.map((mode) => mode.label).join(',') ==
        'A|,A-,A+,A/',
    'four ordered modes',
  );
}

void testGoldenVector() {
  final visual = bit.spec(0x89abcdef);
  check(bit.mix32(0x89abcdef) == 0x47ac5876, 'golden mix32');
  check(visual.mixed == 0x47ac5876, 'golden mixed field');
  check(
    visual.preferredMode == bit.BitSquiggleMode.topBottom,
    'golden preferred mode',
  );
  check(
    visual.actualMode == bit.BitSquiggleMode.topBottom,
    'golden actual mode',
  );
  check(!visual.fallback, 'golden fallback');
  check(visual.luminanceIndex == 2, 'golden luminance');
  check(visual.background.hex == '#140040', 'golden background');
  check(visual.foreground.hex == '#8d9200', 'golden foreground');
  check(
    bits(visual.connections) ==
        '0001111010110001011000011101010010101100001010011011010011',
    'golden connections',
  );
  check(
    bit.matchesMode(visual.connections, bit.BitSquiggleMode.topBottom),
    'mode membership',
  );
}

void testExactRasterAndStyles() {
  for (final input in [0, 1, 3, 4, 0x89abcdef, 0xffffffff]) {
    final visual = bit.spec(input);
    final grid = bit.pixels(input);
    check(grid.width == 16 && grid.height == 22, 'raster dimensions $input');
    check(grid.pixels.length == 352, 'raster storage $input');
    for (var x = 0; x < grid.width; x++) {
      check(grid.pixels[x] == 0, 'top border $input/$x');
      check(
        grid.pixels[(grid.height - 1) * grid.width + x] == 0,
        'bottom border $input/$x',
      );
    }
    for (var y = 0; y < grid.height; y++) {
      check(grid.pixels[y * grid.width] == 0, 'left border $input/$y');
      check(
        grid.pixels[y * grid.width + grid.width - 1] == 0,
        'right border $input/$y',
      );
    }
    for (var index = 0; index < bit.edgeCount; index++) {
      final edge = bit.edges()[index];
      final x = 1 + edge.startColumn * 3;
      final y = 1 + edge.startRow * 3;
      final bridge = edge.startRow == edge.endRow
          ? grid.pixels[y * grid.width + x + 2]
          : grid.pixels[(y + 2) * grid.width + x];
      check(
        bridge == visual.connections[index],
        'recoverable edge $input/$index',
      );
    }
    for (final style in bit.BitSquiggleStyle.values) {
      final styled = bit.spec(input, style);
      check(
        bits(styled.connections) == bits(visual.connections),
        'style preserves geometry $input/${style.label}',
      );
    }
  }
  for (var input = 0; input < 1000; input++) {
    final visual = bit.spec(input, bit.BitSquiggleStyle.blackAndWhite);
    check(
      visual.background.hex == '#000000' || visual.background.hex == '#ffffff',
      'binary background $input',
    );
    check(
      visual.foreground.hex == '#000000' || visual.foreground.hex == '#ffffff',
      'binary foreground $input',
    );
    check(
      visual.background.hex != visual.foreground.hex,
      'opposed colors $input',
    );
    check(
      (visual.foreground.hex == '#000000') == visual.swapped,
      'parity polarity $input',
    );
  }
}

List<int> connections(List<bit.Edge> selected) {
  final result = List<int>.filled(bit.edgeCount, 0);
  for (final edge in selected) {
    final index = bit.edges().indexOf(edge);
    check(index >= 0, 'test edge exists: $edge');
    result[index] = 1;
  }
  return result;
}

void testSmoothBlobs() {
  check(bit.smoothBlobs(List.filled(bit.edgeCount, 0)).isEmpty, 'empty blobs');
  check(
    bit.smoothBlobs(connections([const bit.Edge(0, 0, 0, 1)])).single ==
        const bit.SmoothBlob(0, 0, 0, 1),
    'single-edge blob',
  );
  check(
    bit
            .smoothBlobs(
              connections([
                const bit.Edge(0, 0, 0, 1),
                const bit.Edge(0, 1, 0, 2),
              ]),
            )
            .single ==
        const bit.SmoothBlob(0, 0, 0, 2),
    'connected row blob',
  );
  check(
    bit
            .smoothBlobs(
              connections([
                const bit.Edge(0, 0, 0, 1),
                const bit.Edge(1, 0, 1, 1),
                const bit.Edge(0, 0, 1, 0),
                const bit.Edge(0, 1, 1, 1),
              ]),
            )
            .single ==
        const bit.SmoothBlob(0, 0, 1, 1),
    'junction blob',
  );
  check(
    bit.smoothBlobs(List.filled(bit.edgeCount, 1)).single ==
        const bit.SmoothBlob(0, 0, 6, 4),
    'complete-grid blob',
  );
  for (var input = 0; input < 2000; input++) {
    final blobs = bit.smoothBlobs(bit.spec(input).connections);
    check(blobs.length <= bit.maxSmoothBlobs, 'bounded blobs $input');
  }
}

void testInvalidInputsAndImmutability() {
  expectFailure(() => bit.mix32(-1), 'negative mixer input');
  expectFailure(() => bit.spec(-1), 'negative input');
  expectFailure(() => bit.spec(0x100000000), 'oversized input');
  expectFailure(
    () => bit.matchesMode(List.filled(57, 0), bit.BitSquiggleMode.leftRight),
    'short mode mask',
  );
  expectFailure(
    () => bit.matchesMode(
      List.filled(bit.edgeCount, 2),
      bit.BitSquiggleMode.leftRight,
    ),
    'nonbinary mode mask',
  );
  expectFailure(() => bit.smoothBlobs(List.filled(57, 0)), 'short smooth mask');
  expectFailure(
    () => bit.smoothBlobs(List.filled(bit.edgeCount, 2)),
    'nonbinary smooth mask',
  );
  final visual = bit.spec(0);
  expectFailure(() => visual.connections[0] = 1, 'immutable connections');
  expectFailure(() => visual.cells[0][0] = 1, 'immutable cells');
  expectFailure(() => bit.edges().add(const bit.Edge(0, 0, 0, 1)), 'edges');
}

void testFixture() {
  final fixtureFile = File.fromUri(
    Platform.script.resolve('../fixtures/v1-32.json'),
  );
  final fixture =
      jsonDecode(fixtureFile.readAsStringSync()) as Map<String, dynamic>;
  check(fixture['schema'] == 'bitsquiggles-conformance', 'fixture schema');
  check(fixture['version'] == 1, 'fixture version');
  final vectors = fixture['vectors'] as List<dynamic>;
  check(vectors.isNotEmpty, 'fixture vectors present');

  for (final raw in vectors) {
    final vector = raw as Map<String, dynamic>;
    final input = int.parse(vector['input'] as String, radix: 16);
    final visual = bit.spec(input);
    final grid = bit.pixels(input);
    check(
      hex32(visual.mixed) == vector['mixed'],
      'fixture mixed ${vector['input']}',
    );
    check(
      bits(visual.connections) == vector['connections'],
      'fixture connections ${vector['input']}',
    );
    check(
      visual.preferredMode.label == vector['preferredMode'],
      'fixture preferred mode ${vector['input']}',
    );
    check(
      visual.actualMode.label == vector['actualMode'],
      'fixture actual mode ${vector['input']}',
    );
    check(
      visual.fallback == vector['fallback'],
      'fixture fallback ${vector['input']}',
    );
    check(
      bits(grid.pixels) == vector['pixels'],
      'fixture pixels ${vector['input']}',
    );
    final fixtureStyles = vector['styles'] as Map<String, dynamic>;
    for (final style in bit.BitSquiggleStyle.values) {
      final styled = bit.spec(input, style);
      final colors = fixtureStyles[style.label] as Map<String, dynamic>;
      check(
        styled.background.hex == colors['background'],
        'fixture background ${vector['input']}/${style.label}',
      );
      check(
        styled.foreground.hex == colors['foreground'],
        'fixture foreground ${vector['input']}/${style.label}',
      );
      check(
        bits(styled.connections) == bits(visual.connections),
        'fixture geometry ${vector['input']}/${style.label}',
      );
    }
  }
  for (final representative in [
    '00000000',
    '00000001',
    '00000003',
    '00000004',
  ]) {
    check(
      vectors.any(
        (raw) => (raw as Map<String, dynamic>)['input'] == representative,
      ),
      'representative fixture $representative',
    );
  }
}

void main() {
  testDimensionsEdgesAndClasses();
  testGoldenVector();
  testExactRasterAndStyles();
  testSmoothBlobs();
  testInvalidInputsAndImmutability();
  testFixture();
  stdout.writeln('BitSquiggles Dart tests passed ($_checks checks)');
}
