/* C99 BitSquiggles core conformance and regression tests.
 *
 * Run from the repository root:
 * c/build/test_bitsquiggles_core fixtures/v1-32.json fixtures/v1-40.json
 * Grug 2-Clause License: do what want; not sue grug.
 */
#include "bitsquiggles_core.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
  const char *label;
  BitsquigglesWidth width;
  unsigned int columns;
  unsigned int edge_count;
  unsigned int pixel_width;
  unsigned int max_smooth_blobs;
  unsigned int input_width;
  unsigned int minimum_avalanche;
  unsigned int minimum_average_avalanche;
  unsigned int style_samples;
  unsigned int injectivity_samples;
  unsigned int smooth_samples;
  unsigned int smooth_coverage_samples;
  unsigned int expected_free[4];
  unsigned int expected_usable[4];
} TestVariant;

static const TestVariant VARIANT32 = {
    "BitSquiggle32",
    BITSQUIGGLES_32,
    BITSQUIGGLES_32_COLUMNS,
    BITSQUIGGLES_32_EDGE_COUNT,
    BITSQUIGGLES_32_PIXEL_WIDTH,
    BITSQUIGGLES_32_MAX_SMOOTH_BLOBS,
    32u,
    20u,
    26u,
    1000u,
    100000u,
    2000u,
    2000u,
    {32u, 31u, 29u, 33u},
    {32u, 31u, 29u, 33u}};

static const TestVariant VARIANT40 = {
    "BitSquiggle40",
    BITSQUIGGLES_40,
    BITSQUIGGLES_40_COLUMNS,
    BITSQUIGGLES_40_EDGE_COUNT,
    BITSQUIGGLES_40_PIXEL_WIDTH,
    BITSQUIGGLES_40_MAX_SMOOTH_BLOBS,
    40u,
    25u,
    0u,
    2000u,
    10000u,
    500u,
    500u,
    {45u, 45u, 42u, 42u},
    {42u, 42u, 40u, 40u}};

static unsigned long checks = 0;
static const char *current_test = "shared";

static void check(bool condition, const char *message) {
  ++checks;
  if (!condition) {
    fprintf(stderr, "C99 test failed [%s]: %s\n", current_test, message);
    exit(1);
  }
}

static void connections_string(const uint8_t *connections,
                               unsigned int edge_count, char *output) {
  unsigned int index;
  for (index = 0; index < edge_count; ++index)
    output[index] = (char)('0' + connections[index]);
  output[edge_count] = '\0';
}

static void pixels_string(const BitsquigglesPixelGrid *grid, char *output) {
  unsigned int index;
  unsigned int pixel_count = grid->width * grid->height;
  for (index = 0; index < pixel_count; ++index)
    output[index] = (char)('0' + grid->pixels[index]);
  output[pixel_count] = '\0';
}

static int edge_compare(BitsquigglesEdge first, BitsquigglesEdge second) {
  if (first.start_row != second.start_row)
    return first.start_row < second.start_row ? -1 : 1;
  if (first.start_column != second.start_column)
    return first.start_column < second.start_column ? -1 : 1;
  if (first.end_row != second.end_row)
    return first.end_row < second.end_row ? -1 : 1;
  if (first.end_column != second.end_column)
    return first.end_column < second.end_column ? -1 : 1;
  return 0;
}

static unsigned int connection_index(const TestVariant *variant,
                                     uint8_t start_row,
                                     uint8_t start_column, uint8_t end_row,
                                     uint8_t end_column) {
  BitsquigglesEdge edges[BITSQUIGGLES_MAX_EDGE_COUNT];
  unsigned int index;
  check(bitsquiggles_edges(variant->width, edges) ==
            (int)variant->edge_count,
        "enumerate canonical edges");
  for (index = 0; index < variant->edge_count; ++index) {
    if (edges[index].start_row == start_row &&
        edges[index].start_column == start_column &&
        edges[index].end_row == end_row &&
        edges[index].end_column == end_column) {
      return index;
    }
  }
  check(false, "known canonical edge");
  return 0;
}

static void set_connection(const TestVariant *variant, uint8_t *connections,
                           uint8_t start_row, uint8_t start_column,
                           uint8_t end_row, uint8_t end_column,
                           uint8_t value) {
  connections[connection_index(variant, start_row, start_column, end_row,
                               end_column)] = value;
}

static uint8_t connection_value(const TestVariant *variant,
                                const uint8_t *connections,
                                uint8_t start_row, uint8_t start_column,
                                uint8_t end_row, uint8_t end_column) {
  return connections[connection_index(variant, start_row, start_column,
                                      end_row, end_column)];
}

static const unsigned int *marker_indices40(void) {
  static unsigned int indices[4];
  static bool initialized = false;
  if (!initialized) {
    indices[0] = connection_index(&VARIANT40, 2, 3, 3, 3);
    indices[1] = connection_index(&VARIANT40, 3, 2, 3, 3);
    indices[2] = connection_index(&VARIANT40, 3, 3, 3, 4);
    indices[3] = connection_index(&VARIANT40, 3, 3, 4, 3);
    initialized = true;
  }
  return indices;
}

static bool marker_is_valid40(const uint8_t *connections) {
  const unsigned int *indices = marker_indices40();
  return connections[indices[0]] == 1 && connections[indices[1]] == 0 &&
         connections[indices[2]] == 0 && connections[indices[3]] == 0;
}

static void clear_marker40(uint8_t *connections) {
  const unsigned int *indices = marker_indices40();
  unsigned int index;
  for (index = 0; index < 4; ++index)
    connections[indices[index]] = 0;
}

static void check_marker40(const uint8_t *connections) {
  const unsigned int *indices = marker_indices40();
  check(connections[indices[0]] == 1,
        "center marker up selected");
  check(connections[indices[1]] == 0,
        "center marker left clear");
  check(connections[indices[2]] == 0,
        "center marker right clear");
  check(connections[indices[3]] == 0,
        "center marker down clear");
}

static void rotate_connections40(const uint8_t *connections,
                                 uint8_t *rotated) {
  static unsigned int permutation[BITSQUIGGLES_40_EDGE_COUNT];
  static bool initialized = false;
  unsigned int index;

  if (!initialized) {
    BitsquigglesEdge edges[BITSQUIGGLES_40_EDGE_COUNT];
    check(bitsquiggles_edges(BITSQUIGGLES_40, edges) ==
              BITSQUIGGLES_40_EDGE_COUNT,
          "enumerate edges for rotation");
    for (index = 0; index < BITSQUIGGLES_40_EDGE_COUNT; ++index) {
      uint8_t start_row = edges[index].start_column;
      uint8_t start_column =
          (uint8_t)(BITSQUIGGLES_40_COLUMNS - 1u - edges[index].start_row);
      uint8_t end_row = edges[index].end_column;
      uint8_t end_column =
          (uint8_t)(BITSQUIGGLES_40_COLUMNS - 1u - edges[index].end_row);
      if (start_row > end_row ||
          (start_row == end_row && start_column > end_column)) {
        uint8_t temporary = start_row;
        start_row = end_row;
        end_row = temporary;
        temporary = start_column;
        start_column = end_column;
        end_column = temporary;
      }
      permutation[index] = connection_index(
          &VARIANT40, start_row, start_column, end_row, end_column);
    }
    initialized = true;
  }

  memset(rotated, 0, BITSQUIGGLES_40_EDGE_COUNT);
  for (index = 0; index < BITSQUIGGLES_40_EDGE_COUNT; ++index)
    rotated[permutation[index]] = connections[index];
}

static void test_edges_and_capacities(const TestVariant *variant) {
  BitsquigglesEdge edges[BITSQUIGGLES_MAX_EDGE_COUNT];
  unsigned int index;
  check(bitsquiggles_edges(variant->width, edges) ==
            (int)variant->edge_count,
        "enumerate canonical edges");
  for (index = 0; index < variant->edge_count; ++index) {
    check((unsigned int)(edges[index].end_row - edges[index].start_row) +
                  (unsigned int)(edges[index].end_column -
                                 edges[index].start_column) ==
              1u,
          "orthogonal unit edge");
    if (index > 0) {
      check(edge_compare(edges[index - 1], edges[index]) < 0,
            "lexical edge order");
    }
  }
  for (index = 0; index < 4; ++index) {
    check(bitsquiggles_free_connection_count(
              variant->width, (BitsquigglesMode)index) ==
              variant->expected_free[index],
          "free connection count");
    check(bitsquiggles_usable_connection_count(
              variant->width, (BitsquigglesMode)index) ==
              variant->expected_usable[index],
          "usable connection count");
  }
}

static void test_golden_vector32(void) {
  BitsquigglesSpec visual;
  BitsquigglesPixelGrid grid;
  uint64_t mixed;
  char mask[BITSQUIGGLES_MAX_EDGE_COUNT + 1];
  unsigned int index;

  check(bitsquiggles_mix(BITSQUIGGLES_32, UINT32_C(0x89abcdef), &mixed) ==
                0 &&
            mixed == UINT32_C(0x47ac5876),
        "golden mixer");
  check(bitsquiggles_spec(BITSQUIGGLES_32, UINT32_C(0x89abcdef),
                          BITSQUIGGLES_STANDARD, &visual) == 0,
        "golden spec success");
  check(visual.input == UINT32_C(0x89abcdef), "golden input metadata");
  check(visual.mixed == UINT32_C(0x47ac5876), "golden mixed value");
  check(visual.preferred_mode == BITSQUIGGLES_TOP_BOTTOM,
        "golden preferred mode");
  check(visual.actual_mode == BITSQUIGGLES_TOP_BOTTOM && !visual.fallback,
        "golden actual mode");
  check(visual.luminance_index == 2, "golden luminance index");
  connections_string(visual.connections, VARIANT32.edge_count, mask);
  check(strcmp(mask,
               "0001111010110001011000011101010010101100001010011011010011") ==
            0,
        "golden connections");
  check(strcmp(visual.background.hex, "#140040") == 0, "golden background");
  check(strcmp(visual.foreground.hex, "#8d9200") == 0, "golden foreground");
  check(bitsquiggles_pixels(BITSQUIGGLES_32, UINT32_C(0x89abcdef),
                            BITSQUIGGLES_STANDARD, &grid) == 0,
        "golden pixels success");
  check(grid.width == BITSQUIGGLES_32_PIXEL_WIDTH &&
            grid.height == BITSQUIGGLES_PIXEL_HEIGHT,
        "golden grid dimensions");
  for (index = BITSQUIGGLES_32_PIXEL_WIDTH * BITSQUIGGLES_PIXEL_HEIGHT;
       index < BITSQUIGGLES_MAX_PIXEL_WIDTH * BITSQUIGGLES_PIXEL_HEIGHT;
       ++index) {
    check(grid.pixels[index] == 0, "unused pixel capacity is zero");
  }
}

static void test_slash_wrap_regression32(void) {
  BitsquigglesSpec visual;
  check(bitsquiggles_spec(BITSQUIGGLES_32, UINT32_C(0xd9abcdef),
                          BITSQUIGGLES_STANDARD, &visual) == 0,
        "slash wrap regression spec");
  check(visual.actual_mode == BITSQUIGGLES_DIAGONAL_SLASH,
        "slash wrap regression mode");
  check(connection_value(&VARIANT32, visual.connections, 1, 1, 2, 1) ==
            connection_value(&VARIANT32, visual.connections, 4, 3, 4, 4),
        "slash copy maps upper vertical to lower horizontal edge");
  check(connection_value(&VARIANT32, visual.connections, 1, 2, 2, 2) ==
            connection_value(&VARIANT32, visual.connections, 3, 3, 3, 4),
        "slash copy preserves adjacent diagonal edge relation");
}

static void test_modes_and_canonical_priority32(void) {
  bool seen[4] = {false, false, false, false};
  bool saw_fallback = false;
  bool saw_half_turn_capacity_fallback = false;
  bool saw_accepted_half_turn = false;
  uint64_t value;

  for (value = 0; value < 50000; ++value) {
    BitsquigglesSpec visual;
    unsigned int earlier;
    check(bitsquiggles_spec(BITSQUIGGLES_32, value, BITSQUIGGLES_STANDARD,
                            &visual) == 0,
          "mode sample spec");
    check((unsigned int)visual.preferred_mode < 4u,
          "preferred mode in range");
    seen[visual.preferred_mode] = true;
    saw_fallback = saw_fallback || visual.fallback;

    if (visual.preferred_mode == BITSQUIGGLES_HALF_TURN) {
      if ((visual.mixed & 1u) != 0) {
        check(visual.fallback, "half-turn omitted bit fallback");
        saw_half_turn_capacity_fallback = true;
      } else if (!visual.fallback) {
        saw_accepted_half_turn = true;
      }
    }

    check(bitsquiggles_matches_mode(BITSQUIGGLES_32, visual.connections,
                                    visual.actual_mode),
          "actual mode match");
    if (visual.fallback) {
      check(visual.actual_mode == BITSQUIGGLES_LEFT_RIGHT,
            "fallback uses default mode");
    } else {
      for (earlier = 0; earlier < (unsigned int)visual.preferred_mode;
           ++earlier) {
        check(!bitsquiggles_matches_mode(BITSQUIGGLES_32,
                                         visual.connections,
                                         (BitsquigglesMode)earlier),
              "canonical mode priority");
      }
    }
  }

  for (value = 0; value < 4; ++value)
    check(seen[value], "preferred mode observed");
  check(saw_fallback, "fallback observed");
  check(saw_half_turn_capacity_fallback,
        "half-turn capacity fallback observed");
  check(saw_accepted_half_turn, "accepted half-turn candidate observed");
}

static void test_input_bit_avalanche(const TestVariant *variant) {
  BitsquigglesSpec base;
  unsigned int total = 0;
  unsigned int bit;

  check(bitsquiggles_spec(variant->width, 0, BITSQUIGGLES_STANDARD, &base) ==
            0,
        "avalanche base spec");
  for (bit = 0; bit < variant->input_width; ++bit) {
    BitsquigglesSpec changed;
    unsigned int distance = 0;
    unsigned int edge;
    check(bitsquiggles_spec(variant->width, UINT64_C(1) << bit,
                            BITSQUIGGLES_STANDARD, &changed) == 0,
          "avalanche changed spec");
    for (edge = 0; edge < variant->edge_count; ++edge) {
      if (base.connections[edge] != changed.connections[edge])
        ++distance;
    }
    check(distance >= variant->minimum_avalanche,
          "one-bit edge avalanche");
    total += distance;
  }
  if (variant->minimum_average_avalanche != 0) {
    check(total >= variant->input_width * variant->minimum_average_avalanche,
          "average edge avalanche");
  }
}

typedef struct {
  uint64_t low;
  uint32_t high;
} TestMask;

static int compare_masks(const void *first, const void *second) {
  const TestMask *left = (const TestMask *)first;
  const TestMask *right = (const TestMask *)second;
  if (left->high != right->high)
    return left->high < right->high ? -1 : 1;
  if (left->low != right->low)
    return left->low < right->low ? -1 : 1;
  return 0;
}

static void test_sampled_injectivity(const TestVariant *variant) {
  TestMask *masks = (TestMask *)calloc(variant->injectivity_samples,
                                      sizeof(*masks));
  unsigned int sample;

  check(masks != NULL, "allocate sampled test masks");
  for (sample = 0; sample < variant->injectivity_samples; ++sample) {
    BitsquigglesSpec visual;
    unsigned int edge;
    check(bitsquiggles_spec(variant->width, sample, BITSQUIGGLES_MONOCHROME,
                            &visual) == 0,
          "sampled injectivity spec");
    if (variant->width == BITSQUIGGLES_40) {
      uint8_t rotated[BITSQUIGGLES_40_EDGE_COUNT];
      uint8_t next[BITSQUIGGLES_40_EDGE_COUNT];
      unsigned int turn;
      check_marker40(visual.connections);
      memcpy(rotated, visual.connections, sizeof(rotated));
      for (turn = 0; turn < 3; ++turn) {
        rotate_connections40(rotated, next);
        memcpy(rotated, next, sizeof(rotated));
        check(!marker_is_valid40(rotated),
              "rotation rejected by center marker");
      }
    }
    for (edge = 0; edge < variant->edge_count; ++edge) {
      if (visual.connections[edge] != 0) {
        if (edge < 64u)
          masks[sample].low |= UINT64_C(1) << edge;
        else
          masks[sample].high |= UINT32_C(1) << (edge - 64u);
      }
    }
  }
  qsort(masks, variant->injectivity_samples, sizeof(*masks), compare_masks);
  for (sample = 1; sample < variant->injectivity_samples; ++sample) {
    check(compare_masks(&masks[sample - 1], &masks[sample]) != 0,
          "sampled monochrome masks are unique");
  }
  free(masks);
}

typedef struct {
  uint64_t words[(BITSQUIGGLES_MAX_PIXEL_WIDTH *
                      BITSQUIGGLES_PIXEL_HEIGHT +
                  63u) /
                 64u];
} TestBitmap;

static int compare_bitmaps(const void *first, const void *second) {
  const TestBitmap *left = (const TestBitmap *)first;
  const TestBitmap *right = (const TestBitmap *)second;
  unsigned int index;
  for (index = 0; index < sizeof(left->words) / sizeof(left->words[0]);
       ++index) {
    if (left->words[index] != right->words[index])
      return left->words[index] < right->words[index] ? -1 : 1;
  }
  return 0;
}

static void
test_black_and_white_bitmap_injectivity(const TestVariant *variant) {
  TestBitmap *bitmaps = (TestBitmap *)calloc(variant->injectivity_samples,
                                             sizeof(*bitmaps));
  unsigned int sample;

  check(bitmaps != NULL, "allocate sampled test bitmaps");
  for (sample = 0; sample < variant->injectivity_samples; ++sample) {
    BitsquigglesPixelGrid grid;
    bool foreground_is_white;
    bool background_is_white;
    unsigned int pixel_count;
    unsigned int pixel;
    check(bitsquiggles_pixels(variant->width, sample,
                              BITSQUIGGLES_BLACK_AND_WHITE, &grid) == 0,
          "sampled black-and-white pixels");
    foreground_is_white = strcmp(grid.foreground.hex, "#ffffff") == 0;
    background_is_white = strcmp(grid.background.hex, "#ffffff") == 0;
    pixel_count = grid.width * grid.height;
    for (pixel = 0; pixel < pixel_count; ++pixel) {
      bool is_white = grid.pixels[pixel] != 0 ? foreground_is_white
                                              : background_is_white;
      if (is_white)
        bitmaps[sample].words[pixel / 64u] |= UINT64_C(1) << (pixel % 64u);
    }
  }
  qsort(bitmaps, variant->injectivity_samples, sizeof(*bitmaps),
        compare_bitmaps);
  for (sample = 1; sample < variant->injectivity_samples; ++sample) {
    check(compare_bitmaps(&bitmaps[sample - 1], &bitmaps[sample]) != 0,
          "sampled black-and-white bitmaps are unique");
  }
  free(bitmaps);
}

static void test_style_and_raster_properties(const TestVariant *variant) {
  bool seen[4] = {false, false, false, false};
  unsigned int sample;

  for (sample = 0; sample < variant->style_samples; ++sample) {
    BitsquigglesSpec standard;
    BitsquigglesSpec high_contrast;
    BitsquigglesSpec monochrome;
    BitsquigglesSpec black_and_white;
    BitsquigglesPixelGrid grid;
    BitsquigglesEdge edges[BITSQUIGGLES_MAX_EDGE_COUNT];
    uint8_t data_connections[BITSQUIGGLES_MAX_EDGE_COUNT] = {0};
    uint8_t expected_cells[BITSQUIGGLES_ROWS][BITSQUIGGLES_MAX_COLUMNS] = {
        {0}};
    uint64_t parity_value = sample;
    bool odd_parity = false;
    unsigned int earlier;
    unsigned int edge;
    unsigned int index;
    unsigned int x;
    unsigned int y;

    while (parity_value != 0) {
      odd_parity = !odd_parity;
      parity_value &= parity_value - 1u;
    }
    check(bitsquiggles_spec(variant->width, sample, BITSQUIGGLES_STANDARD,
                            &standard) == 0,
          "standard spec");
    check(bitsquiggles_spec(variant->width, sample,
                            BITSQUIGGLES_HIGH_CONTRAST, &high_contrast) == 0,
          "high-contrast spec");
    check(bitsquiggles_spec(variant->width, sample, BITSQUIGGLES_MONOCHROME,
                            &monochrome) == 0,
          "monochrome spec");
    check(bitsquiggles_spec(variant->width, sample,
                            BITSQUIGGLES_BLACK_AND_WHITE,
                            &black_and_white) == 0,
          "black-and-white spec");
    check(memcmp(standard.connections, high_contrast.connections,
                 variant->edge_count) == 0,
          "high-contrast style-independent geometry");
    check(memcmp(standard.connections, monochrome.connections,
                 variant->edge_count) == 0,
          "monochrome style-independent geometry");
    check(memcmp(standard.connections, black_and_white.connections,
                 variant->edge_count) == 0,
          "black-and-white style-independent geometry");
    check((strcmp(black_and_white.background.hex, "#000000") == 0 ||
           strcmp(black_and_white.background.hex, "#ffffff") == 0) &&
              (strcmp(black_and_white.foreground.hex, "#000000") == 0 ||
               strcmp(black_and_white.foreground.hex, "#ffffff") == 0) &&
              strcmp(black_and_white.background.hex,
                     black_and_white.foreground.hex) != 0,
          "black-and-white uses opposed binary colors");
    check(high_contrast.foreground.chroma > standard.foreground.chroma,
          "high contrast increases foreground chroma");
    check(monochrome.background.chroma == 0.0 &&
              monochrome.foreground.chroma == 0.0,
          "monochrome removes chroma");
    check((strcmp(black_and_white.foreground.hex, "#000000") == 0) ==
              black_and_white.swapped,
          "black-and-white parity controls foreground polarity");
    check(standard.swapped == odd_parity, "input parity controls polarity");
    check(standard.luminance_index == (uint8_t)(standard.mixed & 3u),
          "luminance index derives from mixed input");
    check((unsigned int)standard.preferred_mode < 4u,
          "preferred mode in range");
    seen[standard.preferred_mode] = true;

    memcpy(data_connections, standard.connections, variant->edge_count);
    if (variant->width == BITSQUIGGLES_40) {
      check_marker40(standard.connections);
      clear_marker40(data_connections);
    }
    check(bitsquiggles_matches_mode(variant->width, data_connections,
                                    standard.actual_mode),
          "actual mode match");
    if (standard.fallback) {
      check(standard.actual_mode == BITSQUIGGLES_LEFT_RIGHT,
            "fallback uses default mode");
    } else {
      for (earlier = 0; earlier < (unsigned int)standard.preferred_mode;
           ++earlier) {
        check(!bitsquiggles_matches_mode(variant->width, data_connections,
                                         (BitsquigglesMode)earlier),
              "canonical mode priority");
      }
    }

    check(bitsquiggles_edges(variant->width, edges) ==
          (int)variant->edge_count,
          "enumerate raster edges");
    for (edge = 0; edge < variant->edge_count; ++edge) {
      if (standard.connections[edge] != 0) {
        expected_cells[edges[edge].start_row][edges[edge].start_column] = 1;
        expected_cells[edges[edge].end_row][edges[edge].end_column] = 1;
      }
    }
    check(memcmp(expected_cells, standard.cells, sizeof(expected_cells)) == 0,
          "cells derive from selected edges");

    check(bitsquiggles_pixels(variant->width, sample, BITSQUIGGLES_STANDARD,
                              &grid) == 0,
          "pixels success");
    check(grid.width == variant->pixel_width &&
              grid.height == BITSQUIGGLES_PIXEL_HEIGHT,
          "exact raster dimensions");
    check(grid.style == standard.style &&
              strcmp(grid.background.hex, standard.background.hex) == 0 &&
              strcmp(grid.foreground.hex, standard.foreground.hex) == 0,
          "pixel metadata matches spec");
    for (x = 0; x < variant->pixel_width; ++x) {
      check(grid.pixels[x] == 0 &&
                grid.pixels[(BITSQUIGGLES_PIXEL_HEIGHT - 1u) *
                                variant->pixel_width +
                            x] == 0,
            "horizontal border");
    }
    for (y = 0; y < BITSQUIGGLES_PIXEL_HEIGHT; ++y) {
      check(grid.pixels[y * variant->pixel_width] == 0 &&
                grid.pixels[y * variant->pixel_width +
                            variant->pixel_width - 1u] == 0,
            "vertical border");
    }
    for (index = 0;
         index < variant->pixel_width * BITSQUIGGLES_PIXEL_HEIGHT; ++index) {
      check(grid.pixels[index] <= 1u, "exact raster is binary");
    }
    for (index = variant->pixel_width * BITSQUIGGLES_PIXEL_HEIGHT;
         index < BITSQUIGGLES_MAX_PIXEL_WIDTH * BITSQUIGGLES_PIXEL_HEIGHT;
         ++index) {
      check(grid.pixels[index] == 0, "unused pixel capacity is zero");
    }
    for (index = variant->edge_count; index < BITSQUIGGLES_MAX_EDGE_COUNT;
         ++index) {
      check(standard.connections[index] == 0,
            "unused connection capacity is zero");
    }
    for (edge = 0; edge < variant->edge_count; ++edge) {
      uint8_t bridge;
      x = 1u + 3u * edges[edge].start_column;
      y = 1u + 3u * edges[edge].start_row;
      bridge = edges[edge].start_row == edges[edge].end_row
                   ? grid.pixels[y * variant->pixel_width + x + 2u]
                   : grid.pixels[(y + 2u) * variant->pixel_width + x];
      check(bridge == standard.connections[edge],
            "bridge recovers connection");
    }
  }
  for (sample = 0; sample < 4; ++sample)
    check(seen[sample], "preferred mode observed");
}

static void check_blob(const BitsquigglesSmoothBlob *blob, uint8_t top,
                       uint8_t left, uint8_t bottom, uint8_t right,
                       const char *message) {
  check(blob->top_row == top && blob->left_column == left &&
            blob->bottom_row == bottom && blob->right_column == right,
        message);
}

static void assert_blob_coverage(
    const TestVariant *variant, const uint8_t *connections,
    const BitsquigglesSmoothBlob *blobs, int count) {
  BitsquigglesEdge edges[BITSQUIGGLES_MAX_EDGE_COUNT];
  uint8_t active[BITSQUIGGLES_ROWS][BITSQUIGGLES_MAX_COLUMNS] = {{0}};
  uint8_t covered_edges[BITSQUIGGLES_MAX_EDGE_COUNT] = {0};
  uint8_t required_junctions[(BITSQUIGGLES_ROWS - 1u) *
                             (BITSQUIGGLES_MAX_COLUMNS - 1u)] = {0};
  uint8_t covered_junctions[(BITSQUIGGLES_ROWS - 1u) *
                            (BITSQUIGGLES_MAX_COLUMNS - 1u)] = {0};
  unsigned int junction_columns = variant->columns - 1u;
  unsigned int junction_count =
      (BITSQUIGGLES_ROWS - 1u) * junction_columns;
  unsigned int edge;
  unsigned int row;
  unsigned int column;
  int blob_index;

  check(bitsquiggles_edges(variant->width, edges) ==
            (int)variant->edge_count,
        "enumerate smooth edges");
  for (edge = 0; edge < variant->edge_count; ++edge) {
    if (connections[edge] != 0) {
      active[edges[edge].start_row][edges[edge].start_column] = 1;
      active[edges[edge].end_row][edges[edge].end_column] = 1;
    }
  }
  for (row = 0; row < BITSQUIGGLES_ROWS - 1u; ++row) {
    for (column = 0; column < junction_columns; ++column) {
      required_junctions[row * junction_columns + column] =
          connection_value(variant, connections, (uint8_t)row,
                           (uint8_t)column, (uint8_t)row,
                           (uint8_t)(column + 1u)) &&
          connection_value(variant, connections, (uint8_t)(row + 1u),
                           (uint8_t)column, (uint8_t)(row + 1u),
                           (uint8_t)(column + 1u)) &&
          connection_value(variant, connections, (uint8_t)row,
                           (uint8_t)column, (uint8_t)(row + 1u),
                           (uint8_t)column) &&
          connection_value(variant, connections, (uint8_t)row,
                           (uint8_t)(column + 1u), (uint8_t)(row + 1u),
                           (uint8_t)(column + 1u));
    }
  }
  for (blob_index = 0; blob_index < count; ++blob_index) {
    const BitsquigglesSmoothBlob *blob = &blobs[blob_index];
    check(blob->top_row <= blob->bottom_row &&
              blob->left_column <= blob->right_column &&
              blob->bottom_row < BITSQUIGGLES_ROWS &&
              blob->right_column < variant->columns,
          "blob coordinates in range");
    for (row = blob->top_row; row <= blob->bottom_row; ++row) {
      for (column = blob->left_column; column <= blob->right_column;
           ++column) {
        check(active[row][column], "blob contains active cells only");
      }
    }
    for (edge = 0; edge < variant->edge_count; ++edge) {
      if (edges[edge].start_row >= blob->top_row &&
          edges[edge].start_row <= blob->bottom_row &&
          edges[edge].start_column >= blob->left_column &&
          edges[edge].start_column <= blob->right_column &&
          edges[edge].end_row >= blob->top_row &&
          edges[edge].end_row <= blob->bottom_row &&
          edges[edge].end_column >= blob->left_column &&
          edges[edge].end_column <= blob->right_column) {
        check(connections[edge] != 0, "blob internal edge selected");
        covered_edges[edge] = 1;
      }
    }
    for (row = blob->top_row; row < blob->bottom_row; ++row) {
      for (column = blob->left_column; column < blob->right_column;
           ++column) {
        covered_junctions[row * junction_columns + column] = 1;
      }
    }
  }
  check(memcmp(covered_edges, connections, variant->edge_count) == 0,
        "blobs cover every selected edge");
  for (row = 0; row < junction_count; ++row) {
    check(!required_junctions[row] || covered_junctions[row],
          "blobs cover every required junction");
  }
}

static void test_smooth_blobs(const TestVariant *variant) {
  uint8_t connections[BITSQUIGGLES_MAX_EDGE_COUNT] = {0};
  BitsquigglesSmoothBlob blobs[BITSQUIGGLES_40_MAX_SMOOTH_BLOBS];
  unsigned int sample;
  int count;

  check(bitsquiggles_smooth_blobs(variant->width, connections, blobs) == 0,
        "empty mask has no blobs");
  set_connection(variant, connections, 0, 0, 0, 1, 1);
  count = bitsquiggles_smooth_blobs(variant->width, connections, blobs);
  check(count == 1, "single edge has one blob");
  check_blob(&blobs[0], 0, 0, 0, 1, "single edge blob geometry");

  memset(connections, 0, sizeof(connections));
  set_connection(variant, connections, 0, 0, 0, 1, 1);
  set_connection(variant, connections, 0, 1, 0, 2, 1);
  count = bitsquiggles_smooth_blobs(variant->width, connections, blobs);
  check(count == 1, "connected row has one blob");
  check_blob(&blobs[0], 0, 0, 0, 2, "connected row blob geometry");

  memset(connections, 0, sizeof(connections));
  set_connection(variant, connections, 0, 0, 0, 1, 1);
  set_connection(variant, connections, 1, 0, 1, 1, 1);
  set_connection(variant, connections, 0, 0, 1, 0, 1);
  set_connection(variant, connections, 0, 1, 1, 1, 1);
  count = bitsquiggles_smooth_blobs(variant->width, connections, blobs);
  check(count == 1, "junction has one blob");
  check_blob(&blobs[0], 0, 0, 1, 1, "junction blob geometry");

  memset(connections, 1, variant->edge_count);
  count = bitsquiggles_smooth_blobs(variant->width, connections, blobs);
  check(count == 1, "complete grid has one blob");
  check_blob(&blobs[0], 0, 0, (uint8_t)(BITSQUIGGLES_ROWS - 1u),
             (uint8_t)(variant->columns - 1u),
             "complete grid blob geometry");

  for (sample = 0; sample < variant->smooth_samples; ++sample) {
    BitsquigglesSpec visual;
    check(bitsquiggles_spec(variant->width, sample, BITSQUIGGLES_STANDARD,
                            &visual) == 0,
          "generated smooth spec");
    count = bitsquiggles_smooth_blobs(variant->width, visual.connections,
                                      blobs);
    check(count >= 0 && count <= (int)variant->max_smooth_blobs,
          "generated smooth blob count bounded");
    if (sample < variant->smooth_coverage_samples)
      assert_blob_coverage(variant, visual.connections, blobs, count);
  }

  connections[0] = 2;
  check(bitsquiggles_smooth_blobs(variant->width, connections, blobs) == -1,
        "reject non-binary smooth mask");
  check(bitsquiggles_smooth_blobs(variant->width, NULL, blobs) == -1,
        "reject null smooth mask");
  check(bitsquiggles_smooth_blobs(variant->width, connections, NULL) == -1,
        "reject null smooth output");
}

static void test_golden_vector40(void) {
  BitsquigglesSpec visual;
  BitsquigglesPixelGrid grid;
  BitsquigglesSmoothBlob blobs[BITSQUIGGLES_40_MAX_SMOOTH_BLOBS];
  uint64_t value;
  char mask[BITSQUIGGLES_MAX_EDGE_COUNT + 1];

  check(bitsquiggles_bip380_checksum_input("89f8spxm", &value) == 0 &&
            value == UINT64_C(0x39527804db),
        "golden checksum");
  check(bitsquiggles_spec(BITSQUIGGLES_40, value, BITSQUIGGLES_STANDARD,
                          &visual) == 0,
        "golden spec success");
  check(visual.mixed == UINT64_C(0x34b1a077c8), "golden mixed value");
  connections_string(visual.connections, VARIANT40.edge_count, mask);
  check(strcmp(mask, "001101001101001011010000110110101110001000000000000011101"
                     "111111011100101000101000000") == 0,
        "golden connections");
  check(bitsquiggles_pixels(BITSQUIGGLES_40, value, BITSQUIGGLES_STANDARD,
                            &grid) == 0 &&
            grid.width == BITSQUIGGLES_40_PIXEL_WIDTH &&
            grid.height == BITSQUIGGLES_PIXEL_HEIGHT,
        "golden pixels");
  check(bitsquiggles_smooth_blobs(BITSQUIGGLES_40, visual.connections,
                                  blobs) > 0,
        "golden smooth blobs");
}

static void test_fallback_vectors40(void) {
  static const uint64_t inputs[] = {UINT64_C(0xa7912def7b),
                                    UINT64_C(0x08a1a65b0c),
                                    UINT64_C(0x500181b841)};
  unsigned int index;
  for (index = 0; index < 3; ++index) {
    BitsquigglesSpec visual;
    check(bitsquiggles_spec(BITSQUIGGLES_40, inputs[index],
                            BITSQUIGGLES_STANDARD, &visual) == 0,
          "fallback vector spec");
    check(visual.preferred_mode == (BitsquigglesMode)(index + 1),
          "fallback preferred mode");
    check(visual.actual_mode == BITSQUIGGLES_LEFT_RIGHT && visual.fallback,
          "fallback vector uses default mode");
    check_marker40(visual.connections);
  }
}

static char *read_fixture(const char *path) {
  FILE *file = fopen(path, "rb");
  char *content;
  long length;
  if (file == NULL)
    return NULL;
  if (fseek(file, 0, SEEK_END) != 0 || (length = ftell(file)) < 0 ||
      fseek(file, 0, SEEK_SET) != 0) {
    fclose(file);
    return NULL;
  }
  content = (char *)malloc((size_t)length + 1u);
  if (content == NULL ||
      fread(content, 1, (size_t)length, file) != (size_t)length) {
    free(content);
    fclose(file);
    return NULL;
  }
  fclose(file);
  content[length] = '\0';
  return content;
}

static bool quoted_field(const char *begin, const char *end,
                         const char *field, char *output,
                         size_t output_size) {
  char needle[48];
  const char *value;
  const char *finish;
  (void)snprintf(needle, sizeof(needle), "\"%s\":\"", field);
  value = strstr(begin, needle);
  if (value == NULL || value >= end)
    return false;
  value += strlen(needle);
  finish = strchr(value, '\"');
  if (finish == NULL || finish >= end ||
      (size_t)(finish - value) >= output_size)
    return false;
  memcpy(output, value, (size_t)(finish - value));
  output[finish - value] = '\0';
  return true;
}

static bool bool_field(const char *begin, const char *end, const char *field,
                       bool *output) {
  char needle[48];
  const char *value;
  (void)snprintf(needle, sizeof(needle), "\"%s\":", field);
  value = strstr(begin, needle);
  if (value == NULL || value >= end)
    return false;
  value += strlen(needle);
  if (strncmp(value, "true", 4) == 0) {
    *output = true;
    return true;
  }
  if (strncmp(value, "false", 5) == 0) {
    *output = false;
    return true;
  }
  return false;
}

static void check_fixture_style(const TestVariant *variant,
                                const char *begin, const char *end,
                                uint64_t input, const char *name,
                                BitsquigglesStyle style) {
  char needle[64];
  char expected[16];
  const char *style_begin;
  BitsquigglesSpec visual;
  (void)snprintf(needle, sizeof(needle), "\"%s\":{", name);
  style_begin = strstr(begin, needle);
  check(style_begin != NULL && style_begin < end, "fixture style field");
  check(bitsquiggles_spec(variant->width, input, style, &visual) == 0,
        "fixture styled spec");
  check(quoted_field(style_begin, end, "background", expected,
                     sizeof(expected)),
        "fixture background field");
  check(strcmp(visual.background.hex, expected) == 0, "fixture background");
  check(quoted_field(style_begin, end, "foreground", expected,
                     sizeof(expected)),
        "fixture foreground field");
  check(strcmp(visual.foreground.hex, expected) == 0, "fixture foreground");
}

static void test_shared_fixture(const TestVariant *variant, const char *path) {
  char *fixture = read_fixture(path);
  const char *vector = fixture;
  unsigned int count = 0;

  check(fixture != NULL, "open shared fixture");
  while ((vector = strstr(vector, "\"input\":\"")) != NULL) {
    const char *next = strstr(vector + 1, "\"input\":\"");
    const char *end = next == NULL ? fixture + strlen(fixture) : next;
    char input[11];
    char expected[BITSQUIGGLES_MAX_PIXEL_WIDTH *
                      BITSQUIGGLES_PIXEL_HEIGHT +
                  1];
    char actual[BITSQUIGGLES_MAX_PIXEL_WIDTH * BITSQUIGGLES_PIXEL_HEIGHT +
                1];
    bool fallback;
    uint64_t value;
    BitsquigglesSpec visual;
    BitsquigglesPixelGrid grid;

    check(quoted_field(vector, end, "input", input, sizeof(input)),
          "fixture input");
    value = (uint64_t)strtoull(input, NULL, 16);
    check(bitsquiggles_spec(variant->width, value, BITSQUIGGLES_STANDARD,
                            &visual) == 0,
          "fixture spec");
    check(quoted_field(vector, end, "mixed", expected, sizeof(expected)),
          "fixture mixed field");
    if (variant->width == BITSQUIGGLES_32)
      (void)snprintf(actual, sizeof(actual), "%08" PRIx64, visual.mixed);
    else
      (void)snprintf(actual, sizeof(actual), "%010" PRIx64, visual.mixed);
    check(strcmp(actual, expected) == 0, "fixture mixed");
    check(quoted_field(vector, end, "connections", expected,
                       sizeof(expected)),
          "fixture connections field");
    connections_string(visual.connections, variant->edge_count, actual);
    check(strcmp(actual, expected) == 0, "fixture connections");
    check(quoted_field(vector, end, "preferredMode", expected,
                       sizeof(expected)),
          "fixture preferred field");
    check(strcmp(bitsquiggles_mode_label(visual.preferred_mode), expected) ==
              0,
          "fixture preferred mode");
    check(quoted_field(vector, end, "actualMode", expected,
                       sizeof(expected)),
          "fixture actual field");
    check(strcmp(bitsquiggles_mode_label(visual.actual_mode), expected) == 0,
          "fixture actual mode");
    check(bool_field(vector, end, "fallback", &fallback) &&
              fallback == visual.fallback,
          "fixture fallback");
    check(bitsquiggles_pixels(variant->width, value, BITSQUIGGLES_STANDARD,
                              &grid) == 0,
          "fixture pixels");
    check(quoted_field(vector, end, "pixels", expected, sizeof(expected)),
          "fixture pixels field");
    pixels_string(&grid, actual);
    check(strcmp(actual, expected) == 0, "fixture pixels");
    check_fixture_style(variant, vector, end, value, "standard",
                        BITSQUIGGLES_STANDARD);
    check_fixture_style(variant, vector, end, value, "high-contrast",
                        BITSQUIGGLES_HIGH_CONTRAST);
    check_fixture_style(variant, vector, end, value, "monochrome",
                        BITSQUIGGLES_MONOCHROME);
    check_fixture_style(variant, vector, end, value, "black-and-white",
                        BITSQUIGGLES_BLACK_AND_WHITE);
    ++count;
    vector = end;
  }
  check(count > 0, "fixture vector count");
  free(fixture);
}

static void test_input_validation(void) {
  BitsquigglesSpec visual;
  BitsquigglesPixelGrid grid;
  BitsquigglesSmoothBlob blobs[BITSQUIGGLES_40_MAX_SMOOTH_BLOBS];
  BitsquigglesEdge edges[BITSQUIGGLES_MAX_EDGE_COUNT];
  uint8_t connections[BITSQUIGGLES_MAX_EDGE_COUNT] = {0};
  uint64_t value = UINT64_C(0x123456789a);

  check(bitsquiggles_mix((BitsquigglesWidth)0, 0, &value) == -1,
        "mix rejects invalid width");
  check(bitsquiggles_mix(BITSQUIGGLES_32, UINT64_C(0x100000000), &value) ==
            -1,
        "mix rejects input wider than selected width");
  check(bitsquiggles_mix(BITSQUIGGLES_40, 0, NULL) == -1,
        "mix rejects null output");
    check(bitsquiggles_spec(BITSQUIGGLES_32, UINT64_C(0x100000000),
            BITSQUIGGLES_STANDARD, &visual) == -1,
      "spec rejects input wider than 32 bits");
    check(bitsquiggles_pixels(BITSQUIGGLES_32, UINT64_C(0x100000000),
              BITSQUIGGLES_STANDARD, &grid) == -1,
      "pixels reject input wider than 32 bits");
  check(bitsquiggles_spec((BitsquigglesWidth)0, 0, BITSQUIGGLES_STANDARD,
                          &visual) == -1,
        "spec rejects invalid width");
  check(bitsquiggles_spec(BITSQUIGGLES_40, BITSQUIGGLES_40_MASK + 1u,
                          BITSQUIGGLES_STANDARD, &visual) == -1,
        "reject input wider than 40 bits");
  check(bitsquiggles_spec(BITSQUIGGLES_40, 0, (BitsquigglesStyle)-1,
                          &visual) == -1,
        "reject negative style");
  check(bitsquiggles_spec(BITSQUIGGLES_40, 0, (BitsquigglesStyle)4,
                          &visual) == -1,
        "reject style above range");
  check(bitsquiggles_spec(BITSQUIGGLES_40, 0, BITSQUIGGLES_STANDARD, NULL) ==
            -1,
        "reject null spec output");
  check(bitsquiggles_pixels(BITSQUIGGLES_40, 0, (BitsquigglesStyle)-1,
                            &grid) == -1,
        "pixels reject negative style");
  check(bitsquiggles_pixels(BITSQUIGGLES_40, 0, BITSQUIGGLES_STANDARD,
                            NULL) == -1,
        "reject null pixel output");
  check(bitsquiggles_edges((BitsquigglesWidth)0, edges) == -1,
        "edges reject invalid width");
  check(bitsquiggles_edges(BITSQUIGGLES_40, NULL) == -1,
        "edges reject null output");
  check(bitsquiggles_free_connection_count(
            BITSQUIGGLES_40, (BitsquigglesMode)-1) == 0,
        "reject negative mode for free count");
  check(bitsquiggles_usable_connection_count(
            BITSQUIGGLES_40, (BitsquigglesMode)-1) == 0,
        "reject negative mode for usable count");
  check(!bitsquiggles_matches_mode(BITSQUIGGLES_40, connections,
                                   (BitsquigglesMode)-1),
        "reject negative mode for membership");
  check(bitsquiggles_mode_label((BitsquigglesMode)-1) == NULL,
        "reject negative mode label");
  check(!bitsquiggles_matches_mode(BITSQUIGGLES_40, NULL,
                                   BITSQUIGGLES_LEFT_RIGHT),
        "reject null membership mask");

  check(bitsquiggles_bip380_checksum_input("qqqqqqqq", &value) == 0 &&
            value == 0,
        "checksum minimum");
  check(bitsquiggles_bip380_checksum_input("llllllll", &value) == 0 &&
            value == BITSQUIGGLES_40_MASK,
        "checksum maximum");
  value = UINT64_C(0x123456789a);
  check(bitsquiggles_bip380_checksum_input("qqqqqqq", &value) == -1 &&
            value == UINT64_C(0x123456789a),
        "reject short checksum without changing output");
  check(bitsquiggles_bip380_checksum_input("qqqqqqqqq", &value) == -1,
        "reject long checksum");
  check(bitsquiggles_bip380_checksum_input("qqqq#qqq", &value) == -1,
        "reject checksum separator");
  check(bitsquiggles_bip380_checksum_input("Qqqqqqqq", &value) == -1,
        "reject uppercase checksum");
  check(bitsquiggles_bip380_checksum_input("qqqqqqqb", &value) == -1,
        "reject non-checksum character");
  check(bitsquiggles_bip380_checksum_input(NULL, &value) == -1,
        "reject null checksum");
  check(bitsquiggles_bip380_checksum_input("qqqqqqqq", NULL) == -1,
        "reject null checksum output");

  check(bitsquiggles_smooth_blobs(BITSQUIGGLES_40, NULL, blobs) == -1,
        "reject null smooth mask");
  check(bitsquiggles_smooth_blobs(BITSQUIGGLES_40, connections, NULL) == -1,
        "reject null smooth output");
  check(bitsquiggles_smooth_blobs((BitsquigglesWidth)0, connections, blobs) ==
            -1,
        "smooth blobs reject invalid width");
  connections[0] = 2;
  check(bitsquiggles_smooth_blobs(BITSQUIGGLES_40, connections, blobs) == -1,
        "reject non-binary smooth mask");
}

int main(int argc, char **argv) {
  const char *fixture32 = argc > 1 ? argv[1] : "fixtures/v1-32.json";
  const char *fixture40 = argc > 2 ? argv[2] : "fixtures/v1-40.json";

  current_test = VARIANT32.label;
  test_edges_and_capacities(&VARIANT32);
  test_golden_vector32();
  test_slash_wrap_regression32();
  test_modes_and_canonical_priority32();
  test_input_bit_avalanche(&VARIANT32);
  test_sampled_injectivity(&VARIANT32);
  test_black_and_white_bitmap_injectivity(&VARIANT32);
  test_style_and_raster_properties(&VARIANT32);
  test_smooth_blobs(&VARIANT32);
  test_shared_fixture(&VARIANT32, fixture32);

  current_test = VARIANT40.label;
  test_edges_and_capacities(&VARIANT40);
  test_golden_vector40();
  test_input_bit_avalanche(&VARIANT40);
  test_style_and_raster_properties(&VARIANT40);
  test_fallback_vectors40();
  test_sampled_injectivity(&VARIANT40);
  test_black_and_white_bitmap_injectivity(&VARIANT40);
  test_smooth_blobs(&VARIANT40);
  test_shared_fixture(&VARIANT40, fixture40);

  current_test = "shared validation";
  test_input_validation();
  printf("BitSquiggles C99 core tests passed (%lu checks)\n", checks);
  return 0;
}