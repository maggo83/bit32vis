/* C99 BitSquiggles framebuffer renderer tests.
 *
 * The renderer header is the test client's only BitSquiggles include.
 * Grug 2-Clause License: do what want; not sue grug.
 */
#include "bitsquiggles_renderer_framebuffer.h"

#include <stdbool.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TEST_MAX_SCALE 2u
#define TEST_X 3u
#define TEST_Y 2u
#define TEST_MAX_WIDTH \
  (TEST_X + BITSQUIGGLES_MAX_PIXEL_WIDTH * TEST_MAX_SCALE)
#define TEST_MAX_HEIGHT \
  (TEST_Y + BITSQUIGGLES_PIXEL_HEIGHT * TEST_MAX_SCALE)

typedef struct {
  unsigned int width;
  unsigned int height;
  unsigned int calls;
  char foreground[8];
  uint8_t pixels[TEST_MAX_WIDTH * TEST_MAX_HEIGHT];
} RecordingFramebuffer;

typedef int (*RasterRenderer)(BitsquigglesFillRect fill_rect, void *context,
                              const BitsquigglesPixelGrid *grid,
                              unsigned int x, unsigned int y,
                              unsigned int scale);

static unsigned long checks = 0;

static void check(bool condition, const char *message) {
  ++checks;
  if (!condition) {
    fprintf(stderr, "C99 framebuffer renderer test failed: %s\n", message);
    exit(1);
  }
}

static int fill_rect(void *context, unsigned int x, unsigned int y,
                     unsigned int width, unsigned int height,
                     const BitsquigglesColor *color) {
  RecordingFramebuffer *target = (RecordingFramebuffer *)context;
  unsigned int row;
  unsigned int column;
  uint8_t value;

  if (target == NULL || color == NULL || x > target->width ||
      y > target->height || width > target->width - x ||
      height > target->height - y)
    return -1;
  value = (uint8_t)(strcmp(color->hex, target->foreground) == 0);
  for (row = y; row < y + height; ++row) {
    for (column = x; column < x + width; ++column)
      target->pixels[row * target->width + column] = value;
  }
  ++target->calls;
  return 0;
}

static int fail_fill(void *context, unsigned int x, unsigned int y,
                     unsigned int width, unsigned int height,
                     const BitsquigglesColor *color) {
  (void)context;
  (void)x;
  (void)y;
  (void)width;
  (void)height;
  (void)color;
  return -1;
}

static void assert_rendered(const BitsquigglesPixelGrid *grid,
                            RasterRenderer renderer) {
  RecordingFramebuffer target = {0};
  unsigned int source_row;
  unsigned int source_column;
  unsigned int offset_row;
  unsigned int offset_column;

    target.width = TEST_X + grid->width * TEST_MAX_SCALE;
    target.height = TEST_Y + grid->height * TEST_MAX_SCALE;
    memset(target.pixels, 2, sizeof(target.pixels));
  memcpy(target.foreground, grid->foreground.hex,
         sizeof(target.foreground));
    check(renderer(fill_rect, &target, grid, TEST_X, TEST_Y, TEST_MAX_SCALE) == 0,
        "render succeeds");
  check(target.calls > 1u, "renderer paints background and foreground");
    check(target.pixels[0] == 2u, "renderer preserves pixels outside its bounds");
  for (source_row = 0; source_row < grid->height; ++source_row) {
    for (source_column = 0; source_column < grid->width; ++source_column) {
      uint8_t expected =
          grid->pixels[source_row * grid->width + source_column];
      for (offset_row = 0; offset_row < TEST_MAX_SCALE; ++offset_row) {
        for (offset_column = 0; offset_column < TEST_MAX_SCALE;
             ++offset_column) {
          unsigned int output_row =
              TEST_Y + source_row * TEST_MAX_SCALE + offset_row;
          unsigned int output_column =
              TEST_X + source_column * TEST_MAX_SCALE + offset_column;
          check(target.pixels[output_row * target.width + output_column] ==
                    expected,
                "renderer preserves every scaled pixel");
        }
      }
    }
  }
}

static void test_application_facade(void) {
  BitsquigglesSpec visual32;
  BitsquigglesSpec visual40;
  BitsquigglesPixelGrid grid32;
  BitsquigglesPixelGrid grid40;
  BitsquigglesPixelGrid invalid_grid;
  uint64_t input40;

  check(bitsquiggles_spec32(UINT32_C(0x89abcdef), BITSQUIGGLES_STANDARD,
                            &visual32) == 0,
        "32-bit spec facade");
  check(visual32.mixed == UINT32_C(0x47ac5876), "32-bit facade output");
  check(bitsquiggles_bip380_checksum_input("89f8spxm", &input40) == 0 &&
            input40 == UINT64_C(0x39527804db),
        "unsuffixed BIP380 facade");
  check(bitsquiggles_spec40(input40, BITSQUIGGLES_STANDARD, &visual40) == 0,
        "40-bit spec facade");
  check(visual40.mixed == UINT64_C(0x34b1a077c8),
        "40-bit facade output");
  check(bitsquiggles_pixels32(UINT32_C(0x89abcdef),
                              BITSQUIGGLES_BLACK_AND_WHITE, &grid32) == 0,
        "32-bit pixels facade");
  check(bitsquiggles_pixels40(input40, BITSQUIGGLES_BLACK_AND_WHITE,
                              &grid40) == 0,
        "40-bit pixels facade");

  assert_rendered(&grid32, bitsquiggles_render_raster32);
  assert_rendered(&grid40, bitsquiggles_render_raster40);

  check(bitsquiggles_render_raster32(fill_rect, NULL, &grid40, 0u, 0u,
                                     1u) == -1,
        "32-bit renderer rejects 40-bit grid");
  check(bitsquiggles_render_raster40(fill_rect, NULL, &grid32, 0u, 0u,
                                     1u) == -1,
        "40-bit renderer rejects 32-bit grid");
  check(bitsquiggles_render_raster32(fill_rect, NULL, &grid32, 0u, 0u,
                                     0u) == -1,
        "renderer rejects zero scale");
  check(bitsquiggles_render_raster32(NULL, NULL, &grid32, 0u, 0u, 1u) ==
            -1,
        "renderer rejects null callback");
  check(bitsquiggles_render_raster32(fail_fill, NULL, &grid32, 0u, 0u, 1u) ==
            -1,
        "renderer propagates callback failure");
    invalid_grid = grid32;
    invalid_grid.pixels[0] = 2u;
    check(bitsquiggles_render_raster32(fill_rect, NULL, &invalid_grid, 0u, 0u,
               1u) == -1,
      "renderer rejects nonbinary pixels");
    check(bitsquiggles_render_raster32(fill_rect, NULL, &grid32, UINT_MAX, 0u,
               1u) == -1,
      "renderer rejects coordinate overflow");
}

int main(void) {
  test_application_facade();
  printf("BitSquiggles C99 framebuffer renderer tests passed (%lu checks)\n",
         checks);
  return 0;
}
