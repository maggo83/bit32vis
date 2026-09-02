# Contributing to BitSquiggles

Thanks for helping improve BitSquiggles.

## Start here

- Read [the project overview](README.md) for purpose, status, and safety limits.
- Treat [the specification](SPEC.md) and its linked chapters as the normative
  algorithm and API contract.
- Use the [reference implementation guides](README.md#reference-implementation-guides)
  for port-specific setup and commands.

## Change rules

1. Propose behavior, format, or API changes in the owning normative chapter
  linked from [SPEC.md](SPEC.md) first.
2. Keep every maintained core implementation conformant with
  `fixtures/v1-32.json` and `fixtures/v1-40.json`.
3. Keep the core dependency-free; renderers and demos remain optional layers.
4. Regenerate and verify tracked generated artifacts according to their owning
  port guides.
5. Do not weaken the project’s safety boundary: BitSquiggles compares an
  already-derived 32-bit or 40-bit value and is not authentication or
  authorization.
6. Follow the shared-version and release requirements in [RELEASING.md](RELEASING.md).

## Validate before opening a pull request

Run the checks specified by the port guides and the repository verification
workflow. At minimum, verify the Java tests and generated artifacts, the Python
fixture/property tests, and the JavaScript core/playground tests.
For a hw renderer change, also verify exact and smooth output in the target
simulator and on representative hardware. For a desktop renderer change,
compile the optional renderer module against its target UI toolkit and inspect
both exact and smooth output.

The local Git hook can regenerate generated outputs:

```bash
git config core.hooksPath .githooks
```

CI is the final verification authority; the workflow definition is in
[.github/workflows/verify-generated-gallery.yml](.github/workflows/verify-generated-gallery.yml).

## Pull requests

Keep pull requests focused. Explain the user-visible effect, reference the
relevant specification section, and include tests for behavior changes. If a
change intentionally affects only one optional renderer or demo, state why core
and cross-port behavior are unchanged.
