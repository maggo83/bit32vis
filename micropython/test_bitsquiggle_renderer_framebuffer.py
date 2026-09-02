"""Focused tests for the dependency-free BitSquiggles framebuffer renderer.

Grug 2-Clause License: do what want; not sue grug.
"""

import bitsquiggle_renderer_framebuffer as renderer
import bitsquiggles_core as core


_checks = 0


def check(condition, message):
    global _checks
    _checks += 1
    if not condition:
        raise AssertionError(message)


def expect_failure(function, message):
    global _checks
    _checks += 1
    try:
        function()
    except ValueError:
        return
    raise AssertionError(message)


class _RecordingFramebuffer:
    def __init__(self):
        self.calls = []

    def fill_rect(self, x, y, width, height, color):
        self.calls.append((x, y, width, height, color))


def _one_bit_color(color):
    if color == "#000000":
        return 0
    if color == "#ffffff":
        return 1
    raise ValueError("expected black-and-white color")


def test_integration_surface():
    expected = {
        "STANDARD",
        "HIGH_CONTRAST",
        "MONOCHROME",
        "BLACK_AND_WHITE",
        "STYLES",
        "bip380_checksum_input",
        "spec32",
        "pixels32",
        "render_raster32",
        "spec40",
        "pixels40",
        "render_raster40",
        "spec",
        "pixels",
        "render_raster",
    }
    check(set(renderer.__all__) == expected, "declares the integration surface")
    check(renderer.spec32(1) == core.spec(32, 1), "exposes 32-bit core output")
    check(renderer.pixels40(1) == core.pixels(40, 1), "exposes 40-bit core output")
    check(
        renderer.bip380_checksum_input("89f8spxm") == 0x39527804DB,
        "exposes BIP380 checksum conversion",
    )


def _assert_exact_renderer(width, values, pixels, render_raster):
    for value in values:
        grid = pixels(value, renderer.BLACK_AND_WHITE)
        target = _RecordingFramebuffer()
        render_raster(target, grid, x=5, y=7, scale=2, color_mapper=_one_bit_color)

        background = _one_bit_color(grid["background"]["hex"])
        foreground = _one_bit_color(grid["foreground"]["hex"])
        check(
            target.calls[0]
            == (5, 7, grid["width"] * 2, grid["height"] * 2, background),
            "%d-bit renderer paints the complete scaled background first" % width,
        )
        rendered = bytearray(grid["width"] * grid["height"])
        for call_x, call_y, call_width, call_height, color in target.calls[1:]:
            check(call_width == 2, "%d-bit renderer uses vertical runs" % width)
            check(color == foreground, "%d-bit renderer uses foreground color" % width)
            start_column = (call_x - 5) // 2
            start_row = (call_y - 7) // 2
            run_length = call_height // 2
            check(
                call_x == 5 + start_column * 2
                and call_y == 7 + start_row * 2
                and call_height == run_length * 2,
                "%d-bit renderer aligns runs to scaled pixels" % width,
            )
            check(
                start_row == 0
                or not grid["pixels"][(start_row - 1) * grid["width"] + start_column],
                "%d-bit renderer starts a maximal vertical run" % width,
            )
            check(
                start_row + run_length == grid["height"]
                or not grid["pixels"][
                    (start_row + run_length) * grid["width"] + start_column
                ],
                "%d-bit renderer ends a maximal vertical run" % width,
            )
            for row in range(start_row, start_row + run_length):
                rendered[row * grid["width"] + start_column] = 1
        check(
            rendered == grid["pixels"],
            "%d-bit renderer reproduces every foreground pixel" % width,
        )


def test_exact_rendering():
    _assert_exact_renderer(
        32,
        (0, 1, 0x89ABCDEF, 0xFFFFFFFF),
        renderer.pixels32,
        renderer.render_raster32,
    )
    _assert_exact_renderer(
        40,
        (0, 1, 0x39527804DB, 0xFFFFFFFFFF),
        renderer.pixels40,
        renderer.render_raster40,
    )


def test_validation():
    grid32 = renderer.pixels32(0, renderer.BLACK_AND_WHITE)
    grid40 = renderer.pixels40(0, renderer.BLACK_AND_WHITE)
    expect_failure(
        lambda: renderer.render_raster32(object(), grid32),
        "reject framebuffer without fill_rect",
    )
    expect_failure(
        lambda: renderer.render_raster32(_RecordingFramebuffer(), grid32, scale=0),
        "reject non-positive scale",
    )
    expect_failure(
        lambda: renderer.render_raster32(
            _RecordingFramebuffer(), grid32, color_mapper=object()
        ),
        "reject non-callable color mapper",
    )
    bad_grid = dict(grid32)
    bad_grid["width"] = 15
    expect_failure(
        lambda: renderer.render_raster32(_RecordingFramebuffer(), bad_grid),
        "reject non-canonical dimensions",
    )
    expect_failure(
        lambda: renderer.render_raster32(_RecordingFramebuffer(), grid40),
        "32-bit renderer rejects a 40-bit grid",
    )
    expect_failure(
        lambda: renderer.render_raster40(_RecordingFramebuffer(), grid32),
        "40-bit renderer rejects a 32-bit grid",
    )


def test_deprecated_32_bit_wrappers_under_cpython():
    try:
        import sys
        import warnings
    except ImportError:
        return
    implementation = getattr(getattr(sys, "implementation", None), "name", "")
    if implementation != "cpython":
        return

    def deprecated_call(function, message):
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            try:
                return function()
            finally:
                check(
                    len(caught) == 1 and caught[0].category is DeprecationWarning,
                    message,
                )

    check(
        deprecated_call(lambda: renderer.spec(1), "deprecated spec warning")
        == renderer.spec32(1),
        "deprecated spec is 32-bit",
    )
    check(
        deprecated_call(lambda: renderer.pixels(1), "deprecated pixels warning")
        == renderer.pixels32(1),
        "deprecated pixels is 32-bit",
    )
    grid32 = renderer.pixels32(0, renderer.BLACK_AND_WHITE)
    target = _RecordingFramebuffer()
    deprecated_call(
        lambda: renderer.render_raster(target, grid32),
        "deprecated raster warning",
    )
    check(bool(target.calls), "deprecated raster renders a 32-bit grid")

    grid40 = renderer.pixels40(0, renderer.BLACK_AND_WHITE)
    expect_failure(
        lambda: deprecated_call(
            lambda: renderer.render_raster(_RecordingFramebuffer(), grid40),
            "deprecated cross-width raster warning",
        ),
        "deprecated raster remains 32-bit",
    )


def main():
    test_integration_surface()
    test_exact_rendering()
    test_validation()
    test_deprecated_32_bit_wrappers_under_cpython()
    print("BitSquiggles framebuffer renderer tests passed (%d checks)" % _checks)


if __name__ == "__main__":
    main()
