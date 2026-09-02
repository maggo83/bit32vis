#ifndef BITSQUIGGLES_CORE_H
#define BITSQUIGGLES_CORE_H

#include <stdbool.h>
#include <stdint.h>

#define BITSQUIGGLES_ROWS 7u
#define BITSQUIGGLES_32_COLUMNS 5u
#define BITSQUIGGLES_40_COLUMNS 7u
#define BITSQUIGGLES_MAX_COLUMNS BITSQUIGGLES_40_COLUMNS
#define BITSQUIGGLES_32_EDGE_COUNT 58u
#define BITSQUIGGLES_40_EDGE_COUNT 84u
#define BITSQUIGGLES_MAX_EDGE_COUNT BITSQUIGGLES_40_EDGE_COUNT
#define BITSQUIGGLES_32_PIXEL_WIDTH 16u
#define BITSQUIGGLES_40_PIXEL_WIDTH 22u
#define BITSQUIGGLES_MAX_PIXEL_WIDTH BITSQUIGGLES_40_PIXEL_WIDTH
#define BITSQUIGGLES_PIXEL_HEIGHT 22u
#define BITSQUIGGLES_40_MASK UINT64_C(0xffffffffff)
#define BITSQUIGGLES_32_MAX_SMOOTH_BLOBS 82u
#define BITSQUIGGLES_40_MAX_SMOOTH_BLOBS 120u

typedef enum {
  BITSQUIGGLES_STANDARD,
  BITSQUIGGLES_HIGH_CONTRAST,
  BITSQUIGGLES_MONOCHROME,
  BITSQUIGGLES_BLACK_AND_WHITE
} BitsquigglesStyle;

typedef enum {
  BITSQUIGGLES_LEFT_RIGHT,
  BITSQUIGGLES_TOP_BOTTOM,
  BITSQUIGGLES_HALF_TURN,
  BITSQUIGGLES_DIAGONAL_SLASH
} BitsquigglesMode;

typedef enum {
  BITSQUIGGLES_32 = 32,
  BITSQUIGGLES_40 = 40
} BitsquigglesWidth;

typedef struct {
  double lightness;
  double chroma;
  double hue;
  char hex[8];
} BitsquigglesColor;

typedef struct {
  uint8_t start_row;
  uint8_t start_column;
  uint8_t end_row;
  uint8_t end_column;
} BitsquigglesEdge;

typedef struct {
  uint8_t top_row;
  uint8_t left_column;
  uint8_t bottom_row;
  uint8_t right_column;
} BitsquigglesSmoothBlob;

typedef struct {
  uint64_t input;
  uint64_t mixed;
  uint8_t connections[BITSQUIGGLES_MAX_EDGE_COUNT];
  uint8_t cells[BITSQUIGGLES_ROWS][BITSQUIGGLES_MAX_COLUMNS];
  BitsquigglesColor background;
  BitsquigglesColor foreground;
  BitsquigglesStyle style;
  BitsquigglesMode preferred_mode;
  BitsquigglesMode actual_mode;
  bool fallback;
  uint8_t luminance_index;
  bool swapped;
} BitsquigglesSpec;

typedef struct {
  uint8_t width;
  uint8_t height;
  uint8_t pixels[BITSQUIGGLES_MAX_PIXEL_WIDTH * BITSQUIGGLES_PIXEL_HEIGHT];
  BitsquigglesColor background;
  BitsquigglesColor foreground;
  BitsquigglesStyle style;
} BitsquigglesPixelGrid;

int bitsquiggles_mix(BitsquigglesWidth width, uint64_t input,
                     uint64_t *output);
unsigned int
bitsquiggles_free_connection_count(BitsquigglesWidth width,
                                   BitsquigglesMode mode);
unsigned int
bitsquiggles_usable_connection_count(BitsquigglesWidth width,
                                     BitsquigglesMode mode);
bool bitsquiggles_matches_mode(BitsquigglesWidth width,
                              const uint8_t *connections,
                              BitsquigglesMode mode);
int bitsquiggles_edges(BitsquigglesWidth width, BitsquigglesEdge *output);
const char *bitsquiggles_mode_label(BitsquigglesMode mode);
int bitsquiggles_spec(BitsquigglesWidth width, uint64_t input,
                      BitsquigglesStyle style, BitsquigglesSpec *output);
int bitsquiggles_pixels(BitsquigglesWidth width, uint64_t input,
                        BitsquigglesStyle style,
                        BitsquigglesPixelGrid *output);
int bitsquiggles_smooth_blobs(BitsquigglesWidth width,
                              const uint8_t *connections,
                              BitsquigglesSmoothBlob *output);
int bitsquiggles_bip380_checksum_input(const char *checksum,
                                       uint64_t *output);

#endif
