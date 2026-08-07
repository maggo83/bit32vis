# Security Policy

BitSquiggles renders a compact visual fingerprint from an already-derived
32-bit value. It is a comparison aid, not an authentication or authorization
mechanism — see [README.md](README.md) for the full safety boundary. Security
issues in this project are typically about implementation correctness
(for example, a raster or encoding bug that causes two different values to
render identically across ports) rather than classic memory-safety or
network-facing vulnerabilities, but reports of any kind are welcome.

## Supported Versions

This project does not yet maintain multiple release branches. Security fixes
are made against the latest release on the `main` branch. See
[CHANGELOG.md](CHANGELOG.md) and [RELEASING.md](RELEASING.md) for release
history and versioning conventions.

## Reporting a Vulnerability

Please do **not** open a public GitHub issue for security reports.

Instead, report privately using one of the following channels:

- Email: maggo83@proton.me
- Signal: maggo.39
- GPG: encrypt sensitive details to the public key below

Include as much detail as practical:

- Affected port(s) (C, Dart, Java, MicroPython, Python, web) and version/commit.
- Steps to reproduce, including input values (e.g. the 32-bit fingerprint or
  seed data) and expected vs. actual rendered output.
- Impact assessment, if known (e.g. two distinct values producing an
  indistinguishable visual output, a crash, or memory-safety issue in a
  native port).

You should expect an initial response within a few days. Please allow time
for a fix and coordinated disclosure before any public disclosure.

## GPG Public Key

Fingerprint: `F621C84374E52EF6F0F9B6FAA310A5312D2EE2C5`

You can fetch the key from a public keyserver, for example:

```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys F621C84374E52EF6F0F9B6FAA310A5312D2EE2C5
```
