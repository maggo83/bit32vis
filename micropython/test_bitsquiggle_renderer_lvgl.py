"""Focused CPython-fake tests for the optional BitSquiggles LVGL renderer.

Grug 2-Clause License: do what want; not sue grug.
"""

import sys
import types
import warnings

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


class _ColorFormat:
    RGB565 = "RGB565"


class _Opacity:
    COVER = "cover"
    TRANSP = "transparent"


class _DrawBuffer:
    def __init__(self, width, height, color_format, stride):
        self.arguments = (width, height, color_format, stride)
        self.destroyed = False

    def destroy(self):
        self.destroyed = True


class _Object:
    class FLAG:
        SCROLLABLE = "scrollable"

    def __init__(self, parent):
        self.parent = parent
        self.calls = []

    def __getattr__(self, name):
        def record(*arguments):
            self.calls.append((name, arguments))

        return record


class _Image(_Object):
    def set_src(self, descriptor):
        self.descriptor = descriptor

    def set_size(self, width, height):
        self.size = (width, height)


class _Canvas(_Object):
    def set_draw_buf(self, draw_buffer):
        self.draw_buffer = draw_buffer

    def init_layer(self, layer):
        layer.canvas = self


class _DrawRectDescriptor:
    def init(self):
        self.initialized = True


class _Layer:
    pass


class _Descriptor:
    def __init__(self, values):
        self.values = values


class _Area:
    def __init__(self, values):
        self.values = values


def _fake_lvgl():
    fake = types.ModuleType("lvgl")
    fake.COLOR_FORMAT = _ColorFormat
    fake.OPA = _Opacity
    fake.obj = _Object
    fake.image = _Image
    fake.canvas = _Canvas
    fake.draw_rect_dsc_t = _DrawRectDescriptor
    fake.layer_t = _Layer
    fake.image_dsc_t = _Descriptor
    fake.area_t = _Area
    fake.draw_buffers = []
    fake.draw_rect_calls = []

    def color_hex(value):
        return value

    def draw_buf_create(width, height, color_format, stride):
        draw_buffer = _DrawBuffer(width, height, color_format, stride)
        fake.draw_buffers.append(draw_buffer)
        return draw_buffer

    def draw_rect(layer, descriptor, area):
        fake.draw_rect_calls.append((layer, descriptor, area))

    fake.color_hex = color_hex
    fake.draw_buf_create = draw_buf_create
    fake.draw_rect = draw_rect
    return fake


_saved_lvgl = sys.modules.get("lvgl")
_fake = _fake_lvgl()
sys.modules["lvgl"] = _fake
import bitsquiggle_renderer_lvgl as renderer


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
        "render_smooth32",
        "spec40",
        "pixels40",
        "render_raster40",
        "render_smooth40",
        "spec",
        "pixels",
        "render_raster",
        "render_smooth",
    }
    check(set(renderer.__all__) == expected, "declares the integration surface")
    check(renderer.spec32(1) == core.spec(32, 1), "exposes 32-bit core output")
    check(renderer.spec40(1) == core.spec(40, 1), "exposes 40-bit core output")
    check(renderer.pixels32(1) == core.pixels(32, 1), "exposes 32-bit raster")
    check(renderer.pixels40(1) == core.pixels(40, 1), "exposes 40-bit raster")


def test_raster_descriptors_and_ownership():
    grid32 = renderer.pixels32(0, renderer.BLACK_AND_WHITE)
    image32, descriptor_object32, pixel_buffer32 = renderer.render_raster32(
        "parent-32", grid32, scale=2
    )
    descriptor32 = image32.descriptor.values
    check(image32.parent == "parent-32", "32-bit image keeps its parent")
    check(image32.size == (32, 44), "32-bit image dimensions")
    check(
        descriptor32["header"] == {"w": 32, "h": 44, "cf": "RGB565"},
        "32-bit descriptor header",
    )
    check(descriptor32["data_size"] == 32 * 44 * 2, "32-bit descriptor data size")
    check(
        descriptor_object32 is image32.descriptor,
        "raster result returns the image descriptor",
    )
    check(isinstance(pixel_buffer32, bytearray), "raster result returns a bytearray")
    check(
        descriptor32["data"] is pixel_buffer32,
        "descriptor uses the returned bytearray without copying",
    )
    check(
        descriptor32["data"][:2]
        == bytes(renderer._rgb565_le(grid32["background"]["hex"])),
        "background is little-endian RGB565",
    )

    repeated, repeated_descriptor, repeated_buffer = renderer.render_raster32(
        "other-parent", grid32, scale=2
    )
    check(
        repeated.descriptor is repeated_descriptor,
        "repeated raster returns its descriptor",
    )
    check(
        repeated_descriptor is not descriptor_object32,
        "separate raster renders own separate descriptors",
    )
    check(
        repeated_buffer is not pixel_buffer32,
        "separate raster renders own separate buffers",
    )

    grid40 = renderer.pixels40(0, renderer.BLACK_AND_WHITE)
    image40, descriptor40, pixel_buffer40 = renderer.render_raster40(
        "parent-40", grid40, scale=2
    )
    check(image40.size == (44, 44), "40-bit image dimensions")
    check(image40.descriptor is descriptor40, "40-bit result returns its descriptor")
    check(
        image40.descriptor.values["data"] is pixel_buffer40,
        "40-bit descriptor uses its returned bytearray without copying",
    )
    check(not hasattr(renderer, "_RASTER_CACHE"), "renderer retains no raster cache")


def test_smooth_construction():
    _fake.draw_rect_calls.clear()
    visual32 = renderer.spec32(0x836DA7F8, renderer.HIGH_CONTRAST)
    wrapper32, draw_buffer32 = renderer.render_smooth32(
        "parent-32", visual32, scale=4, bordered=True
    )
    check(wrapper32.parent == "parent-32", "smooth wrapper keeps its parent")
    check(("set_size", (64, 88)) in wrapper32.calls, "32-bit smooth dimensions")
    check(("set_style_border_width", (1, 0)) in wrapper32.calls, "border enabled")
    check(bool(_fake.draw_rect_calls), "smooth rendering draws canonical blobs")
    check(
        draw_buffer32.arguments == (64, 88, "RGB565", 0),
        "32-bit smooth draw buffer dimensions",
    )
    check(draw_buffer32 is _fake.draw_buffers[-1], "smooth result returns draw buffer")

    visual40 = renderer.spec40(0x39527804DB, renderer.HIGH_CONTRAST)
    wrapper40, draw_buffer40 = renderer.render_smooth40(
        "parent-40", visual40, scale=4
    )
    check(("set_size", (88, 88)) in wrapper40.calls, "40-bit smooth dimensions")
    check(("set_style_border_width", (0, 0)) in wrapper40.calls, "border disabled")
    check(draw_buffer40 is _fake.draw_buffers[-1], "40-bit result returns draw buffer")

    draw_buffer32.destroy()
    check(draw_buffer32.destroyed, "caller can destroy its smooth draw buffer")
    check(
        not hasattr(renderer, "_SMOOTH_DRAW_BUFS"),
        "renderer retains no smooth draw buffers",
    )


def test_validation():
    grid32 = renderer.pixels32(0)
    grid40 = renderer.pixels40(0)
    visual32 = renderer.spec32(0)
    visual40 = renderer.spec40(0)
    expect_failure(
        lambda: renderer.render_raster32("parent", grid32, scale=0),
        "reject non-positive raster scale",
    )
    expect_failure(
        lambda: renderer.render_smooth32("parent", visual32, scale=1.5),
        "reject non-integer smooth scale",
    )
    expect_failure(
        lambda: renderer.render_raster32("parent", grid40),
        "32-bit raster rejects a 40-bit grid",
    )
    expect_failure(
        lambda: renderer.render_raster40("parent", grid32),
        "40-bit raster rejects a 32-bit grid",
    )
    expect_failure(
        lambda: renderer.render_smooth32("parent", visual40),
        "32-bit smooth renderer rejects a 40-bit visual",
    )
    expect_failure(
        lambda: renderer.render_smooth40("parent", visual32),
        "40-bit smooth renderer rejects a 32-bit visual",
    )


def _deprecated_call(function, message):
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        try:
            return function()
        finally:
            check(
                len(caught) == 1 and caught[0].category is DeprecationWarning,
                message,
            )


def test_deprecated_32_bit_wrappers():
    check(
        _deprecated_call(lambda: renderer.spec(1), "deprecated spec warning")
        == renderer.spec32(1),
        "deprecated spec is 32-bit",
    )
    check(
        _deprecated_call(lambda: renderer.pixels(1), "deprecated pixels warning")
        == renderer.pixels32(1),
        "deprecated pixels is 32-bit",
    )
    grid32 = renderer.pixels32(0)
    raster, _descriptor, _pixel_buffer = _deprecated_call(
        lambda: renderer.render_raster("parent", grid32),
        "deprecated raster warning",
    )
    check(raster.size == (16, 22), "deprecated raster renders 32-bit output")
    visual32 = renderer.spec32(0)
    smooth, _draw_buffer = _deprecated_call(
        lambda: renderer.render_smooth("parent", visual32),
        "deprecated smooth warning",
    )
    check(("set_size", (64, 88)) in smooth.calls, "deprecated smooth is 32-bit")


def main():
    test_integration_surface()
    test_raster_descriptors_and_ownership()
    test_smooth_construction()
    test_validation()
    test_deprecated_32_bit_wrappers()
    print("BitSquiggles LVGL renderer fake tests passed (%d checks)" % _checks)


if __name__ == "__main__":
    try:
        main()
    finally:
        del sys.modules["bitsquiggle_renderer_lvgl"]
        if _saved_lvgl is None:
            del sys.modules["lvgl"]
        else:
            sys.modules["lvgl"] = _saved_lvgl
