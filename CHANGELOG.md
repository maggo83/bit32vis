# Changelog

All notable BitSquiggles changes are recorded here. Versioning and publication
rules are defined in [RELEASING.md](RELEASING.md).

## Unreleased

- Renamed the 32-bit conformance fixture from `fixtures/v1.json` to
  `fixtures/v1-32.json`, matching `fixtures/v1-40.json`.
- Added BitSquiggle40 to the Java port, consolidated both widths in one
  dependency-free internal core with neutral shared result types and
  generators, and retained one Swing and one JavaFX renderer class as the
  public width-specific entry points. Applications can access shared types
  through their selected renderer import, and the BIP380 adapter is
  deliberately unsuffixed.
- Added BitSquiggle40 to the C99 port and consolidated both widths behind one
  width-selected implementation, shared result types, and generated packed
  mode tables. Added an allocation-free fill-rectangle renderer whose single
  application header exposes width-specific spec, pixel, and raster operations.
- Added BitSquiggle40 to the Python/MicroPython port and consolidated both
  widths in one dependency-free core with separate framebuffer, LVGL, and
  PyQt6 renderers.
- Replaced runtime mode-definition structures with generated packed tables
  suitable for frozen MicroPython modules.
- Changed the LVGL renderer to return caller-owned resource tuples, remove
  global per-render retention, and pass RGB565 raster bytearrays without
  copying them.
- Batched framebuffer foreground pixels into maximal vertical runs.

## 0.1.0-beta.1 — 2026-07-22

First tagged reference release: a stable point for beta testers and potential
collaborators to integrate against. No further core changes are planned
imminently, but encoding details may still evolve based on integration
feedback before a stable 1.0.

- Dependency-free Java, Python/MicroPython, JavaScript/TypeScript, C99, and
  Dart cores, each with shared-fixture conformance coverage.
- Shared normative specification and versioned cross-port conformance fixture.
- Optional Swing/Java2D, JavaFX, PyQt6, MicroPython LVGL, and Flutter
  exact-raster and smooth renderers.
- Static GitHub Pages playground.
- Proof-of-concept integrations verified in simulators for Sparrow,
  Bitcoin Safe, Bull Bitcoin, BitBox, ColdCard, and Specter; the Specter
  integration was additionally verified on physical hardware. See
  [PoC integration branches](README.md#poc-integration-branches) for the fork
  and branch references.
