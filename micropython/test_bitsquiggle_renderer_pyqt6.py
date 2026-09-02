"""Focused tests for the optional PyQt6 BitSquiggles renderer."""

import os
import unittest
import warnings

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtGui import QGuiApplication

import bitsquiggle_renderer_pyqt6 as renderer
import bitsquiggles_core as core


class PyQt6RendererTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.application = QGuiApplication.instance() or QGuiApplication([])

    def test_narrow_integration_facade(self):
        self.assertNotIn("edges", renderer.__all__)
        self.assertNotIn("matches_mode", renderer.__all__)
        self.assertEqual(renderer.spec32(1), core.spec(32, 1))
        self.assertEqual(renderer.pixels40(1), core.pixels(40, 1))

    def test_exact_raster(self):
        grid = renderer.pixels32(0x836DA7F8, renderer.HIGH_CONTRAST)
        image = renderer.render_raster32(grid, scale=2).toImage()
        self.assertEqual((image.width(), image.height()), (32, 44))
        for row in range(core.variant(32)["pixel_height"]):
            for column in range(core.variant(32)["pixel_width"]):
                expected = grid[
                    "foreground" if grid["pixels"][row * 16 + column] else "background"
                ]["hex"]
                self.assertEqual(image.pixelColor(column * 2, row * 2).name(), expected)

    def test_smooth_rendering(self):
        visual = renderer.spec32(0x836DA7F8, renderer.HIGH_CONTRAST)
        image = renderer.render_smooth32(visual, scale=4).toImage()
        self.assertEqual((image.width(), image.height()), (64, 88))
        self.assertEqual(image.pixelColor(0, 0).alpha(), 0)
        self.assertGreater(image.pixelColor(32, 44).alpha(), 0)

    def test_40_bit_rendering(self):
        value = renderer.bip380_checksum_input("89f8spxm")
        grid = renderer.pixels40(value, renderer.BLACK_AND_WHITE)
        raster = renderer.render_raster40(grid, scale=2).toImage()
        self.assertEqual((raster.width(), raster.height()), (44, 44))

        visual = renderer.spec40(value, renderer.HIGH_CONTRAST)
        smooth = renderer.render_smooth40(visual, scale=4).toImage()
        self.assertEqual((smooth.width(), smooth.height()), (88, 88))

    def test_renderers_reject_the_other_width(self):
        grid32 = core.pixels(32, 0)
        grid40 = core.pixels(40, 0)
        visual32 = core.spec(32, 0)
        visual40 = core.spec(40, 0)
        with self.assertRaises(ValueError):
            renderer.render_raster32(grid40)
        with self.assertRaises(ValueError):
            renderer.render_raster40(grid32)
        with self.assertRaises(ValueError):
            renderer.render_smooth32(visual40)
        with self.assertRaises(ValueError):
            renderer.render_smooth40(visual32)

    def test_deprecated_32_bit_wrappers(self):
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            self.assertEqual(renderer.spec(1), renderer.spec32(1))
            self.assertEqual(renderer.pixels(1), renderer.pixels32(1))
            grid = renderer.pixels32(0)
            self.assertEqual(renderer.render_raster(grid).size().width(), 16)
            visual = renderer.spec32(0)
            self.assertEqual(renderer.render_smooth(visual).size().width(), 64)
        self.assertEqual(len(caught), 4)
        self.assertTrue(
            all(warning.category is DeprecationWarning for warning in caught)
        )


if __name__ == "__main__":
    unittest.main()
