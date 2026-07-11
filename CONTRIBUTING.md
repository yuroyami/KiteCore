# Contributing to KiteCore

This guide covers building and contributing to KiteCore.

## Building

- JDK 21 (the build uses a 21 toolchain; the published JVM artifact targets 11).
- `./gradlew :kitecore:allTests` runs the full test matrix. Browser tests
  (`jsBrowserTest`, `wasmJsBrowserTest`) need a local Chrome; Apple simulator
  tests need Xcode.

## Rules

- **Public API is guarded.** Explicit API mode is on, and the ABI dump is
  committed. If you change the public surface, regenerate the dump
  (`./gradlew :kitecore:updateKotlinAbi`; check `./gradlew :kitecore:tasks --all | grep -i abi`
  if the task name differs) and include it in the same commit.
- **The worker protocol has three implementations.** The jsMain `KiteWorker`,
  the wasmJsMain `KiteWorker`, and kmp-ssot's `generateIoWorkerSource` implement
  one wire protocol (v2: `{ id, payload }` request, `{ id, ok, result | error }`
  reply). A protocol bug fixed in one copy must be fixed in all of them.
- Every public declaration carries KDoc. README examples must be real outputs
  of the code, not aspirational ones.
- Tests accompany behavior changes, especially on the web targets.

## Commits

Small, focused commits with imperative subjects. Reference the phase/finding if
you're working from an audit document.
