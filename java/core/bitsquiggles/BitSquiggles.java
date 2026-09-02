package bitsquiggles;

/** Shared public data model for BitSquiggle32 and BitSquiggle40 renderers. */
public interface BitSquiggles {

  public enum Width {
    BITS_32,
    BITS_40,
  }

  public enum Style {
    STANDARD,
    HIGH_CONTRAST,
    MONOCHROME,
    BLACK_AND_WHITE,
  }

  public enum Mode {
    LEFT_RIGHT("A|"),
    TOP_BOTTOM("A-"),
    HALF_TURN("A+"),
    DIAGONAL_SLASH("A/");

    private final String label;

    Mode(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  public record OklchColor(double L, double C, double h, String hex) {}

  public record VisSpec(
    Width variant,
    long input,
    long mixed,
    byte[] connections,
    int[][] cells,
    OklchColor background,
    OklchColor foreground,
    Style style,
    Mode preferredMode,
    Mode actualMode,
    boolean fallback,
    int luminanceIndex,
    boolean swapped
  ) {}

  public record PixelGrid(
    Width variant,
    int width,
    int height,
    byte[] pixels,
    OklchColor background,
    OklchColor foreground,
    Style style
  ) {}
}
