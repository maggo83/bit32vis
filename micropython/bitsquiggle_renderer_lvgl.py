"""Optional LVGL renderer for BitSquiggles.

New integrations should use the width-suffixed operations. The unsuffixed
32-bit operations are deprecated compatibility wrappers.

Grug 2-Clause License: do what want; not sue grug.
"""

import lvgl as lv

import bitsquiggles_core as _core

STANDARD = _core.STANDARD
HIGH_CONTRAST = _core.HIGH_CONTRAST
MONOCHROME = _core.MONOCHROME
BLACK_AND_WHITE = _core.BLACK_AND_WHITE
STYLES = _core.STYLES
bip380_checksum_input = _core.bip380_checksum_input

__all__ = (
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
)


def spec32(bits, style=STANDARD):
    return _core.spec(32, bits, style)


def pixels32(bits, style=STANDARD):
    return _core.pixels(32, bits, style)


def spec40(bits, style=STANDARD):
    return _core.spec(40, bits, style)


def pixels40(bits, style=STANDARD):
    return _core.pixels(40, bits, style)


def _color(hex_value):
    return lv.color_hex(int(hex_value[1:], 16))


def _require_scale(scale):
    if not isinstance(scale, int) or scale < 1:
        raise ValueError("scale must be a positive integer")


def _rgb565_le(hex_value):
    red = int(hex_value[1:3], 16) >> 3
    green = int(hex_value[3:5], 16) >> 2
    blue = int(hex_value[5:7], 16) >> 3
    value = (red << 11) | (green << 5) | blue
    return value & 0xFF, value >> 8


def _raster_descriptor(width, grid, scale):
    grid_width, grid_height, pixels_data, background_hex, foreground_hex = (
        _core.validate_grid(width, grid)
    )
    background = _rgb565_le(background_hex)
    foreground = _rgb565_le(foreground_hex)
    data = bytearray(grid_width * scale * grid_height * scale * 2)
    output_width = grid_width * scale
    for source_y in range(grid_height):
        for source_x in range(grid_width):
            low, high = (
                foreground
                if pixels_data[source_y * grid_width + source_x]
                else background
            )
            for y_offset in range(scale):
                output_y = source_y * scale + y_offset
                for x_offset in range(scale):
                    output_x = source_x * scale + x_offset
                    index = (output_y * output_width + output_x) * 2
                    data[index] = low
                    data[index + 1] = high

    descriptor = lv.image_dsc_t(
        {
            "header": {
                "w": output_width,
                "h": grid_height * scale,
                "cf": lv.COLOR_FORMAT.RGB565,
            },
            "data_size": len(data),
            "data": data,
        }
    )
    return descriptor, data


def _render_raster(width, parent, grid, scale):
    _require_scale(scale)
    descriptor, data = _raster_descriptor(width, grid, scale)
    image = lv.image(parent)
    image.set_src(descriptor)
    image.set_size(grid["width"] * scale, grid["height"] * scale)
    return image, descriptor, data


def _draw_rect(layer, color, x, y, width, height, radius):
    descriptor = lv.draw_rect_dsc_t()
    descriptor.init()
    descriptor.bg_opa = lv.OPA.COVER
    descriptor.border_opa = lv.OPA.TRANSP
    descriptor.bg_color = color
    descriptor.radius = radius
    area = lv.area_t({"x1": x, "y1": y, "x2": x + width - 1, "y2": y + height - 1})
    lv.draw_rect(layer, descriptor, area)


def _render_smooth(width, parent, visual, scale, bordered):
    _require_scale(scale)
    connections = _core.validate_visual(width, visual)
    dimensions = _core._dimensions(width)
    pixel_width = dimensions[3]
    pixel_height = dimensions[4]
    foreground = _color(visual["foreground"]["hex"])
    background = _color(visual["background"]["hex"])

    wrapper = lv.obj(parent)
    wrapper.set_size(pixel_width * scale, pixel_height * scale)
    wrapper.set_style_radius(scale, 0)
    wrapper.set_style_bg_color(background, 0)
    wrapper.set_style_bg_opa(lv.OPA.COVER, 0)
    wrapper.set_style_pad_all(0, 0)
    wrapper.set_style_clip_corner(True, 0)
    wrapper.remove_flag(lv.obj.FLAG.SCROLLABLE)
    if bordered:
        wrapper.set_style_border_color(foreground, 0)
        wrapper.set_style_border_width(1, 0)
        wrapper.set_style_border_opa(lv.OPA.COVER, 0)
    else:
        wrapper.set_style_border_width(0, 0)

    draw_buffer = lv.draw_buf_create(
        pixel_width * scale, pixel_height * scale, lv.COLOR_FORMAT.RGB565, 0
    )
    canvas = lv.canvas(wrapper)
    canvas.set_draw_buf(draw_buffer)
    canvas.set_pos(0, 0)
    canvas.fill_bg(background, lv.OPA.COVER)

    layer = lv.layer_t()
    canvas.init_layer(layer)
    for top, left, bottom, right in _core.smooth_blobs(width, connections):
        x = (1 + 3 * left) * scale
        y = (1 + 3 * top) * scale
        blob_width = (2 + 3 * (right - left)) * scale
        blob_height = (2 + 3 * (bottom - top)) * scale
        _draw_rect(layer, foreground, x, y, blob_width, blob_height, scale)
    canvas.finish_layer(layer)
    return wrapper, draw_buffer


def render_raster32(parent, grid, scale=1):
    """Return an ``(image, descriptor, pixel_buffer)`` owned by the caller."""
    return _render_raster(32, parent, grid, scale)


def render_smooth32(parent, visual, scale=4, bordered=False):
    """Return a ``(wrapper, draw_buffer)`` owned by the caller."""
    return _render_smooth(32, parent, visual, scale, bordered)


def render_raster40(parent, grid, scale=1):
    """Return an ``(image, descriptor, pixel_buffer)`` owned by the caller."""
    return _render_raster(40, parent, grid, scale)


def render_smooth40(parent, visual, scale=4, bordered=False):
    """Return a ``(wrapper, draw_buffer)`` owned by the caller."""
    return _render_smooth(40, parent, visual, scale, bordered)


def spec(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`spec32`."""
    _core.warn_deprecated("spec", "spec32")
    return spec32(bits, style)


def pixels(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`pixels32`."""
    _core.warn_deprecated("pixels", "pixels32")
    return pixels32(bits, style)


def render_raster(parent, grid, scale=1):
    """Deprecated compatibility wrapper for :func:`render_raster32`."""
    _core.warn_deprecated("render_raster", "render_raster32")
    return render_raster32(parent, grid, scale)


def render_smooth(parent, visual, scale=4, bordered=False):
    """Deprecated compatibility wrapper for :func:`render_smooth32`."""
    _core.warn_deprecated("render_smooth", "render_smooth32")
    return render_smooth32(parent, visual, scale, bordered)
