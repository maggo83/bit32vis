/** Optional Swing/Java2D renderers for both BitSquiggles core variants. */
module io.github.maggo83.bitsquiggles.renderer.swing {
  requires transitive io.github.maggo83.bitsquiggles;
  requires transitive java.desktop;

  exports bitsquiggles.renderer.swing;
}
