/** Shared BitSquiggles model with an implementation private to renderers. */
module io.github.maggo83.bitsquiggles {
  exports bitsquiggles;
  exports bitsquiggles.internal to
    io.github.maggo83.bitsquiggles.renderer.javafx,
    io.github.maggo83.bitsquiggles.renderer.swing;
}
