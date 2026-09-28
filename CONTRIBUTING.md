# Contributing to KiteCore

This guide covers building and contributing to KiteCore.

## Building

- JDK 21 (the build uses a 21 toolchain; the published JVM artifact targets 11).
- `./gradlew :kitecore:allTests` runs the full test matrix. Browser tests
  (`jsBrowserTest`, `wasmJsBrowserTest`) need a local Chrome; Apple simulator
  tests need Xcode.
- Browser tests find no tests when the checkout path contains `#`. The test
  runner loads files by URL, and a URL ends at `#`. Clone into a path without it.

## Rules

- **Public API is guarded.** Explicit API mode is on, and the ABI dump is
  committed. If you change the public surface, regenerate the dump
  (`./gradlew :kitecore:updateKotlinAbi`; check `./gradlew :kitecore:tasks --all | grep -i abi`
  if the task name differs) and include it in the same commit.
- Every public declaration carries KDoc. README examples must be real outputs
  of the code, not aspirational ones.
- Tests accompany behavior changes, especially on the web targets.

## Commits

Small, focused commits with imperative subjects. Reference the phase/finding if
you're working from an audit document.
