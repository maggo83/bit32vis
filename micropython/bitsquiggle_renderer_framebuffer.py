"""Exact-raster framebuffer renderer for BitSquiggles.

New integrations should use the width-suffixed operations. The unsuffixed
32-bit operations are deprecated compatibility wrappers.

Grug 2-Clause License: do what want; not sue grug.
"""

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
    "spec40",
    "pixels40",
    "render_raster40",
    "spec",
    "pixels",
    "render_raster",
)


def spec32(bits, style=STANDARD):
    return _core.spec(32, bits, style)


def pixels32(bits, style=STANDARD):
    return _core.pixels(32, bits, style)


def spec40(bits, style=STANDARD):
    return _core.spec(40, bits, style)


def pixels40(bits, style=STANDARD):
    return _core.pixels(40, bits, style)


def _require_scale(scale):
    if not isinstance(scale, int) or scale < 1:
        raise ValueError("scale must be a positive integer")


def _render_raster(width, target, grid, x, y, scale, color_mapper):
    _require_scale(scale)
    if not callable(getattr(target, "fill_rect", None)):
        raise ValueError("target must provide fill_rect")
    if color_mapper is not None and not callable(color_mapper):
        raise ValueError("color_mapper must be callable")

    grid_width, grid_height, pixel_data, background, foreground = _core.validate_grid(
        width, grid
    )
    if color_mapper is not None:
        background = color_mapper(background)
        foreground = color_mapper(foreground)
    target.fill_rect(x, y, grid_width * scale, grid_height * scale, background)

    for column in range(grid_width):
        run_start = -1
        for row in range(grid_height + 1):
            active = row < grid_height and pixel_data[row * grid_width + column]
            if active and run_start < 0:
                run_start = row
            elif not active and run_start >= 0:
                target.fill_rect(
                    x + column * scale,
                    y + run_start * scale,
                    scale,
                    (row - run_start) * scale,
                    foreground,
                )
                run_start = -1


def render_raster32(target, grid, x=0, y=0, scale=1, color_mapper=None):
    """Paint a canonical 32-bit exact raster."""
    return _render_raster(32, target, grid, x, y, scale, color_mapper)


def render_raster40(target, grid, x=0, y=0, scale=1, color_mapper=None):
    """Paint a canonical 40-bit exact raster."""
    return _render_raster(40, target, grid, x, y, scale, color_mapper)


def spec(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`spec32`."""
    _core.warn_deprecated("spec", "spec32")
    return spec32(bits, style)


def pixels(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`pixels32`."""
    _core.warn_deprecated("pixels", "pixels32")
    return pixels32(bits, style)


def render_raster(target, grid, x=0, y=0, scale=1, color_mapper=None):
    """Deprecated compatibility wrapper for :func:`render_raster32`."""
    _core.warn_deprecated("render_raster", "render_raster32")
    return render_raster32(target, grid, x, y, scale, color_mapper)
