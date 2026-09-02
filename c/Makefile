CC ?= cc
PYTHON ?= python3
CFLAGS ?= -std=c99 -Wall -Wextra -Werror -pedantic
LDLIBS ?= -lm
BUILD_DIR ?= build
CORE_TEST_BIN := $(BUILD_DIR)/test_bitsquiggles_core
RENDERER_TEST_BIN := $(BUILD_DIR)/test_bitsquiggles_renderer_framebuffer
TEST_BINS := $(CORE_TEST_BIN) $(RENDERER_TEST_BIN)

.PHONY: all check-generated test clean

all: $(TEST_BINS)

$(CORE_TEST_BIN): bitsquiggles_core.c bitsquiggles_core.h \
	test_bitsquiggles_core.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) bitsquiggles_core.c test_bitsquiggles_core.c $(LDLIBS) -o $@

$(RENDERER_TEST_BIN): bitsquiggles_core.c bitsquiggles_core.h \
	bitsquiggles_renderer_framebuffer.c bitsquiggles_renderer_framebuffer.h \
	test_bitsquiggles_renderer_framebuffer.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) bitsquiggles_core.c bitsquiggles_renderer_framebuffer.c \
		test_bitsquiggles_renderer_framebuffer.c $(LDLIBS) -o $@

$(BUILD_DIR):
	mkdir -p $@

check-generated:
	$(PYTHON) generate_packed_tables.py --check

test: check-generated $(TEST_BINS)
	$(CORE_TEST_BIN) ../fixtures/v1.json ../fixtures/v1-40.json
	$(RENDERER_TEST_BIN)

clean:
	rm -rf $(BUILD_DIR)
