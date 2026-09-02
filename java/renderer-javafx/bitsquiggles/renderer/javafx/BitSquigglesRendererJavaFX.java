// BitSquiggles — optional JavaFX renderer.
//
// Grug 2-Clause License
// 1. do what want
// 2. not sue grug

package bitsquiggles.renderer.javafx;

import bitsquiggles.BitSquiggles;
import bitsquiggles.internal.BitSquigglesCore;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Optional JavaFX renderer for both BitSquiggles widths. */
public final class BitSquigglesRendererJavaFX implements BitSquiggles {

  private BitSquigglesRendererJavaFX() {}

  public static BitSquiggles.VisSpec spec32(
    int input,
    BitSquiggles.Style style
  ) {
    return BitSquigglesCore.spec(
      BitSquiggles.Width.BITS_32,
      Integer.toUnsignedLong(input),
      style
    );
  }

  public static BitSquiggles.VisSpec spec32(int input) {
    return spec32(input, BitSquiggles.Style.STANDARD);
  }

  public static BitSquiggles.VisSpec spec40(
    long input,
    BitSquiggles.Style style
  ) {
    return BitSquigglesCore.spec(BitSquiggles.Width.BITS_40, input, style);
  }

  public static BitSquiggles.VisSpec spec40(long input) {
    return spec40(input, BitSquiggles.Style.STANDARD);
  }

  public static BitSquiggles.PixelGrid pixels32(
    int input,
    BitSquiggles.Style style
  ) {
    return BitSquigglesCore.pixels(
      BitSquiggles.Width.BITS_32,
      Integer.toUnsignedLong(input),
      style
    );
  }

  public static BitSquiggles.PixelGrid pixels32(int input) {
    return pixels32(input, BitSquiggles.Style.STANDARD);
  }

  public static BitSquiggles.PixelGrid pixels40(
    long input,
    BitSquiggles.Style style
  ) {
    return BitSquigglesCore.pixels(BitSquiggles.Width.BITS_40, input, style);
  }

  public static BitSquiggles.PixelGrid pixels40(long input) {
    return pixels40(input, BitSquiggles.Style.STANDARD);
  }

  public static long bip380ChecksumInput(String checksum) {
    return BitSquigglesCore.bip380ChecksumInput(checksum);
  }

  /** Paint a smooth 32-bit presentation in the available bounds. */
  public static void renderSmooth32(
    GraphicsContext graphics,
    BitSquiggles.VisSpec spec,
    double width,
    double height
  ) {
    renderSmoothForWidth(
      graphics,
      spec,
      BitSquiggles.Width.BITS_32,
      width,
      height
    );
  }

  /** Paint a smooth 40-bit presentation in the available bounds. */
  public static void renderSmooth40(
    GraphicsContext graphics,
    BitSquiggles.VisSpec spec,
    double width,
    double height
  ) {
    renderSmoothForWidth(
      graphics,
      spec,
      BitSquiggles.Width.BITS_40,
      width,
      height
    );
  }

  /** Paint an exact 32-bit pixel grid using whole target pixels. */
  public static void renderRaster32(
    GraphicsContext graphics,
    BitSquiggles.PixelGrid grid,
    int pixelSize
  ) {
    renderRasterForWidth(
      graphics,
      grid,
      BitSquiggles.Width.BITS_32,
      pixelSize
    );
  }

  /** Paint an exact 40-bit pixel grid using whole target pixels. */
  public static void renderRaster40(
    GraphicsContext graphics,
    BitSquiggles.PixelGrid grid,
    int pixelSize
  ) {
    renderRasterForWidth(
      graphics,
      grid,
      BitSquiggles.Width.BITS_40,
      pixelSize
    );
  }

  private static void renderSmoothForWidth(
    GraphicsContext graphics,
    BitSquiggles.VisSpec spec,
    BitSquiggles.Width expectedWidth,
    double width,
    double height
  ) {
    requireWidth(spec, expectedWidth);
    if (width <= 0 || height <= 0) return;

    int pixelWidth = BitSquigglesCore.pixelWidth(expectedWidth);
    int pixelHeight = BitSquigglesCore.pixelHeight(expectedWidth);
    double scale = Math.min(width / pixelWidth, height / pixelHeight);
    double scaledWidth = pixelWidth * scale;
    double scaledHeight = pixelHeight * scale;
    double offsetX = (width - scaledWidth) / 2.0;
    double offsetY = (height - scaledHeight) / 2.0;

    graphics.setFill(Color.web(spec.background().hex()));
    graphics.fillRoundRect(
      offsetX,
      offsetY,
      scaledWidth,
      scaledHeight,
      2.0 * scale,
      2.0 * scale
    );

    graphics.setFill(Color.web(spec.foreground().hex()));
    for (BitSquigglesCore.SmoothBlob blob : BitSquigglesCore.smoothBlobs(
      expectedWidth,
      spec.connections()
    )) {
      graphics.fillRoundRect(
        offsetX + (1 + 3 * blob.leftColumn()) * scale,
        offsetY + (1 + 3 * blob.topRow()) * scale,
        (2 + 3 * (blob.rightColumn() - blob.leftColumn())) * scale,
        (2 + 3 * (blob.bottomRow() - blob.topRow())) * scale,
        2 * scale,
        2 * scale
      );
    }
  }

  private static void renderRasterForWidth(
    GraphicsContext graphics,
    BitSquiggles.PixelGrid grid,
    BitSquiggles.Width expectedWidth,
    int pixelSize
  ) {
    requireWidth(grid, expectedWidth);
    if (pixelSize <= 0) {
      throw new IllegalArgumentException("pixelSize must be positive");
    }

    int width = grid.width();
    int height = grid.height();
    byte[] pixels = grid.pixels();
    graphics.setFill(Color.web(grid.background().hex()));
    graphics.fillRect(0, 0, width * pixelSize, height * pixelSize);
    graphics.setFill(Color.web(grid.foreground().hex()));
    for (int row = 0; row < height; row++) {
      for (int column = 0; column < width; column++) {
        if (pixels[row * width + column] != 0) {
          graphics.fillRect(
            column * pixelSize,
            row * pixelSize,
            pixelSize,
            pixelSize
          );
        }
      }
    }
  }

  private static void requireWidth(
    BitSquiggles.VisSpec spec,
    BitSquiggles.Width expectedWidth
  ) {
    if (spec == null || spec.variant() != expectedWidth) {
      throw new IllegalArgumentException(
        widthLabel(expectedWidth) + " visual specification required"
      );
    }
  }

  private static void requireWidth(
    BitSquiggles.PixelGrid grid,
    BitSquiggles.Width expectedWidth
  ) {
    if (grid == null || grid.variant() != expectedWidth) {
      throw new IllegalArgumentException(
        widthLabel(expectedWidth) + " pixel grid required"
      );
    }
  }

  private static String widthLabel(BitSquiggles.Width width) {
    return width == BitSquiggles.Width.BITS_32 ? "32-bit" : "40-bit";
  }
}