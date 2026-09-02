// Grug 2-Clause License
// 1. do what want
// 2. not sue grug

package bitsquiggles.renderer.swing;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class BitSquigglesRendererSwingTest {

  private static int checks;

  private BitSquigglesRendererSwingTest() {}

  public static void main(String[] args) {
    testFacade32();
    testFacade40();
    testRendering32();
    testRendering40();
    testValidation();
    System.out.println(
      "BitSquiggles Swing renderer tests passed (" + checks + " checks)"
    );
  }

  private static void testFacade32() {
    int input = 0x89ABCDEF;
    BitSquigglesRendererSwing.VisSpec visual = BitSquigglesRendererSwing.spec32(
      input,
      BitSquigglesRendererSwing.Style.BLACK_AND_WHITE
    );
    BitSquigglesRendererSwing.PixelGrid grid = BitSquigglesRendererSwing.pixels32(
      input,
      BitSquigglesRendererSwing.Style.BLACK_AND_WHITE
    );
    check(
      visual.variant() == BitSquigglesRendererSwing.Width.BITS_32 &&
      visual.input() == Integer.toUnsignedLong(input) &&
      visual.style() == BitSquigglesRendererSwing.Style.BLACK_AND_WHITE,
      "32-bit styled spec"
    );
    check(
      grid.variant() == BitSquigglesRendererSwing.Width.BITS_32 &&
      grid.style() == BitSquigglesRendererSwing.Style.BLACK_AND_WHITE,
      "32-bit styled pixels"
    );
    check(
      sameSpec(
        BitSquigglesRendererSwing.spec32(input),
        BitSquigglesRendererSwing.spec32(
          input,
          BitSquigglesRendererSwing.Style.STANDARD
        )
      ) &&
      sameGrid(
        BitSquigglesRendererSwing.pixels32(input),
        BitSquigglesRendererSwing.pixels32(
          input,
          BitSquigglesRendererSwing.Style.STANDARD
        )
      ),
      "32-bit default style"
    );
  }

  private static void testFacade40() {
    long input = 0x39527804DBL;
    BitSquigglesRendererSwing.VisSpec visual = BitSquigglesRendererSwing.spec40(
      input,
      BitSquigglesRendererSwing.Style.BLACK_AND_WHITE
    );
    BitSquigglesRendererSwing.PixelGrid grid = BitSquigglesRendererSwing.pixels40(
      input,
      BitSquigglesRendererSwing.Style.BLACK_AND_WHITE
    );
    check(
      visual.variant() == BitSquigglesRendererSwing.Width.BITS_40 &&
      visual.input() == input &&
      visual.style() == BitSquigglesRendererSwing.Style.BLACK_AND_WHITE,
      "40-bit styled spec"
    );
    check(
      grid.variant() == BitSquigglesRendererSwing.Width.BITS_40 &&
      grid.style() == BitSquigglesRendererSwing.Style.BLACK_AND_WHITE,
      "40-bit styled pixels"
    );
    check(
      sameSpec(
        BitSquigglesRendererSwing.spec40(input),
        BitSquigglesRendererSwing.spec40(
          input,
          BitSquigglesRendererSwing.Style.STANDARD
        )
      ) &&
      sameGrid(
        BitSquigglesRendererSwing.pixels40(input),
        BitSquigglesRendererSwing.pixels40(
          input,
          BitSquigglesRendererSwing.Style.STANDARD
        )
      ),
      "40-bit default style"
    );
    check(
      BitSquigglesRendererSwing.bip380ChecksumInput("89f8spxm") == input,
      "40-bit BIP380 adapter"
    );
  }

  private static void testRendering32() {
    assertSmoothPainted(BitSquigglesRendererSwing.spec32(0x12345678));
    assertRasterPainted(BitSquigglesRendererSwing.pixels32(0x12345678));
  }

  private static void testRendering40() {
    assertSmoothPainted(BitSquigglesRendererSwing.spec40(0x39527804DBL));
    assertRasterPainted(BitSquigglesRendererSwing.pixels40(0x39527804DBL));
  }

  private static void assertSmoothPainted(
    BitSquigglesRendererSwing.VisSpec visual
  ) {
    BufferedImage image = new BufferedImage(
      220,
      220,
      BufferedImage.TYPE_INT_ARGB
    );
    Graphics2D graphics = image.createGraphics();
    try {
      if (visual.variant() == BitSquigglesRendererSwing.Width.BITS_32) {
        BitSquigglesRendererSwing.renderSmooth32(
          graphics,
          visual,
          220,
          220
        );
      } else {
        BitSquigglesRendererSwing.renderSmooth40(
          graphics,
          visual,
          220,
          220
        );
      }
    } finally {
      graphics.dispose();
    }
    check(countOpaquePixels(image) > 0, "smooth renderer paints output");
  }

  private static void assertRasterPainted(
    BitSquigglesRendererSwing.PixelGrid grid
  ) {
    int pixelSize = 4;
    BufferedImage image = new BufferedImage(
      grid.width() * pixelSize,
      grid.height() * pixelSize,
      BufferedImage.TYPE_INT_ARGB
    );
    Graphics2D graphics = image.createGraphics();
    try {
      if (grid.variant() == BitSquigglesRendererSwing.Width.BITS_32) {
        BitSquigglesRendererSwing.renderRaster32(graphics, grid, pixelSize);
      } else {
        BitSquigglesRendererSwing.renderRaster40(graphics, grid, pixelSize);
      }
    } finally {
      graphics.dispose();
    }
    check(
      countOpaquePixels(image) == image.getWidth() * image.getHeight(),
      "raster renderer paints every target pixel"
    );
  }

  private static void testValidation() {
    BitSquigglesRendererSwing.VisSpec visual32 =
      BitSquigglesRendererSwing.spec32(0);
    BitSquigglesRendererSwing.VisSpec visual40 =
      BitSquigglesRendererSwing.spec40(0);
    BitSquigglesRendererSwing.PixelGrid pixels32 =
      BitSquigglesRendererSwing.pixels32(0);
    BitSquigglesRendererSwing.PixelGrid pixels40 =
      BitSquigglesRendererSwing.pixels40(0);
    expectFailure(() -> BitSquigglesRendererSwing.spec40(-1));
    expectFailure(() -> BitSquigglesRendererSwing.pixels40(0x10000000000L));
    expectFailure(
      () -> BitSquigglesRendererSwing.renderSmooth32(null, visual40, 10, 10)
    );
    expectFailure(
      () -> BitSquigglesRendererSwing.renderSmooth40(null, visual32, 10, 10)
    );
    expectFailure(
      () -> BitSquigglesRendererSwing.renderRaster32(null, pixels40, 1)
    );
    expectFailure(
      () -> BitSquigglesRendererSwing.renderRaster40(null, pixels32, 1)
    );
    expectFailure(
      () -> BitSquigglesRendererSwing.renderRaster32(null, pixels32, 0)
    );
    expectFailure(
      () -> BitSquigglesRendererSwing.renderRaster40(null, pixels40, -1)
    );
  }

  private static int countOpaquePixels(BufferedImage image) {
    int result = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) result++;
      }
    }
    return result;
  }

  private static boolean sameSpec(
    BitSquigglesRendererSwing.VisSpec first,
    BitSquigglesRendererSwing.VisSpec second
  ) {
    return (
      first.variant() == second.variant() &&
      first.input() == second.input() &&
      first.mixed() == second.mixed() &&
      Arrays.equals(first.connections(), second.connections()) &&
      Arrays.deepEquals(first.cells(), second.cells()) &&
      first.background().equals(second.background()) &&
      first.foreground().equals(second.foreground()) &&
      first.style() == second.style() &&
      first.preferredMode() == second.preferredMode() &&
      first.actualMode() == second.actualMode() &&
      first.fallback() == second.fallback() &&
      first.luminanceIndex() == second.luminanceIndex() &&
      first.swapped() == second.swapped()
    );
  }

  private static boolean sameGrid(
    BitSquigglesRendererSwing.PixelGrid first,
    BitSquigglesRendererSwing.PixelGrid second
  ) {
    return (
      first.variant() == second.variant() &&
      first.width() == second.width() &&
      first.height() == second.height() &&
      Arrays.equals(first.pixels(), second.pixels()) &&
      first.background().equals(second.background()) &&
      first.foreground().equals(second.foreground()) &&
      first.style() == second.style()
    );
  }

  private static void expectFailure(Runnable action) {
    try {
      action.run();
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      checks++;
    }
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}