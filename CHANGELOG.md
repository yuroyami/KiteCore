# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — unreleased

### Added

- `ioDispatcher()` — one IO dispatcher on every target: the real `Dispatchers.IO`
  on JVM, Android, iOS and macOS (public on Kotlin/Native since coroutines 1.7);
  on the web targets, the dispatcher installed via `installIoDispatcher(...)`,
  falling back to `Dispatchers.Default` with a one-time console warning.
- `Dispatchers.KiteIO` — sugar for `ioDispatcher()`.
- `KiteWorker` + `kiteOffload` on `js` **and** `wasmJs` — inline Blob-URL Web
  Worker offload. Protocol v2: request/reply correlation ids make calls
  cancellation-safe (a reply to a cancelled call is dropped, never delivered to
  the next caller), worker script load failures fail fast instead of hanging,
  `close()` fails an in-flight call with `KiteWorkerException`, results are
  normalized so an `undefined` job result round-trips as `"undefined"` on both
  targets, and Blob object URLs are revoked after Worker construction.
- `Platform.current` — platform identity (`name`, `osVersion`, `deviceModel`,
  `family`) resolved per target, with real device models on Apple
  (`sysctlbyname`) and sane OS fields on every target.
- Targets: `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`,
  `macosArm64`, `js(browser, nodejs)`, `wasmJs(browser, nodejs)`.
  (`macosX64` is deliberately absent — deprecated for removal in Kotlin 2.4.)
- Explicit API mode and a committed ABI dump guard the public surface.
- `KiteWeak<T>` — common weak references: `java.lang.ref.WeakReference` on
  JVM/Android, `kotlin.native.ref.WeakReference` on Apple, the ES2021 `WeakRef`
  global on JS (JS primitives fall back to a strong hold — they are not valid
  `WeakRef` targets), and an honest strong-hold fallback on Wasm, where the
  platform has no weak primitive; `KiteWeak.isWeakSupported` reports the
  difference.
- `KiteFuture<T>` / `kiteAsync { }` / `KiteProgress` — progress-reporting
  futures, pure commonMain. `KiteFuture` *is* a `Deferred` (delegation), plus a
  conflated `StateFlow<KiteProgress>` (fraction + sticky phase label,
  indeterminate support). `slice(weight)` carves sequential sub-windows for
  multi-stage jobs; `report(done, total)` covers counter-style parallel work;
  success auto-completes progress to 100%, failure/cancellation freeze it.
  Policy (throttling, monotonic bars, ETA) is deliberately left to `Flow`
  operators at the consumer.

### Removed

- iOS framework binaries are no longer produced by the default build. Maven
  consumers compile against klibs; an XCFramework distribution channel is a
  separate future feature.

### Known gaps

- Node runs skip the `KiteWorker` tests: Node has no Web `Worker` global, and
  `worker_threads` is a different API (future feature, not a test shim).
- Android host tests run `commonTest` against JVM stub values of
  `android.os.Build` (fields read as null/0 off-device); device-true values are
  exercised on real Android only.
