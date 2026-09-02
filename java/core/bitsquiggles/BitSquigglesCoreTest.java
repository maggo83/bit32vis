// Grug 2-Clause License
// 1. do what want
// 2. not sue grug

package bitsquiggles;

import bitsquiggles.internal.BitSquigglesCore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class BitSquigglesCoreTest {

  private static int checks;

  private static final TestVariant VARIANT_32 = new TestVariant(
    BitSquiggles.Width.BITS_32,
    BitSquigglesCore.rows(BitSquiggles.Width.BITS_32),
    BitSquigglesCore.columns(BitSquiggles.Width.BITS_32),
    BitSquigglesCore.edgeCount(BitSquiggles.Width.BITS_32),
    BitSquigglesCore.pixelWidth(BitSquiggles.Width.BITS_32),
    BitSquigglesCore.pixelHeight(BitSquiggles.Width.BITS_32)
  );
  private static final TestVariant VARIANT_40 = new TestVariant(
    BitSquiggles.Width.BITS_40,
    BitSquigglesCore.rows(BitSquiggles.Width.BITS_40),
    BitSquigglesCore.columns(BitSquiggles.Width.BITS_40),
    BitSquigglesCore.edgeCount(BitSquiggles.Width.BITS_40),
    BitSquigglesCore.pixelWidth(BitSquiggles.Width.BITS_40),
    BitSquigglesCore.pixelHeight(BitSquiggles.Width.BITS_40)
  );

  private record TestVariant(
    BitSquiggles.Width width,
    int rows,
    int columns,
    int edgeCount,
    int pixelWidth,
    int pixelHeight
  ) {
    BitSquigglesCore.Edge[] edges() {
      return BitSquigglesCore.edges(width);
    }

    BitSquiggles.VisSpec spec(long input) {
      return spec(input, BitSquiggles.Style.STANDARD);
    }

    BitSquiggles.VisSpec spec(
      long input,
      BitSquiggles.Style style
    ) {
      return BitSquigglesCore.spec(width, input, style);
    }

    BitSquiggles.PixelGrid pixels(long input) {
      return BitSquigglesCore.pixels(width, input);
    }

    boolean matches(byte[] connections, BitSquiggles.Mode mode) {
      return BitSquigglesCore.matchesMode(width, connections, mode);
    }

    BitSquigglesCore.SmoothBlob[] smoothBlobs(byte[] connections) {
      return BitSquigglesCore.smoothBlobs(width, connections);
    }
  }

  private BitSquigglesCoreTest() {}

  public static void main(String[] args) {
    testModeCapacities();
    testGolden32();
    testGolden40();
    testCanonicalEdges(VARIANT_32);
    testCanonicalEdges(VARIANT_40);
    testModeSelection(VARIANT_32, 100_000);
    testModeSelection(VARIANT_40, 100_000);
    testBitDiffusion(VARIANT_32, 20, 26.0);
    testBitDiffusion(VARIANT_40, 25, 25.0);
    testMarkerAndBip380();
    testSlashWrapRegression();
    testValidation();
    testSampledInjectivity(VARIANT_32, 250_000);
    testSampledInjectivity(VARIANT_40, 100_000);
    testPresentationAndRaster(VARIANT_32, 2_000);
    testPresentationAndRaster(VARIANT_40, 2_000);
    testSmoothBlobs(VARIANT_32, 2_000);
    testSmoothBlobs(VARIANT_40, 2_000);
    System.out.println(
      "BitSquiggles Java core tests passed (" + checks + " checks)"
    );
  }

  private static void testModeCapacities() {
    int[] expected32 = { 32, 31, 29, 33 };
    int[] expected40 = { 45, 45, 42, 42 };
    int[] usable40 = { 42, 42, 40, 40 };
    for (int index = 0; index < BitSquiggles.Mode.values().length; index++) {
      BitSquiggles.Mode mode = BitSquiggles.Mode.values()[index];
      check(
        BitSquigglesCore.freeConnectionCount(
          BitSquiggles.Width.BITS_32,
          mode
        ) == expected32[index],
        "32-bit free connection count"
      );
      check(
        BitSquigglesCore.usableConnectionCount(
          BitSquiggles.Width.BITS_32,
          mode
        ) == expected32[index],
        "32-bit usable connection count"
      );
      check(
        BitSquigglesCore.freeConnectionCount(
          BitSquiggles.Width.BITS_40,
          mode
        ) == expected40[index],
        "40-bit free connection count"
      );
      check(
        BitSquigglesCore.usableConnectionCount(
          BitSquiggles.Width.BITS_40,
          mode
        ) == usable40[index],
        "40-bit usable connection count"
      );
    }
  }

  private static void testGolden32() {
    BitSquiggles.VisSpec visual = BitSquigglesCore.spec(
      BitSquiggles.Width.BITS_32,
      0x89ABCDEFL
    );
    check(visual.variant() == BitSquiggles.Width.BITS_32, "32-bit width");
    check(visual.mixed() == 0x47AC5876L, "32-bit golden mixed value");
    check(
      visual.preferredMode() == BitSquiggles.Mode.TOP_BOTTOM,
      "32-bit golden preferred mode"
    );
    check(
      visual.actualMode() == BitSquiggles.Mode.TOP_BOTTOM,
      "32-bit golden actual mode"
    );
    check(!visual.fallback(), "32-bit golden fallback");
    check(visual.luminanceIndex() == 2, "32-bit golden luminance");
    check(visual.background().hex().equals("#140040"), "32-bit background");
    check(visual.foreground().hex().equals("#8d9200"), "32-bit foreground");
    check(
      bits(visual.connections()).equals(
        "0001111010110001011000011101010010101100001010011011010011"
      ),
      "32-bit golden connections"
    );
  }

  private static void testGolden40() {
    BitSquiggles.VisSpec visual = BitSquigglesCore.spec(
      BitSquiggles.Width.BITS_40,
      0x39527804DBL
    );
    check(visual.variant() == BitSquiggles.Width.BITS_40, "40-bit width");
    check(visual.mixed() == 0x34B1A077C8L, "40-bit golden mixed value");
    check(
      visual.preferredMode() == BitSquiggles.Mode.LEFT_RIGHT,
      "40-bit golden preferred mode"
    );
    check(
      visual.actualMode() == BitSquiggles.Mode.LEFT_RIGHT,
      "40-bit golden actual mode"
    );
    check(!visual.fallback(), "40-bit golden fallback");
    check(visual.luminanceIndex() == 0, "40-bit golden luminance");
    check(visual.background().hex().equals("#010001"), "40-bit background");
    check(visual.foreground().hex().equals("#007a41"), "40-bit foreground");
    check(
      bits(visual.connections()).equals(
        "001101001101001011010000110110101110001000000000000011101111111011100101000101000000"
      ),
      "40-bit golden connections"
    );
  }

  private static void testCanonicalEdges(TestVariant variant) {
    BitSquigglesCore.Edge[] edges = variant.edges();
    check(edges.length == variant.edgeCount(), "canonical edge count");
    String previous = "";
    for (BitSquigglesCore.Edge edge : edges) {
      check(
        edge.endRow() == edge.startRow() ||
        edge.endRow() == edge.startRow() + 1,
        "edge row adjacency"
      );
      check(
        edge.endColumn() == edge.startColumn() ||
        edge.endColumn() == edge.startColumn() + 1,
        "edge column adjacency"
      );
      check(
        edge.endRow() -
          edge.startRow() +
          edge.endColumn() -
          edge.startColumn() ==
        1,
        "orthogonal unit edge"
      );
      String key = String.format(
        "%d%d%d%d",
        edge.startRow(),
        edge.startColumn(),
        edge.endRow(),
        edge.endColumn()
      );
      check(previous.compareTo(key) < 0, "lexical edge order");
      previous = key;
    }
  }

  private static void testModeSelection(TestVariant variant, int samples) {
    boolean[] seenPreferred = new boolean[BitSquiggles.Mode.values().length];
    boolean sawFallback = false;
    boolean sawHalfTurnCapacityFallback = false;
    boolean sawAcceptedHalfTurn = false;
    for (int sample = 0; sample < samples; sample++) {
      BitSquiggles.VisSpec visual = variant.spec(sample);
      int preferred = visual.preferredMode().ordinal();
      seenPreferred[preferred] = true;
      sawFallback |= visual.fallback();
      if (
        variant.width() == BitSquiggles.Width.BITS_32 &&
        visual.preferredMode() == BitSquiggles.Mode.HALF_TURN
      ) {
        if ((visual.mixed() & 1) != 0) {
          check(visual.fallback(), "half-turn omitted bit uses fallback");
          sawHalfTurnCapacityFallback = true;
        } else if (!visual.fallback()) {
          sawAcceptedHalfTurn = true;
        }
      }

      byte[] dataConnections = visual.connections().clone();
      if (variant.width() == BitSquiggles.Width.BITS_40) {
        clearMarker(dataConnections);
      }
      check(
        variant.matches(dataConnections, visual.actualMode()),
        "visual matches actual mode"
      );
      if (visual.fallback()) {
        check(
          visual.actualMode() == BitSquiggles.Mode.LEFT_RIGHT,
          "fallback uses default mode"
        );
      } else {
        for (int earlier = 0; earlier < preferred; earlier++) {
          check(
            !variant.matches(
              dataConnections,
              BitSquiggles.Mode.values()[earlier]
            ),
            "accepted mode is canonical"
          );
        }
      }
    }
    for (int index = 0; index < seenPreferred.length; index++) {
      check(seenPreferred[index], "preferred mode observed");
    }
    if (variant.width() == BitSquiggles.Width.BITS_32) {
      check(sawFallback, "fallback observed");
      check(sawHalfTurnCapacityFallback, "capacity fallback observed");
      check(sawAcceptedHalfTurn, "accepted half-turn observed");
    }
  }

  private static void testBitDiffusion(
    TestVariant variant,
    int minimum,
    double minimumAverage
  ) {
    byte[] base = variant.spec(0).connections();
    int bitCount = variant.width() == BitSquiggles.Width.BITS_32 ? 32 : 40;
    int total = 0;
    for (int bit = 0; bit < bitCount; bit++) {
      int distance = distance(base, variant.spec(1L << bit).connections());
      check(distance >= minimum, "one-bit edge diffusion");
      total += distance;
    }
    check(total / (double) bitCount >= minimumAverage, "average diffusion");
  }

  private static void testMarkerAndBip380() {
    check(BitSquigglesCore.bip380ChecksumInput("qqqqqqqq") == 0, "zero BIP380");
    check(
      BitSquigglesCore.bip380ChecksumInput("89f8spxm") == 0x39527804DBL,
      "canonical BIP380"
    );
    check(
      BitSquigglesCore.bip380ChecksumInput("llllllll") == 0xFFFFFFFFFFL,
      "maximum BIP380"
    );
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput(null));
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput("89f8spx"));
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput("89f8spxmq"));
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput("89F8spxm"));
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput("89f8#pxm"));
    expectFailure(() -> BitSquigglesCore.bip380ChecksumInput("iiiiiiii"));

    long[] fallbackInputs = {
      0xA7912DEF7BL,
      0x08A1A65B0CL,
      0x500181B841L,
    };
    for (long input : fallbackInputs) {
      BitSquiggles.VisSpec visual = BitSquigglesCore.spec(
        BitSquiggles.Width.BITS_40,
        input
      );
      check(visual.fallback(), "targeted 40-bit fallback");
      check(
        visual.actualMode() == BitSquiggles.Mode.LEFT_RIGHT,
        "targeted fallback mode"
      );
    }

    for (long input = 0; input < 10_000; input++) {
      byte[] connections = BitSquigglesCore.spec(
        BitSquiggles.Width.BITS_40,
        input
      ).connections();
      check(validMarker(connections), "center marker");
      byte[] rotated = connections;
      for (int turn = 0; turn < 3; turn++) {
        rotated = rotate40(rotated);
        check(!validMarker(rotated), "rotation rejected by marker");
      }
    }
  }

  private static void testSlashWrapRegression() {
    BitSquiggles.VisSpec visual = BitSquigglesCore.spec(
      BitSquiggles.Width.BITS_32,
      0xD9ABCDEFL
    );
    check(
      visual.actualMode() == BitSquiggles.Mode.DIAGONAL_SLASH,
      "slash wrap regression mode"
    );
    check(
      edgeValue(VARIANT_32, visual.connections(), 1, 1, 2, 1) ==
      edgeValue(VARIANT_32, visual.connections(), 4, 3, 4, 4),
      "slash upper vertical maps to lower horizontal"
    );
    check(
      edgeValue(VARIANT_32, visual.connections(), 1, 2, 2, 2) ==
      edgeValue(VARIANT_32, visual.connections(), 3, 3, 3, 4),
      "slash adjacent edge relation"
    );
  }

  private static void testValidation() {
    expectFailure(() -> BitSquigglesCore.spec(null, 0));
    expectFailure(
      () -> BitSquigglesCore.spec(BitSquiggles.Width.BITS_32, 0, null)
    );
    expectFailure(
      () -> BitSquigglesCore.pixels(BitSquiggles.Width.BITS_32, 0, null)
    );
    expectFailure(
      () -> BitSquigglesCore.mix(BitSquiggles.Width.BITS_40, -1)
    );
    expectFailure(
      () -> BitSquigglesCore.mix(
        BitSquiggles.Width.BITS_40,
        0x10000000000L
      )
    );
    expectFailure(
      () -> BitSquigglesCore.spec(BitSquiggles.Width.BITS_40, -1)
    );
    expectFailure(
      () -> BitSquigglesCore.spec(
        BitSquiggles.Width.BITS_40,
        0x10000000000L
      )
    );
    expectFailure(
      () -> BitSquigglesCore.spec(BitSquiggles.Width.BITS_40, 0, null)
    );
    expectFailure(
      () -> BitSquigglesCore.pixels(BitSquiggles.Width.BITS_40, 0, null)
    );

    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_32,
        null,
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject null 32-bit connections"
    );
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_40,
        null,
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject null 40-bit connections"
    );
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_32,
        new byte[57],
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject short 32-bit connections"
    );
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_40,
        new byte[83],
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject short 40-bit connections"
    );
    byte[] invalid32 = new byte[
      BitSquigglesCore.edgeCount(BitSquiggles.Width.BITS_32)
    ];
    invalid32[0] = 2;
    byte[] invalid40 = new byte[
      BitSquigglesCore.edgeCount(BitSquiggles.Width.BITS_40)
    ];
    invalid40[0] = 2;
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_32,
        invalid32,
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject non-binary 32-bit connections"
    );
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_40,
        invalid40,
        BitSquiggles.Mode.LEFT_RIGHT
      ),
      "reject non-binary 40-bit connections"
    );
    check(
      !BitSquigglesCore.matchesMode(
        BitSquiggles.Width.BITS_32,
        new byte[58],
        null
      ),
      "reject null mode"
    );
  }

  private static void testSampledInjectivity(
    TestVariant variant,
    int samples
  ) {
    Set<String> monochromeMasks = new HashSet<>();
    Set<String> blackAndWhiteBitmaps = new HashSet<>();
    for (int sample = 0; sample < samples; sample++) {
      BitSquiggles.VisSpec monochrome = variant.spec(
        sample,
        BitSquiggles.Style.MONOCHROME
      );
      check(
        monochromeMasks.add(bits(monochrome.connections())),
        "unique sampled monochrome mask"
      );

      BitSquiggles.VisSpec blackAndWhite = variant.spec(
        sample,
        BitSquiggles.Style.BLACK_AND_WHITE
      );
      BitSquiggles.PixelGrid grid = BitSquigglesCore.pixels(
        variant.width(),
        sample,
        BitSquiggles.Style.BLACK_AND_WHITE
      );
      check(
        blackAndWhiteBitmaps.add(blackAndWhiteBitmap(grid, blackAndWhite)),
        "unique sampled black-and-white bitmap"
      );
    }
  }

  private static void testPresentationAndRaster(
    TestVariant variant,
    int samples
  ) {
    for (int sample = 0; sample < samples; sample++) {
      BitSquiggles.VisSpec standard = variant.spec(
        sample,
        BitSquiggles.Style.STANDARD
      );
      BitSquiggles.VisSpec contrast = variant.spec(
        sample,
        BitSquiggles.Style.HIGH_CONTRAST
      );
      BitSquiggles.VisSpec monochrome = variant.spec(
        sample,
        BitSquiggles.Style.MONOCHROME
      );
      BitSquiggles.VisSpec blackAndWhite = variant.spec(
        sample,
        BitSquiggles.Style.BLACK_AND_WHITE
      );
      check(
        Arrays.equals(standard.connections(), contrast.connections()),
        "high contrast preserves connections"
      );
      check(
        Arrays.equals(standard.connections(), monochrome.connections()),
        "monochrome preserves connections"
      );
      check(
        Arrays.equals(standard.connections(), blackAndWhite.connections()),
        "black-and-white preserves connections"
      );
      check(
        contrast.foreground().C() > standard.foreground().C(),
        "high contrast increases chroma"
      );
      check(
        monochrome.background().C() == 0 &&
        monochrome.foreground().C() == 0,
        "monochrome removes chroma"
      );
      check(
        blackAndWhite.background().hex().matches("#(?:000000|ffffff)") &&
        blackAndWhite.foreground().hex().matches("#(?:000000|ffffff)") &&
        !blackAndWhite.background().hex().equals(
          blackAndWhite.foreground().hex()
        ),
        "black-and-white uses opposed binary colors"
      );
      check(
        blackAndWhite.foreground().hex().equals("#000000") ==
        blackAndWhite.swapped(),
        "black-and-white parity controls polarity"
      );
      check(
        standard.swapped() == ((Long.bitCount(sample) & 1) != 0),
        "input parity controls polarity"
      );
      check(
        standard.luminanceIndex() == (standard.mixed() & 3),
        "mixed value controls luminance"
      );
      assertActiveCells(variant, standard);
      assertRaster(variant, sample, standard);
    }
  }

  private static void assertActiveCells(
    TestVariant variant,
    BitSquiggles.VisSpec visual
  ) {
    int[][] expected = new int[variant.rows()][variant.columns()];
    BitSquigglesCore.Edge[] edges = variant.edges();
    for (int index = 0; index < edges.length; index++) {
      if (visual.connections()[index] == 0) continue;
      BitSquigglesCore.Edge edge = edges[index];
      expected[edge.startRow()][edge.startColumn()] = 1;
      expected[edge.endRow()][edge.endColumn()] = 1;
    }
    check(Arrays.deepEquals(expected, visual.cells()), "active cells");
  }

  private static void assertRaster(
    TestVariant variant,
    long input,
    BitSquiggles.VisSpec visual
  ) {
    BitSquiggles.PixelGrid grid = variant.pixels(input);
    check(grid.variant() == variant.width(), "pixel grid width variant");
    check(
      grid.width() == variant.pixelWidth() &&
      grid.height() == variant.pixelHeight(),
      "pixel dimensions"
    );
    check(
      grid.pixels().length == variant.pixelWidth() * variant.pixelHeight(),
      "pixel storage"
    );
    check(
      grid.background().equals(visual.background()) &&
      grid.foreground().equals(visual.foreground()),
      "pixel colors"
    );
    for (int x = 0; x < grid.width(); x++) {
      check(grid.pixels()[x] == 0, "top raster border");
      check(
        grid.pixels()[(grid.height() - 1) * grid.width() + x] == 0,
        "bottom raster border"
      );
    }
    for (int y = 0; y < grid.height(); y++) {
      check(grid.pixels()[y * grid.width()] == 0, "left raster border");
      check(
        grid.pixels()[y * grid.width() + grid.width() - 1] == 0,
        "right raster border"
      );
    }
    BitSquigglesCore.Edge[] edges = variant.edges();
    for (int index = 0; index < edges.length; index++) {
      BitSquigglesCore.Edge edge = edges[index];
      int x = 1 + edge.startColumn() * 3;
      int y = 1 + edge.startRow() * 3;
      int bridge = edge.startRow() == edge.endRow()
        ? grid.pixels()[y * grid.width() + x + 2]
        : grid.pixels()[(y + 2) * grid.width() + x];
      check(bridge == visual.connections()[index], "recoverable raster edge");
    }
  }

  private static void testSmoothBlobs(TestVariant variant, int samples) {
    check(
      variant.smoothBlobs(new byte[variant.edgeCount()]).length == 0,
      "empty mask has no smooth blobs"
    );

    byte[] singleEdge = connections(variant, 0, 0, 0, 1);
    BitSquigglesCore.SmoothBlob[] single = variant.smoothBlobs(singleEdge);
    check(
      Arrays.equals(single, new BitSquigglesCore.SmoothBlob[] {
        new BitSquigglesCore.SmoothBlob(0, 0, 0, 1),
      }),
      "single edge smooth blob"
    );
    assertBlobCoverage(variant, singleEdge, single);

    byte[] row = connections(variant, 0, 0, 0, 1, 0, 1, 0, 2);
    BitSquigglesCore.SmoothBlob[] rowBlobs = variant.smoothBlobs(row);
    check(
      Arrays.equals(rowBlobs, new BitSquigglesCore.SmoothBlob[] {
        new BitSquigglesCore.SmoothBlob(0, 0, 0, 2),
      }),
      "connected row smooth blob"
    );
    assertBlobCoverage(variant, row, rowBlobs);

    byte[] square = connections(
      variant,
      0,
      0,
      0,
      1,
      1,
      0,
      1,
      1,
      0,
      0,
      1,
      0,
      0,
      1,
      1,
      1
    );
    BitSquigglesCore.SmoothBlob[] squareBlobs = variant.smoothBlobs(square);
    check(
      Arrays.equals(squareBlobs, new BitSquigglesCore.SmoothBlob[] {
        new BitSquigglesCore.SmoothBlob(0, 0, 1, 1),
      }),
      "square smooth blob"
    );
    assertBlobCoverage(variant, square, squareBlobs);

    byte[] complete = new byte[variant.edgeCount()];
    Arrays.fill(complete, (byte) 1);
    BitSquigglesCore.SmoothBlob[] completeBlobs = variant.smoothBlobs(complete);
    check(
      Arrays.equals(completeBlobs, new BitSquigglesCore.SmoothBlob[] {
        new BitSquigglesCore.SmoothBlob(
          0,
          0,
          variant.rows() - 1,
          variant.columns() - 1
        ),
      }),
      "complete grid smooth blob"
    );
    assertBlobCoverage(variant, complete, completeBlobs);

    for (int sample = 0; sample < samples; sample++) {
      byte[] mask = variant.spec(sample).connections();
      assertBlobCoverage(variant, mask, variant.smoothBlobs(mask));
    }

    expectFailure(() -> variant.smoothBlobs(new byte[variant.edgeCount() - 1]));
    byte[] invalid = new byte[variant.edgeCount()];
    invalid[0] = 2;
    expectFailure(() -> variant.smoothBlobs(invalid));
  }

  private static byte[] connections(TestVariant variant, int... endpoints) {
    byte[] result = new byte[variant.edgeCount()];
    for (int index = 0; index < endpoints.length; index += 4) {
      result[
        edgeIndex(
          variant,
          endpoints[index],
          endpoints[index + 1],
          endpoints[index + 2],
          endpoints[index + 3]
        )
      ] = 1;
    }
    return result;
  }

  private static void assertBlobCoverage(
    TestVariant variant,
    byte[] connections,
    BitSquigglesCore.SmoothBlob[] blobs
  ) {
    boolean[] selectedEdges = new boolean[variant.edgeCount()];
    boolean[] coveredEdges = new boolean[variant.edgeCount()];
    boolean[][] activeCells = new boolean[variant.rows()][variant.columns()];
    BitSquigglesCore.Edge[] edges = variant.edges();
    for (int index = 0; index < edges.length; index++) {
      if (connections[index] == 0) continue;
      selectedEdges[index] = true;
      BitSquigglesCore.Edge edge = edges[index];
      activeCells[edge.startRow()][edge.startColumn()] = true;
      activeCells[edge.endRow()][edge.endColumn()] = true;
    }

    boolean[] requiredJunctions = new boolean[
      (variant.rows() - 1) * (variant.columns() - 1)
    ];
    boolean[] coveredJunctions = new boolean[requiredJunctions.length];
    for (int row = 0; row < variant.rows() - 1; row++) {
      for (int column = 0; column < variant.columns() - 1; column++) {
        requiredJunctions[junctionIndex(variant, row, column)] =
          edgeValue(variant, connections, row, column, row, column + 1) == 1 &&
          edgeValue(
            variant,
            connections,
            row + 1,
            column,
            row + 1,
            column + 1
          ) == 1 &&
          edgeValue(variant, connections, row, column, row + 1, column) == 1 &&
          edgeValue(
            variant,
            connections,
            row,
            column + 1,
            row + 1,
            column + 1
          ) == 1;
      }
    }

    for (BitSquigglesCore.SmoothBlob blob : blobs) {
      check(
        blob.topRow() >= 0 &&
        blob.leftColumn() >= 0 &&
        blob.bottomRow() < variant.rows() &&
        blob.rightColumn() < variant.columns(),
        "smooth blob coordinates"
      );
      for (int row = blob.topRow(); row <= blob.bottomRow(); row++) {
        for (int column = blob.leftColumn(); column <= blob.rightColumn(); column++) {
          check(activeCells[row][column], "smooth blob uses active cells");
        }
      }
      for (int index = 0; index < edges.length; index++) {
        if (!inside(blob, edges[index])) continue;
        check(selectedEdges[index], "smooth blob internal edge is selected");
        coveredEdges[index] = true;
      }
      for (int row = blob.topRow(); row < blob.bottomRow(); row++) {
        for (int column = blob.leftColumn(); column < blob.rightColumn(); column++) {
          coveredJunctions[junctionIndex(variant, row, column)] = true;
        }
      }
    }
    check(Arrays.equals(selectedEdges, coveredEdges), "smooth edge coverage");
    for (int index = 0; index < requiredJunctions.length; index++) {
      check(
        !requiredJunctions[index] || coveredJunctions[index],
        "smooth junction coverage"
      );
    }
  }

  private static boolean inside(
    BitSquigglesCore.SmoothBlob blob,
    BitSquigglesCore.Edge edge
  ) {
    return (
      edge.startRow() >= blob.topRow() &&
      edge.startRow() <= blob.bottomRow() &&
      edge.startColumn() >= blob.leftColumn() &&
      edge.startColumn() <= blob.rightColumn() &&
      edge.endRow() >= blob.topRow() &&
      edge.endRow() <= blob.bottomRow() &&
      edge.endColumn() >= blob.leftColumn() &&
      edge.endColumn() <= blob.rightColumn()
    );
  }

  private static int junctionIndex(
    TestVariant variant,
    int row,
    int column
  ) {
    return row * (variant.columns() - 1) + column;
  }

  private static String blackAndWhiteBitmap(
    BitSquiggles.PixelGrid grid,
    BitSquiggles.VisSpec visual
  ) {
    boolean foregroundWhite = visual.foreground().hex().equals("#ffffff");
    boolean backgroundWhite = visual.background().hex().equals("#ffffff");
    StringBuilder result = new StringBuilder((grid.pixels().length + 15) / 16);
    int packed = 0;
    int count = 0;
    for (byte pixel : grid.pixels()) {
      packed = (packed << 1) |
        (pixel == 1 ? foregroundWhite ? 1 : 0 : backgroundWhite ? 1 : 0);
      count++;
      if (count == 16) {
        result.append((char) packed);
        packed = 0;
        count = 0;
      }
    }
    if (count != 0) result.append((char) (packed << (16 - count)));
    return result.toString();
  }

  private static void clearMarker(byte[] connections) {
    connections[edgeIndex(VARIANT_40, 2, 3, 3, 3)] = 0;
    connections[edgeIndex(VARIANT_40, 3, 2, 3, 3)] = 0;
    connections[edgeIndex(VARIANT_40, 3, 3, 3, 4)] = 0;
    connections[edgeIndex(VARIANT_40, 3, 3, 4, 3)] = 0;
  }

  private static boolean validMarker(byte[] connections) {
    return (
      edgeValue(VARIANT_40, connections, 2, 3, 3, 3) == 1 &&
      edgeValue(VARIANT_40, connections, 3, 2, 3, 3) == 0 &&
      edgeValue(VARIANT_40, connections, 3, 3, 3, 4) == 0 &&
      edgeValue(VARIANT_40, connections, 3, 3, 4, 3) == 0
    );
  }

  private static byte[] rotate40(byte[] connections) {
    byte[] result = new byte[connections.length];
    BitSquigglesCore.Edge[] edges = VARIANT_40.edges();
    for (int index = 0; index < edges.length; index++) {
      BitSquigglesCore.Edge edge = edges[index];
      int startRow = edge.startColumn();
      int startColumn = 6 - edge.startRow();
      int endRow = edge.endColumn();
      int endColumn = 6 - edge.endRow();
      result[
        edgeIndex(
          VARIANT_40,
          startRow,
          startColumn,
          endRow,
          endColumn
        )
      ] = connections[index];
    }
    return result;
  }

  private static int edgeValue(
    TestVariant variant,
    byte[] connections,
    int startRow,
    int startColumn,
    int endRow,
    int endColumn
  ) {
    return connections[
      edgeIndex(
        variant,
        startRow,
        startColumn,
        endRow,
        endColumn
      )
    ];
  }

  private static int edgeIndex(
    TestVariant variant,
    int startRow,
    int startColumn,
    int endRow,
    int endColumn
  ) {
    if (
      startRow > endRow ||
      (startRow == endRow && startColumn > endColumn)
    ) {
      int row = startRow;
      int column = startColumn;
      startRow = endRow;
      startColumn = endColumn;
      endRow = row;
      endColumn = column;
    }
    BitSquigglesCore.Edge[] edges = variant.edges();
    for (int index = 0; index < edges.length; index++) {
      BitSquigglesCore.Edge edge = edges[index];
      if (
        edge.startRow() == startRow &&
        edge.startColumn() == startColumn &&
        edge.endRow() == endRow &&
        edge.endColumn() == endColumn
      ) return index;
    }
    throw new AssertionError("not a canonical edge");
  }

  private static int distance(byte[] first, byte[] second) {
    int result = 0;
    for (int index = 0; index < first.length; index++) {
      if (first[index] != second[index]) result++;
    }
    return result;
  }

  private static void expectFailure(Runnable action) {
    try {
      action.run();
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      checks++;
    }
  }

  private static String bits(byte[] values) {
    StringBuilder result = new StringBuilder(values.length);
    for (byte value : values) result.append(value);
    return result.toString();
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}