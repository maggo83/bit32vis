#include "bitsquiggles_renderer_framebuffer.h"

#include <limits.h>
#include <stddef.h>

static int render_raster(BitsquigglesWidth width,
                         BitsquigglesFillRect fill_rect, void *context,
                         const BitsquigglesPixelGrid *grid, unsigned int x,
                         unsigned int y, unsigned int scale) {
  unsigned int expected_width;
  unsigned int output_width;
  unsigned int output_height;
  unsigned int pixel_count;
  unsigned int index;
  unsigned int column;

  if (width == BITSQUIGGLES_32)
    expected_width = BITSQUIGGLES_32_PIXEL_WIDTH;
  else if (width == BITSQUIGGLES_40)
    expected_width = BITSQUIGGLES_40_PIXEL_WIDTH;
  else
    return -1;
  if (fill_rect == NULL || grid == NULL || scale == 0u ||
      grid->width != expected_width ||
      grid->height != BITSQUIGGLES_PIXEL_HEIGHT ||
      scale > UINT_MAX / grid->width || scale > UINT_MAX / grid->height)
    return -1;

  output_width = grid->width * scale;
  output_height = grid->height * scale;
  if (x > UINT_MAX - output_width || y > UINT_MAX - output_height)
    return -1;

  pixel_count = grid->width * grid->height;
  for (index = 0; index < pixel_count; ++index) {
    if (grid->pixels[index] > 1u)
      return -1;
  }

  if (fill_rect(context, x, y, output_width, output_height,
                &grid->background) != 0)
    return -1;

  for (column = 0; column < grid->width; ++column) {
    unsigned int row;
    unsigned int run_start = grid->height;
    for (row = 0; row <= grid->height; ++row) {
      bool active = row < grid->height &&
                    grid->pixels[row * grid->width + column] != 0u;
      if (active && run_start == grid->height) {
        run_start = row;
      } else if (!active && run_start != grid->height) {
        if (fill_rect(context, x + column * scale, y + run_start * scale,
                      scale, (row - run_start) * scale,
                      &grid->foreground) != 0)
          return -1;
        run_start = grid->height;
      }
    }
  }
  return 0;
}

int bitsquiggles_render_raster32(BitsquigglesFillRect fill_rect,
                                 void *context,
                                 const BitsquigglesPixelGrid *grid,
                                 unsigned int x, unsigned int y,
                                 unsigned int scale) {
  return render_raster(BITSQUIGGLES_32, fill_rect, context, grid, x, y,
                       scale);
}

int bitsquiggles_render_raster40(BitsquigglesFillRect fill_rect,
                                 void *context,
                                 const BitsquigglesPixelGrid *grid,
                                 unsigned int x, unsigned int y,
                                 unsigned int scale) {
  return render_raster(BITSQUIGGLES_40, fill_rect, context, grid, x, y,
                       scale);
}
