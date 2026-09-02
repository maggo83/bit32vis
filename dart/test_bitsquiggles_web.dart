// Dart-to-JavaScript numeric smoke test for the dependency-free core.
// Grug 2-Clause License: do what want; not sue grug.

import 'bitsquiggles_core.dart' as bit;

void check(bool condition, String message) {
  if (!condition) throw StateError('check failed: $message');
}

void main() {
  check(bit.mix(32, 0x89abcdef) == 0x47ac5876, '32-bit golden mixer');
  check(bit.mix(40, 0x39527804db) == 0x34b1a077c8, '40-bit golden mixer');
  check(bit.spec(40, 0xffffffffff).connections.length == 84, 'maximum input');
  check(
    bit.smoothBlobs(40, List.filled(84, 1)).single ==
        const bit.SmoothBlob(0, 0, 6, 6),
    '84-edge smooth mask',
  );
  print('BitSquiggles Dart web smoke test passed');
}
