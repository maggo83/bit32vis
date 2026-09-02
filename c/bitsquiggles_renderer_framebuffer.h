#ifndef BITSQUIGGLES_RENDERER_FRAMEBUFFER_H
#define BITSQUIGGLES_RENDERER_FRAMEBUFFER_H

#include "bitsquiggles_core.h"

typedef int (*BitsquigglesFillRect)(
    void *context, unsigned int x, unsigned int y, unsigned int width,
    unsigned int height, const BitsquigglesColor *color);

static inline int bitsquiggles_spec32(uint32_t input, BitsquigglesStyle style,
                                      BitsquigglesSpec *output) {
  return bitsquiggles_spec(BITSQUIGGLES_32, input, style, output);
}

static inline int bitsquiggles_spec40(uint64_t input, BitsquigglesStyle style,
                                      BitsquigglesSpec *output) {
  return bitsquiggles_spec(BITSQUIGGLES_40, input, style, output);
}

static inline int bitsquiggles_pixels32(uint32_t input,
                                        BitsquigglesStyle style,
                                        BitsquigglesPixelGrid *output) {
  return bitsquiggles_pixels(BITSQUIGGLES_32, input, style, output);
}

static inline int bitsquiggles_pixels40(uint64_t input,
                                        BitsquigglesStyle style,
                                        BitsquigglesPixelGrid *output) {
  return bitsquiggles_pixels(BITSQUIGGLES_40, input, style, output);
}

int bitsquiggles_render_raster32(BitsquigglesFillRect fill_rect,
                                 void *context,
                                 const BitsquigglesPixelGrid *grid,
                                 unsigned int x, unsigned int y,
                                 unsigned int scale);
int bitsquiggles_render_raster40(BitsquigglesFillRect fill_rect,
                                 void *context,
                                 const BitsquigglesPixelGrid *grid,
                                 unsigned int x, unsigned int y,
                                 unsigned int scale);

#endif
