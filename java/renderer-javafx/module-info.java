/** Optional JavaFX renderers for both BitSquiggles core variants. */
module io.github.maggo83.bitsquiggles.renderer.javafx {
  requires transitive io.github.maggo83.bitsquiggles;
  requires transitive javafx.graphics;

  exports bitsquiggles.renderer.javafx;
}
