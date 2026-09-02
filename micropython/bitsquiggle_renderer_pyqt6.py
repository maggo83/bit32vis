"""Optional PyQt6 renderer for BitSquiggles.

New integrations should use the width-suffixed operations. The unsuffixed
32-bit operations are deprecated compatibility wrappers.

Grug 2-Clause License: do what want; not sue grug.
"""

from PyQt6.QtCore import QRectF, Qt
from PyQt6.QtGui import QColor, QPainter, QPainterPath, QPixmap

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


def _require_scale(scale):
    if not isinstance(scale, int) or scale < 1:
        raise ValueError("scale must be a positive integer")


def _render_raster(width, grid, scale):
    _require_scale(scale)
    grid_width, grid_height, pixel_data, background, foreground = _core.validate_grid(
        width, grid
    )
    pixmap = QPixmap(grid_width * scale, grid_height * scale)
    pixmap.fill(QColor(background))
    painter = QPainter(pixmap)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor(foreground))
    for index, pixel in enumerate(pixel_data):
        if pixel:
            row, column = divmod(index, grid_width)
            painter.drawRect(column * scale, row * scale, scale, scale)
    painter.end()
    return pixmap


def _render_smooth(width, visual, scale):
    _require_scale(scale)
    connections = _core.validate_visual(width, visual)
    dimensions = _core._dimensions(width)
    pixel_width = dimensions[3]
    pixel_height = dimensions[4]
    pixmap = QPixmap(pixel_width * scale, pixel_height * scale)
    pixmap.fill(Qt.GlobalColor.transparent)

    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor(visual["background"]["hex"]))
    painter.drawRoundedRect(
        QRectF(0, 0, pixel_width * scale, pixel_height * scale), scale, scale
    )

    foreground = QPainterPath()
    foreground.setFillRule(Qt.FillRule.WindingFill)
    for top, left, bottom, right in _core.smooth_blobs(width, connections):
        x = (1 + 3 * left) * scale
        y = (1 + 3 * top) * scale
        blob_width = (2 + 3 * (right - left)) * scale
        blob_height = (2 + 3 * (bottom - top)) * scale
        foreground.addRoundedRect(QRectF(x, y, blob_width, blob_height), scale, scale)

    painter.setBrush(QColor(visual["foreground"]["hex"]))
    painter.drawPath(foreground)
    painter.end()
    return pixmap


def render_raster32(grid, scale=1):
    return _render_raster(32, grid, scale)


def render_smooth32(visual, scale=4):
    return _render_smooth(32, visual, scale)


def render_raster40(grid, scale=1):
    return _render_raster(40, grid, scale)


def render_smooth40(visual, scale=4):
    return _render_smooth(40, visual, scale)


def spec(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`spec32`."""
    _core.warn_deprecated("spec", "spec32")
    return spec32(bits, style)


def pixels(bits, style=STANDARD):
    """Deprecated compatibility wrapper for :func:`pixels32`."""
    _core.warn_deprecated("pixels", "pixels32")
    return pixels32(bits, style)


def render_raster(grid, scale=1):
    """Deprecated compatibility wrapper for :func:`render_raster32`."""
    _core.warn_deprecated("render_raster", "render_raster32")
    return render_raster32(grid, scale)


def render_smooth(visual, scale=4):
    """Deprecated compatibility wrapper for :func:`render_smooth32`."""
    _core.warn_deprecated("render_smooth", "render_smooth32")
    return render_smooth32(visual, scale)
