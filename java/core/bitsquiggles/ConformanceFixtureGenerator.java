// BitSquiggles — deterministic cross-language conformance-fixture generator.
// Grug 2-Clause License
// 1. do what want
// 2. not sue grug

package bitsquiggles;

import bitsquiggles.internal.BitSquigglesCore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Generates versioned test vectors directly from the Java reference implementation. */
public final class ConformanceFixtureGenerator {

  private static final Path OUTPUT_32 = Path.of("fixtures", "v1-32.json");
  private static final Path OUTPUT_40 = Path.of("fixtures", "v1-40.json");

  private ConformanceFixtureGenerator() {}

  /** Run with {@code --check} to reject stale committed conformance fixtures. */
  public static void main(String[] args) throws IOException {
    boolean check = args.length == 1 && args[0].equals("--check");
    if (args.length > 1 || (args.length == 1 && !check)) {
      throw new IllegalArgumentException(
        "usage: ConformanceFixtureGenerator [--check]"
      );
    }
    for (BitSquiggles.Width width : BitSquiggles.Width.values()) {
      Path output = width == BitSquiggles.Width.BITS_32
        ? OUTPUT_32
        : OUTPUT_40;
      String expected = generate(width);
      if (check) {
        if (
          !Files.isRegularFile(output) ||
          !Files.readString(output, StandardCharsets.UTF_8).equals(expected)
        ) {
          throw new IllegalStateException("stale conformance fixture: " + output);
        }
      } else {
        Files.createDirectories(output.getParent());
        Files.writeString(output, expected, StandardCharsets.UTF_8);
      }
    }
  }

  private static String generate(BitSquiggles.Width width) {
    StringBuilder json = new StringBuilder(
      width == BitSquiggles.Width.BITS_32 ? 300_000 : 400_000
    );
    json
      .append(
        "{\n  \"schema\": \"bitsquiggles-conformance\",\n  \"version\": 1,\n"
      );
    if (width == BitSquiggles.Width.BITS_40) {
      json.append("  \"variant\": 40,\n");
    }
    json
      .append("  \"dimensions\": {\"rows\": ")
      .append(BitSquigglesCore.rows(width))
      .append(", \"columns\": ")
      .append(BitSquigglesCore.columns(width))
      .append(", \"edges\": ")
      .append(BitSquigglesCore.edgeCount(width))
      .append(", \"pixelWidth\": ")
      .append(BitSquigglesCore.pixelWidth(width))
      .append(", \"pixelHeight\": ")
      .append(BitSquigglesCore.pixelHeight(width))
      .append("},\n")
      .append(
        "  \"styles\": [\"standard\", \"high-contrast\", \"monochrome\", \"black-and-white\"],\n"
      )
      .append("  \"vectors\": [\n");
    boolean first = true;
    if (width == BitSquiggles.Width.BITS_32) {
      for (int input : corpus32()) {
        if (!first) json.append(",\n");
        first = false;
        appendVector(json, width, Integer.toUnsignedLong(input));
      }
    } else {
      for (long input : corpus40()) {
        if (!first) json.append(",\n");
        first = false;
        appendVector(json, width, input);
      }
    }
    return json.append("\n  ]\n}\n").toString();
  }

  /**
   * A compact, deterministic sample of the full 32-bit domain. LinkedHashSet
   * preserves fixture order while removing values shared by multiple groups.
   */
  private static Set<Integer> corpus32() {
    Set<Integer> inputs = new LinkedHashSet<>();
    for (int input = 0; input < 1_024; input++) inputs.add(input);

    for (int bit = 0; bit < 32; bit++) {
      int oneBit = 1 << bit;
      inputs.add(oneBit);
      inputs.add(~oneBit);
    }

    int[] recognizable = {
      0xAAAAAAAA,
      0x55555555,
      0x7FFFFFFF,
      0x80000000,
      0x12345678,
      0x89ABCDEF,
      0xD9ABCDEF,
      0xFFFFFFFF,
    };
    for (int input : recognizable) inputs.add(input);

    int state = 0x6D2B79F5;
    for (int index = 0; index < 4_096; index++) {
      state = xorshift32(state);
      inputs.add(state);
    }
    return inputs;
  }

  private static Set<Long> corpus40() {
    Set<Long> inputs = new LinkedHashSet<>();
    for (long input = 0; input < 256; input++) inputs.add(input);
    for (int bit = 0; bit < 40; bit++) {
      inputs.add(1L << bit);
      inputs.add(0xFFFFFFFFFFL ^ (1L << bit));
    }
    long[] recognizable = {
      0x39527804DBL,
      0xA7912DEF7BL,
      0x08A1A65B0CL,
      0x500181B841L,
      0xAAAAAAAAAAL,
      0x5555555555L,
      0xFFFFFFFFFFL,
    };
    for (long input : recognizable) inputs.add(input);

    long state = 0x6D2B79F5L;
    for (int index = 0; index < 1_024; index++) {
      state ^= state << 13;
      state ^= state >>> 7;
      state ^= state << 17;
      state &= 0xFFFFFFFFFFL;
      inputs.add(state);
    }
    return inputs;
  }

  private static int xorshift32(int value) {
    value ^= value << 13;
    value ^= value >>> 17;
    return value ^ (value << 5);
  }

  private static void appendVector(
    StringBuilder json,
    BitSquiggles.Width width,
    long input
  ) {
    BitSquiggles.VisSpec standard = BitSquigglesCore.spec(width, input);
    BitSquiggles.PixelGrid raster = BitSquigglesCore.pixels(width, input);
    json
      .append("    {\"input\":\"")
      .append(hex(width, input))
      .append("\",\"mixed\":\"")
      .append(hex(width, standard.mixed()))
      .append("\",\"connections\":\"")
      .append(bits(standard.connections()))
      .append("\",\"pixels\":\"")
      .append(bits(raster.pixels()))
      .append("\",\"preferredMode\":\"")
      .append(standard.preferredMode().label())
      .append("\",\"actualMode\":\"")
      .append(standard.actualMode().label())
      .append("\",\"fallback\":")
      .append(standard.fallback())
      .append(",\"styles\":{");
    for (
      int index = 0;
      index < BitSquiggles.Style.values().length;
      index++
    ) {
      if (index != 0) json.append(',');
      BitSquiggles.Style style = BitSquiggles.Style.values()[index];
      BitSquiggles.VisSpec visual = BitSquigglesCore.spec(
        width,
        input,
        style
      );
      json
        .append('"')
        .append(style.name().toLowerCase().replace('_', '-'))
        .append("\":{\"background\":\"")
        .append(visual.background().hex())
        .append("\",\"foreground\":\"")
        .append(visual.foreground().hex())
        .append("\"}");
    }
    json.append("}}");
  }

  private static String hex(BitSquiggles.Width width, long value) {
    return width == BitSquiggles.Width.BITS_32
      ? String.format("%08x", (int) value)
      : String.format("%010x", value);
  }

  private static String bits(byte[] values) {
    StringBuilder bits = new StringBuilder(values.length);
    for (byte value : values) bits.append(value);
    return bits.toString();
  }
}
