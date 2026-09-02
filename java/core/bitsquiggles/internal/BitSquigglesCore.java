package bitsquiggles.internal;

import bitsquiggles.BitSquiggles.Mode;
import bitsquiggles.BitSquiggles.OklchColor;
import bitsquiggles.BitSquiggles.PixelGrid;
import bitsquiggles.BitSquiggles.Style;
import bitsquiggles.BitSquiggles.VisSpec;
import bitsquiggles.BitSquiggles.Width;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Internal dependency-free implementation shared by both variants. */
public final class BitSquigglesCore {

  private static final int ROWS_32 = 7;
  private static final int COLUMNS_32 = 5;
  private static final int EDGE_COUNT_32 = 58;
  private static final int PIXEL_WIDTH_32 = 16;
  private static final int PIXEL_HEIGHT_32 = 22;

  private static final int ROWS_40 = 7;
  private static final int COLUMNS_40 = 7;
  private static final int EDGE_COUNT_40 = 84;
  private static final int PIXEL_WIDTH_40 = 22;
  private static final int PIXEL_HEIGHT_40 = 22;

  public record Edge(
    int startRow,
    int startColumn,
    int endRow,
    int endColumn
  ) {}

  public record SmoothBlob(
    int topRow,
    int leftColumn,
    int bottomRow,
    int rightColumn
  ) {}

  record ColorData(double lightness, double chroma, double hue, String hex) {}

  record Colors(ColorData background, ColorData foreground) {}

  private record ConnectionClass(int[] occurrences, boolean excluded) {}

  private record ModeDefinition(
    ConnectionClass[] classes,
    ConnectionClass[] usable
  ) {}

  private record Cell(int row, int column) {}

  @FunctionalInterface
  private interface CellTransform {
    Cell apply(int row, int column);
  }

  private static final class Variant {

    final Width width;
    final int rows;
    final int columns;
    final int edgeCount;
    final int pixelWidth;
    final int pixelHeight;
    final int inputWidth;
    final int payloadWidth;
    final long inputMask;
    final Edge[] edges;
    final int[] markerIndices;
    final ModeDefinition[] definitions;

    Variant(
      Width width,
      int rows,
      int columns,
      int edgeCount,
      int pixelWidth,
      int pixelHeight,
      int inputWidth,
      int payloadWidth,
      long inputMask
    ) {
      this.width = width;
      this.rows = rows;
      this.columns = columns;
      this.edgeCount = edgeCount;
      this.pixelWidth = pixelWidth;
      this.pixelHeight = pixelHeight;
      this.inputWidth = inputWidth;
      this.payloadWidth = payloadWidth;
      this.inputMask = inputMask;
      edges = createEdges(this);
      markerIndices = width == Width.BITS_40
        ? new int[] {
          edgeIndex(this, 2, 3, 3, 3),
          edgeIndex(this, 3, 2, 3, 3),
          edgeIndex(this, 3, 3, 3, 4),
          edgeIndex(this, 3, 3, 4, 3),
        }
        : new int[0];
      definitions = width == Width.BITS_32
        ? createDefinitions32(this)
        : createDefinitions40(this);
    }
  }

  private static final long MASK_32 = 0xFFFFFFFFL;
  private static final long MASK_40 = 0xFFFFFFFFFFL;
  private static final String CHECKSUM_CHARSET =
    "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

  private static final String[][][] TEMPLATES_32 = {
    rows(
      "A B C B' A'",
      "D E F E' D'",
      "G H I H' G'",
      "J K L K' J'",
      "M N O N' M'",
      "P Q R Q' P'",
      "S T U T' S'"
    ),
    rows(
      "A B C D E",
      "F G H I J",
      "K L M N O",
      "P Q R S T",
      "K' L' M' N' O'",
      "F' G' H' I' J'",
      "A' B' C' D' E'"
    ),
    rows(
      "A B C D E",
      "F G H I J",
      "K L M N O",
      "P Q R Q' P'",
      "O' N' M' L' K'",
      "J' I' H' G' F'",
      "E' D' C' B' A'"
    ),
    rows(
      "A B C D E",
      "F G H I J",
      "K L M N I'",
      "O P Q M' H'",
      "R S P' L' G'",
      "T R' O' K' F'",
      "E' D' C' B' A'"
    ),
  };

  private static final Variant VARIANT_32 = new Variant(
    Width.BITS_32,
    ROWS_32,
    COLUMNS_32,
    EDGE_COUNT_32,
    PIXEL_WIDTH_32,
    PIXEL_HEIGHT_32,
    32,
    30,
    MASK_32
  );
  private static final Variant VARIANT_40 = new Variant(
    Width.BITS_40,
    ROWS_40,
    COLUMNS_40,
    EDGE_COUNT_40,
    PIXEL_WIDTH_40,
    PIXEL_HEIGHT_40,
    40,
    38,
    MASK_40
  );

  private BitSquigglesCore() {}

  public static int rows(Width width) {
    return variant(width).rows;
  }

  public static int columns(Width width) {
    return variant(width).columns;
  }

  public static int edgeCount(Width width) {
    return variant(width).edgeCount;
  }

  public static int pixelWidth(Width width) {
    return variant(width).pixelWidth;
  }

  public static int pixelHeight(Width width) {
    return variant(width).pixelHeight;
  }

  public static Edge[] edges(Width width) {
    return variant(width).edges.clone();
  }

  public static int freeConnectionCount(Width width, Mode mode) {
    return definition(variant(width), mode).classes().length;
  }

  public static int usableConnectionCount(Width width, Mode mode) {
    return definition(variant(width), mode).usable().length;
  }

  public static long mix(Width width, long input) {
    Variant variant = variant(width);
    requireInput(variant, input);
    return mix(variant, input);
  }

  public static boolean matchesMode(
    Width width,
    byte[] connections,
    Mode mode
  ) {
    return matchesMode(variant(width), connections, mode);
  }

  public static VisSpec spec(Width width, long input, Style style) {
    return generateSpec(variant(width), input, style);
  }

  public static VisSpec spec(Width width, long input) {
    return spec(width, input, Style.STANDARD);
  }

  public static PixelGrid pixels(Width width, long input, Style style) {
    Variant variant = variant(width);
    return generatePixels(variant, generateSpec(variant, input, style));
  }

  public static PixelGrid pixels(Width width, long input) {
    return pixels(width, input, Style.STANDARD);
  }

  public static SmoothBlob[] smoothBlobs(
    Width width,
    byte[] connections
  ) {
    return smoothBlobs(variant(width), connections);
  }

  public static long bip380ChecksumInput(String checksum) {
    if (checksum == null || checksum.length() != 8) {
      throw new IllegalArgumentException(
        "checksum must contain exactly eight characters"
      );
    }
    long value = 0;
    for (int index = 0; index < checksum.length(); index++) {
      int symbol = CHECKSUM_CHARSET.indexOf(checksum.charAt(index));
      if (symbol < 0) throw new IllegalArgumentException(
        "invalid BIP380 checksum character"
      );
      value = (value << 5) | symbol;
    }
    return value;
  }

  private static Variant variant(Width width) {
    if (width == null) throw new IllegalArgumentException("width is required");
    return width == Width.BITS_32 ? VARIANT_32 : VARIANT_40;
  }

  private static ModeDefinition definition(Variant variant, Mode mode) {
    if (mode == null) throw new IllegalArgumentException("mode is required");
    return variant.definitions[mode.ordinal()];
  }

  private static void requireInput(Variant variant, long input) {
    if (input < 0 || input > variant.inputMask) throw new IllegalArgumentException(
      "input must be unsigned " + variant.inputWidth + "-bit"
    );
  }

  private static VisSpec generateSpec(
    Variant variant,
    long input,
    Style style
  ) {
    requireInput(variant, input);
    if (style == null) throw new IllegalArgumentException("style is required");
    long mixed = mix(variant, input);
    Mode preferredMode = Mode.values()[(int) (mixed >>> variant.payloadWidth)];
    byte[] candidate = encode(variant, mixed, preferredMode);
    boolean fallback =
      preferredMode != Mode.LEFT_RIGHT &&
      (!preferredModeHasCapacity(variant, mixed, preferredMode) ||
        conflictsWithEarlierMode(variant, candidate, preferredMode));
    Mode actualMode = fallback ? Mode.LEFT_RIGHT : preferredMode;
    byte[] connections = fallback
      ? encode(variant, mixed, Mode.LEFT_RIGHT)
      : candidate;
    applyMarker(variant, connections);
    Colors colors = deriveColors(mixed, input, style.ordinal());
    return new VisSpec(
      variant.width,
      input,
      mixed,
      connections,
      activeCells(variant, connections),
      color(colors.background()),
      color(colors.foreground()),
      style,
      preferredMode,
      actualMode,
      fallback,
      (int) (mixed & 3),
      (Long.bitCount(input) & 1) != 0
    );
  }

  private static long mix(Variant variant, long value) {
    if (variant.width == Width.BITS_32) {
      int mixed = (int) value + 0x9E3779B9;
      mixed ^= mixed >>> 16;
      mixed *= 0x85EBCA6B;
      mixed ^= mixed >>> 13;
      mixed *= 0xC2B2AE35;
      return Integer.toUnsignedLong(mixed ^ (mixed >>> 16));
    }
    long mixed = (value + 0xB97F4A7C15L) & MASK_40;
    mixed = ((mixed ^ (mixed >>> 20)) * 0xD7ED558CCDL) & MASK_40;
    mixed = ((mixed ^ (mixed >>> 15)) * 0xFE1A85EC53L) & MASK_40;
    return (mixed ^ (mixed >>> 20)) & MASK_40;
  }

  private static byte[] encode(Variant variant, long mixed, Mode mode) {
    ModeDefinition definition = definition(variant, mode);
    int bitWidth = mode == Mode.LEFT_RIGHT
      ? variant.inputWidth
      : variant.payloadWidth;
    long value = mode == Mode.LEFT_RIGHT
      ? mixed
      : mixed & ((1L << variant.payloadWidth) - 1);
    byte[] connections = new byte[variant.edgeCount];
    int usableIndex = 0;
    for (ConnectionClass connectionClass : definition.classes()) {
      byte selected = connectionClass.excluded()
        ? 0
        : (byte) ((value >>> (bitWidth - 1 - usableIndex % bitWidth)) & 1);
      for (int edgeIndex : connectionClass.occurrences()) {
        connections[edgeIndex] = selected;
      }
      if (!connectionClass.excluded()) usableIndex++;
    }
    return connections;
  }

  private static boolean preferredModeHasCapacity(
    Variant variant,
    long mixed,
    Mode mode
  ) {
    int missingBits =
      variant.payloadWidth - definition(variant, mode).usable().length;
    return missingBits <= 0 || (mixed & ((1L << missingBits) - 1)) == 0;
  }

  private static boolean conflictsWithEarlierMode(
    Variant variant,
    byte[] connections,
    Mode mode
  ) {
    for (int index = 0; index < mode.ordinal(); index++) {
      if (matchesMode(variant, connections, Mode.values()[index])) return true;
    }
    return false;
  }

  private static boolean matchesMode(
    Variant variant,
    byte[] connections,
    Mode mode
  ) {
    if (
      connections == null ||
      connections.length != variant.edgeCount ||
      mode == null
    ) return false;
    for (byte value : connections) if (value != 0 && value != 1) return false;
    for (ConnectionClass connectionClass : definition(variant, mode).classes()) {
      int expected = connections[connectionClass.occurrences()[0]];
      if (connectionClass.excluded() && expected != 0) return false;
      for (int edgeIndex : connectionClass.occurrences()) {
        if (connections[edgeIndex] != expected) return false;
      }
    }
    return true;
  }

  private static void applyMarker(Variant variant, byte[] connections) {
    for (int index = 0; index < variant.markerIndices.length; index++) {
      connections[variant.markerIndices[index]] = (byte) (index == 0 ? 1 : 0);
    }
  }

  private static int[][] activeCells(Variant variant, byte[] connections) {
    int[][] cells = new int[variant.rows][variant.columns];
    for (int index = 0; index < variant.edgeCount; index++) {
      if (connections[index] == 0) continue;
      Edge edge = variant.edges[index];
      cells[edge.startRow()][edge.startColumn()] = 1;
      cells[edge.endRow()][edge.endColumn()] = 1;
    }
    return cells;
  }

  private static PixelGrid generatePixels(Variant variant, VisSpec visual) {
    byte[] pixels = new byte[variant.pixelWidth * variant.pixelHeight];
    int[][] cells = visual.cells();
    for (int row = 0; row < variant.rows; row++) {
      for (int column = 0; column < variant.columns; column++) {
        if (cells[row][column] == 0) continue;
        int x = 1 + column * 3;
        int y = 1 + row * 3;
        setPixel(variant, pixels, x, y);
        setPixel(variant, pixels, x + 1, y);
        setPixel(variant, pixels, x, y + 1);
        setPixel(variant, pixels, x + 1, y + 1);
      }
    }
    for (int index = 0; index < variant.edgeCount; index++) {
      if (visual.connections()[index] == 0) continue;
      Edge edge = variant.edges[index];
      int x = 1 + edge.startColumn() * 3;
      int y = 1 + edge.startRow() * 3;
      if (edge.startRow() == edge.endRow()) {
        setPixel(variant, pixels, x + 2, y);
        setPixel(variant, pixels, x + 2, y + 1);
      } else {
        setPixel(variant, pixels, x, y + 2);
        setPixel(variant, pixels, x + 1, y + 2);
      }
    }
    for (int row = 0; row < variant.rows - 1; row++) {
      for (int column = 0; column < variant.columns - 1; column++) {
        if (
          connection(variant, visual.connections(), row, column, row, column + 1) == 1 &&
          connection(
            variant,
            visual.connections(),
            row + 1,
            column,
            row + 1,
            column + 1
          ) == 1 &&
          connection(variant, visual.connections(), row, column, row + 1, column) == 1 &&
          connection(
            variant,
            visual.connections(),
            row,
            column + 1,
            row + 1,
            column + 1
          ) == 1
        ) {
          setPixel(variant, pixels, 3 + column * 3, 3 + row * 3);
        }
      }
    }
    return new PixelGrid(
      variant.width,
      variant.pixelWidth,
      variant.pixelHeight,
      pixels,
      visual.background(),
      visual.foreground(),
      visual.style()
    );
  }

  private static void setPixel(
    Variant variant,
    byte[] pixels,
    int x,
    int y
  ) {
    pixels[y * variant.pixelWidth + x] = 1;
  }

  private static int connection(
    Variant variant,
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

  private static OklchColor color(ColorData color) {
    return new OklchColor(
      color.lightness(),
      color.chroma(),
      color.hue(),
      color.hex()
    );
  }

  private static Edge[] createEdges(Variant variant) {
    List<Edge> result = new ArrayList<>(variant.edgeCount);
    for (int row = 0; row < variant.rows; row++) {
      for (int column = 0; column < variant.columns; column++) {
        if (column + 1 < variant.columns) {
          result.add(new Edge(row, column, row, column + 1));
        }
        if (row + 1 < variant.rows) {
          result.add(new Edge(row, column, row + 1, column));
        }
      }
    }
    if (result.size() != variant.edgeCount) throw new IllegalStateException(
      "unexpected canonical edge count"
    );
    return result.toArray(Edge[]::new);
  }

  private static ModeDefinition[] createDefinitions32(Variant variant) {
    ModeDefinition[] result = new ModeDefinition[Mode.values().length];
    for (int index = 0; index < result.length; index++) {
      result[index] = createTemplateDefinition(variant, TEMPLATES_32[index]);
    }
    return result;
  }

  private static ModeDefinition createTemplateDefinition(
    Variant variant,
    String[][] template
  ) {
    String[][] references = new String[variant.rows][variant.columns];
    Map<String, Integer> sourcePositions = new HashMap<>();
    for (int row = 0; row < variant.rows; row++) {
      if (template[row].length != variant.columns) throw new IllegalStateException(
        "invalid BitSquiggle32 mode template"
      );
      for (int column = 0; column < variant.columns; column++) {
        String token = template[row][column];
        boolean copied = token.endsWith("'");
        String name = copied ? token.substring(0, token.length() - 1) : token;
        references[row][column] = name;
        if (!copied) sourcePositions.put(name, row * variant.columns + column);
      }
    }
    TreeMap<Integer, List<Integer>> grouped = new TreeMap<>();
    for (int edgeIndex = 0; edgeIndex < variant.edgeCount; edgeIndex++) {
      Edge edge = variant.edges[edgeIndex];
      Integer start = sourcePositions.get(
        references[edge.startRow()][edge.startColumn()]
      );
      Integer end = sourcePositions.get(
        references[edge.endRow()][edge.endColumn()]
      );
      if (start == null || end == null || start.equals(end)) {
        throw new IllegalStateException("invalid BitSquiggle32 mode edge");
      }
      int key = Math.min(start, end) * 64 + Math.max(start, end);
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edgeIndex);
    }
    ConnectionClass[] classes = grouped
      .values()
      .stream()
      .map(BitSquigglesCore::connectionClass)
      .toArray(ConnectionClass[]::new);
    return new ModeDefinition(classes, classes);
  }

  private static ModeDefinition[] createDefinitions40(Variant variant) {
    CellTransform[] transforms = {
      (row, column) -> new Cell(row, 6 - column),
      (row, column) -> new Cell(6 - row, column),
      (row, column) -> new Cell(6 - row, 6 - column),
      (row, column) -> new Cell(6 - column, 6 - row),
    };
    ModeDefinition[] result = new ModeDefinition[transforms.length];
    for (int index = 0; index < transforms.length; index++) {
      result[index] = createTransformDefinition(variant, transforms[index]);
    }
    return result;
  }

  private static ModeDefinition createTransformDefinition(
    Variant variant,
    CellTransform transform
  ) {
    TreeMap<Long, List<Integer>> grouped = new TreeMap<>();
    for (int index = 0; index < variant.edgeCount; index++) {
      Edge edge = variant.edges[index];
      Cell start = transform.apply(edge.startRow(), edge.startColumn());
      Cell end = transform.apply(edge.endRow(), edge.endColumn());
      Edge transformed = canonicalEdge(
        start.row(),
        start.column(),
        end.row(),
        end.column()
      );
      long key = Math.min(
        edgeKey(variant, edge),
        edgeKey(variant, transformed)
      );
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
    }
    List<ConnectionClass> classes = new ArrayList<>();
    List<ConnectionClass> usable = new ArrayList<>();
    for (List<Integer> occurrences : grouped.values()) {
      int[] indices = occurrences.stream().mapToInt(Integer::intValue).toArray();
      boolean excluded = false;
      for (int edgeIndex : indices) {
        if (isMarkerEdge(variant, edgeIndex)) excluded = true;
      }
      ConnectionClass connectionClass = new ConnectionClass(indices, excluded);
      classes.add(connectionClass);
      if (!excluded) usable.add(connectionClass);
    }
    return new ModeDefinition(
      classes.toArray(ConnectionClass[]::new),
      usable.toArray(ConnectionClass[]::new)
    );
  }

  private static ConnectionClass connectionClass(List<Integer> occurrences) {
    return new ConnectionClass(
      occurrences.stream().mapToInt(Integer::intValue).toArray(),
      false
    );
  }

  private static boolean isMarkerEdge(Variant variant, int edgeIndex) {
    for (int markerIndex : variant.markerIndices) {
      if (edgeIndex == markerIndex) return true;
    }
    return false;
  }

  private static Edge canonicalEdge(
    int startRow,
    int startColumn,
    int endRow,
    int endColumn
  ) {
    return startRow < endRow ||
        (startRow == endRow && startColumn < endColumn)
      ? new Edge(startRow, startColumn, endRow, endColumn)
      : new Edge(endRow, endColumn, startRow, startColumn);
  }

  private static long edgeKey(Variant variant, Edge edge) {
    return (
      (((long) edge.startRow() * variant.columns + edge.startColumn()) << 8) |
      (edge.endRow() * variant.columns + edge.endColumn())
    );
  }

  private static int edgeIndex(
    Variant variant,
    int startRow,
    int startColumn,
    int endRow,
    int endColumn
  ) {
    Edge edge = canonicalEdge(startRow, startColumn, endRow, endColumn);
    if (
      edge.startRow() < 0 ||
      edge.startColumn() < 0 ||
      edge.endRow() >= variant.rows ||
      edge.endColumn() >= variant.columns
    ) throw new IllegalArgumentException("not a canonical edge");
    if (edge.startRow() == edge.endRow()) {
      if (edge.endColumn() != edge.startColumn() + 1) {
        throw new IllegalArgumentException("not a canonical edge");
      }
      int rowOffset = edge.startRow() * (2 * variant.columns - 1);
      return rowOffset + (
        edge.startRow() == variant.rows - 1
          ? edge.startColumn()
          : 2 * edge.startColumn()
      );
    }
    if (
      edge.endRow() != edge.startRow() + 1 ||
      edge.startColumn() != edge.endColumn()
    ) throw new IllegalArgumentException("not a canonical edge");
    int columnOffset = edge.startColumn() == variant.columns - 1
      ? 2 * variant.columns - 2
      : 2 * edge.startColumn() + 1;
    return (
      edge.startRow() * (2 * variant.columns - 1) +
      columnOffset
    );
  }

  private static String[][] rows(String... rows) {
    String[][] result = new String[rows.length][];
    for (int index = 0; index < rows.length; index++) {
      result[index] = rows[index].split(" ");
    }
    return result;
  }

  private static SmoothBlob[] smoothBlobs(
    Variant variant,
    byte[] connections
  ) {
    validateConnections(variant, connections);
    BigInteger requiredEdges = BigInteger.ZERO;
    for (int index = 0; index < variant.edgeCount; index++) {
      if (connections[index] == 1) requiredEdges = requiredEdges.setBit(index);
    }
    long requiredJunctions = requiredJunctionMask(variant, requiredEdges);
    BigInteger coveredEdges = BigInteger.ZERO;
    long coveredJunctions = 0;
    List<SmoothBlob> result = new ArrayList<>();
    while (
      !coveredEdges.equals(requiredEdges) ||
      coveredJunctions != requiredJunctions
    ) {
      BigInteger remainingEdges = requiredEdges.andNot(coveredEdges);
      int anchorEdge = remainingEdges.signum() == 0
        ? -1
        : remainingEdges.getLowestSetBit();
      int anchorJunction = anchorEdge < 0
        ? Long.numberOfTrailingZeros(requiredJunctions & ~coveredJunctions)
        : -1;
      int anchorTop;
      int anchorLeft;
      int anchorBottom;
      int anchorRight;
      if (anchorEdge >= 0) {
        Edge edge = variant.edges[anchorEdge];
        anchorTop = edge.startRow();
        anchorLeft = edge.startColumn();
        anchorBottom = edge.endRow();
        anchorRight = edge.endColumn();
      } else {
        anchorTop = anchorJunction / (variant.columns - 1);
        anchorLeft = anchorJunction % (variant.columns - 1);
        anchorBottom = anchorTop + 1;
        anchorRight = anchorLeft + 1;
      }

      SmoothBlob best = null;
      BigInteger bestEdges = BigInteger.ZERO;
      long bestJunctions = 0;
      for (int top = anchorTop; top >= 0; top--) {
        for (int left = anchorLeft; left >= 0; left--) {
          for (int bottom = anchorBottom; bottom < variant.rows; bottom++) {
            for (
              int right = anchorRight;
              right < variant.columns;
              right++
            ) {
              BigInteger candidateEdges = connectedRectangleEdgeMask(
                variant,
                top,
                left,
                bottom,
                right,
                requiredEdges
              );
              if (candidateEdges == null) continue;
              long candidateJunctions = rectangleJunctionMask(
                variant,
                top,
                left,
                bottom,
                right,
                requiredJunctions
              );
              BigInteger newEdges = candidateEdges.andNot(coveredEdges);
              long newJunctions = candidateJunctions & ~coveredJunctions;
              if (newEdges.signum() == 0 && newJunctions == 0) continue;
              SmoothBlob candidate = new SmoothBlob(
                top,
                left,
                bottom,
                right
              );
              if (
                best == null ||
                isBetterBlob(
                  candidate,
                  newEdges,
                  newJunctions,
                  best,
                  bestEdges.andNot(coveredEdges),
                  bestJunctions & ~coveredJunctions
                )
              ) {
                best = candidate;
                bestEdges = candidateEdges;
                bestJunctions = candidateJunctions;
              }
            }
          }
        }
      }
      if (best == null) throw new IllegalStateException(
        "uncoverable smooth feature"
      );
      result.add(best);
      coveredEdges = coveredEdges.or(bestEdges);
      coveredJunctions |= bestJunctions;
    }
    return result.toArray(SmoothBlob[]::new);
  }

  private static void validateConnections(
    Variant variant,
    byte[] connections
  ) {
    if (
      connections == null || connections.length != variant.edgeCount
    ) throw new IllegalArgumentException(
      "connections must contain " + variant.edgeCount + " entries"
    );
    for (byte value : connections) {
      if (value != 0 && value != 1) throw new IllegalArgumentException(
        "connections must contain only zeroes and ones"
      );
    }
  }

  private static long requiredJunctionMask(
    Variant variant,
    BigInteger requiredEdges
  ) {
    long result = 0;
    for (int row = 0; row < variant.rows - 1; row++) {
      for (int column = 0; column < variant.columns - 1; column++) {
        if (
          connectedRectangleEdgeMask(
            variant,
            row,
            column,
            row + 1,
            column + 1,
            requiredEdges
          ) != null
        ) result |= 1L << junctionIndex(variant, row, column);
      }
    }
    return result;
  }

  private static BigInteger connectedRectangleEdgeMask(
    Variant variant,
    int top,
    int left,
    int bottom,
    int right,
    BigInteger requiredEdges
  ) {
    BigInteger result = BigInteger.ZERO;
    for (int row = top; row <= bottom; row++) {
      for (int column = left; column <= right; column++) {
        if (column < right) {
          int edge = edgeIndex(variant, row, column, row, column + 1);
          if (!requiredEdges.testBit(edge)) return null;
          result = result.setBit(edge);
        }
        if (row < bottom) {
          int edge = edgeIndex(variant, row, column, row + 1, column);
          if (!requiredEdges.testBit(edge)) return null;
          result = result.setBit(edge);
        }
      }
    }
    return result;
  }

  private static long rectangleJunctionMask(
    Variant variant,
    int top,
    int left,
    int bottom,
    int right,
    long requiredJunctions
  ) {
    long result = 0;
    for (int row = top; row < bottom; row++) {
      for (int column = left; column < right; column++) {
        long bit = 1L << junctionIndex(variant, row, column);
        if ((requiredJunctions & bit) != 0) result |= bit;
      }
    }
    return result;
  }

  private static int junctionIndex(
    Variant variant,
    int row,
    int column
  ) {
    return row * (variant.columns - 1) + column;
  }

  private static boolean isBetterBlob(
    SmoothBlob candidate,
    BigInteger candidateEdges,
    long candidateJunctions,
    SmoothBlob best,
    BigInteger bestEdges,
    long bestJunctions
  ) {
    int candidateJunctionCount = Long.bitCount(candidateJunctions);
    int bestJunctionCount = Long.bitCount(bestJunctions);
    if (candidateJunctionCount != bestJunctionCount) {
      return candidateJunctionCount > bestJunctionCount;
    }
    int candidateEdgeCount = candidateEdges.bitCount();
    int bestEdgeCount = bestEdges.bitCount();
    if (candidateEdgeCount != bestEdgeCount) {
      return candidateEdgeCount > bestEdgeCount;
    }
    int candidateArea =
      (candidate.bottomRow() - candidate.topRow() + 1) *
      (candidate.rightColumn() - candidate.leftColumn() + 1);
    int bestArea =
      (best.bottomRow() - best.topRow() + 1) *
      (best.rightColumn() - best.leftColumn() + 1);
    if (candidateArea != bestArea) return candidateArea < bestArea;
    if (candidate.topRow() != best.topRow()) {
      return candidate.topRow() < best.topRow();
    }
    if (candidate.leftColumn() != best.leftColumn()) {
      return candidate.leftColumn() < best.leftColumn();
    }
    if (candidate.bottomRow() != best.bottomRow()) {
      return candidate.bottomRow() < best.bottomRow();
    }
    return candidate.rightColumn() < best.rightColumn();
  }

  static Colors deriveColors(long mixed, long input, int style) {
    double hue = ((mixed >>> 12) & 15) * 22.5;
    double chroma = 0.05 + ((mixed >>> 8) & 15) * (0.20 / 15.0);
    double base = 0.50 + (mixed & 3) * (0.20 / 3.0);
    double foregroundL = base;
    double backgroundL = foregroundL - 0.50;
    double foregroundC = chroma;
    double backgroundC = chroma;
    if (style != 0) {
      foregroundL = style == 3 ? 1.0 : base + 0.30;
      backgroundL = style == 3 ? 0.0 : foregroundL - 0.80;
      foregroundC = style == 2 || style == 3 ? 0.0 : chroma + 0.10;
      backgroundC = foregroundC;
    }
    foregroundL = clamp(foregroundL);
    backgroundL = clamp(backgroundL);
    if ((Long.bitCount(input) & 1) != 0) {
      double temporary = foregroundL;
      foregroundL = backgroundL;
      backgroundL = temporary;
    }
    return new Colors(
      makeColor(backgroundL, backgroundC, (hue + 180) % 360),
      makeColor(foregroundL, foregroundC, hue)
    );
  }

  private static ColorData makeColor(
    double lightness,
    double chroma,
    double hue
  ) {
    double radians = Math.toRadians(hue);
    double a = chroma * Math.cos(radians);
    double b = chroma * Math.sin(radians);
    double l = lightness + .3963377774 * a + .2158037573 * b;
    double m = lightness - .1055613458 * a - .0638541728 * b;
    double s = lightness - .0894841775 * a - 1.2914855480 * b;
    double red = srgb(
      4.0767416621 * l * l * l -
        3.3077115913 * m * m * m +
        .2309699292 * s * s * s
    );
    double green = srgb(
      -1.2684380046 * l * l * l +
        2.6097574011 * m * m * m -
        .3413193965 * s * s * s
    );
    double blue = srgb(
      -.0041960863 * l * l * l -
        .7034186147 * m * m * m +
        1.707614701 * s * s * s
    );
    String hex = String.format(
      "#%02x%02x%02x",
      Math.round(red * 255),
      Math.round(green * 255),
      Math.round(blue * 255)
    );
    return new ColorData(lightness, chroma, hue, hex);
  }

  private static double srgb(double value) {
    value = clamp(value);
    return value <= .0031308
      ? 12.92 * value
      : 1.055 * Math.pow(value, 1 / 2.4) - .055;
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }
}
