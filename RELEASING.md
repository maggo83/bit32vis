# Releasing BitSquiggles

This document is the single source for BitSquiggles versioning and release
procedure. It applies to the project and every maintained port.

## Version policy

BitSquiggles has one shared version across the Java, Python/MicroPython,
JavaScript/TypeScript, and C99 ports. A release tag, package metadata,
changelog entry, fixture schema compatibility statement, and published
artifacts use that same version.

- A core behavior, normative API, exact-raster, color, or fixture change updates
  every maintained port in the same release.
- A renderer/demo-only change may be released at the shared version without
  changing the core algorithm, but it must state that scope in the changelog.
- A change to the normative behavior in [SPEC.md](SPEC.md) or its linked
  chapters, the common core API, or fixture schema requires an explicit
  compatibility review before release.
- Until 1.0, breaking changes are permitted only when documented in the
  changelog and released consistently across all maintained ports.

The current version is `0.1.0-beta.1`, tagged as the first reference point for
beta testers and integrators. No package registry artifact (PyPI, npm, Maven
Central) has been published yet; the tagged source and a GitHub Release are
the only published artifacts so far.

## Release checklist

1. Confirm that [SPEC.md](SPEC.md), its affected normative chapters, the port
  guides, package metadata, and `CHANGELOG.md` agree on the release version
  and compatibility notes.
2. Run the repository verification workflow checks locally or in CI: Java core,
   renderer/demo tests, generated gallery, generated fixture, CPython/MicroPython
   harness, C core and framebuffer renderer tests, JavaScript core test,
   playground smoke test, and package dry run.
  When changing the optional LVGL renderer, also validate exact and smooth
  output in an LVGL simulator and on representative hardware.
3. Regenerate and review `docs/examples/` and `fixtures/v1.json` with the Java
   generators when applicable.
4. Confirm that every maintained core port consumes the current fixture in its
   host-side conformance test.
5. Create an annotated version tag after the release commit is reviewed.
6. Publish only artifacts built from that tag. Record package names, artifact
   checksums, and fixture schema/version in the corresponding release notes.
7. Verify package installation from the published artifacts before announcing
   the release.

## Artifact policy

- Java core, optional renderers, and demos are independently selectable
  artifacts/modules; a core consumer must not acquire renderer dependencies.
- The Python distribution packages the same core source used for MicroPython
  vendoring. The optional LVGL renderer is separately vendored source and is
  not part of the dependency-free CPython package.
- The JavaScript package publishes the dependency-free ESM core and explicitly
  named optional renderer subpaths.
- The C99 distribution consists of the dependency-free core and framebuffer
  renderer headers and sources plus their conformance harnesses. Applications
  include only the renderer header; it has no external framework dependency.
- Publication to Maven Central, PyPI, or npm requires an approved maintainer
  account and the registry name reserved by the project.

## Security and support

Releases remain experimental until the project changes that status in the root
README. Release notes must preserve the safety boundary: BitSquiggles compares
an already-derived 32-bit or 40-bit value and is not authentication or
authorization.
