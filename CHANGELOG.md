# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

KiteCore now keeps only the APIs that fill a real gap in `commonMain`. This is
a breaking change. Every kept API has the same signature as in 0.1.0.

### Removed

- `WebWorker`, `WebWorkerException`, and `runInWorker` on js and wasmJs.
- The `collections`, `encoding`, `math`, `random`, `structures`, `time`, and
  `util` packages.
- From `text`: everything except `format` and `StringFormatException`.
- From `coroutines`: everything except `retryWithBackoff`,
  `runCatchingCancellable`, `parallelMap`, `parallelForEach`, `SingleFlight`,
  and `suspendLazy`.
- From `flow`: everything except `throttleFirst`, `mapAsync`, `mapState`, and
  `combineStates`.

### Changed

- The web fallback warning of `ioDispatcher` no longer points to `WebWorker`.

### Migration

Most removed APIs have a direct replacement in the Kotlin standard library
(2.2 or later), in kotlinx-coroutines, or in a kept KiteCore API.

| Removed | Use instead |
| --- | --- |
| `toBase64`, `decodeBase64`, `toBase64Url`, `decodeBase64Url` | `kotlin.io.encoding.Base64`. For the unpadded URL-safe form, use `Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)`. |
| `toHex`, `decodeHex` | `toHexString()` and `hexToByteArray()` |
| `Flow.chunked(size)` | `chunked(size)` from kotlinx-coroutines. The two had the same signature, so a file that imported both packages did not compile. |
| `timeoutBetweenEmissions` | `Flow.timeout` |
| `flowFromSuspend` | `(suspend () -> T).asFlow()` |
| `firstOrDefault`, `lastOrDefault` | `firstOrNull() ?: default`, `lastOrNull() ?: default` |
| `Flow.mapIndexed`, `filterIndexed`, `onEachIndexed` | `withIndex()` |
| `startWith` | `onStart { emit(value) }` |
| `retry`, `retryForever` | `retryWithBackoff` with `factor = 1.0`. For `retryForever`, pass `times = Int.MAX_VALUE`. |
| `formatDecimals`, `formatGrouped`, `formatPercent` | `"%.2f".format(x)`, `"%,d".format(n)`, `"%.0f%%".format(x * 100)` |
| `formatHms` | `Duration.toString()` |
| `nextAlphanumeric` and the other random string helpers | `Uuid.random()` for identifiers |
| `tryOrNull`, `tryOrDefault` | `runCatching { }.getOrNull()` and `getOrDefault(...)` |
| `Counter`, `frequencies`, `countBy` | `groupingBy { }.eachCount()` |

`format` rounds the way the JVM formatter does. For example,
`"%.2f".format(1.005)` gives `1.01`, where `1.005.formatDecimals(2)` gave `1.00`.

## [0.1.0] - 2026-07-24

### Added

- Pure-common JVM-style string formatting through both
  `String.format(format, vararg args)` and `formatString.format(vararg args)`,
  including indexed arguments, flags, width, precision, numeric conversions,
  and deterministic ROOT-like punctuation without `expect`/`actual`.
- `ioDispatcher`: one IO dispatcher on every target. Resolves to `Dispatchers.IO`
  on JVM, Android, iOS, and macOS (public on Kotlin/Native since coroutines 1.7).
  On the web targets it resolves to the dispatcher installed via
  `installIoDispatcher(...)`, falling back to `Dispatchers.Default` with a
  one-time console warning.
- `WebWorker` and `runInWorker` on `js` and `wasmJs`: inline Blob-URL Web
  Worker offload. Protocol v2 uses request/reply correlation ids. A reply to a
  cancelled call is dropped rather than delivered to the next caller. Worker
  script load failures fail immediately instead of hanging. `close()` fails an
  in-flight call with `WebWorkerException`. Results are normalized so an
  `undefined` job result round-trips as `"undefined"` on both targets. Blob
  object URLs are revoked after Worker construction.
- `Platform.current`: platform identity (`name`, `osVersion`, `deviceModel`,
  `family`) resolved per target. Device models come from `sysctlbyname` on
  Apple. OS fields are populated on every target.
- Targets: `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`,
  `macosArm64`, `js(browser, nodejs)`, `wasmJs(browser, nodejs)`.
  `macosX64` is absent (deprecated for removal in Kotlin 2.4).
- Explicit API mode and a committed ABI dump guard the public surface.
- `WeakRef<T>`: weak references across targets. Backed by
  `java.lang.ref.WeakReference` on JVM and Android,
  `kotlin.native.ref.WeakReference` on Apple, and the ES2021 `WeakRef` global on
  JS. JS primitives are not valid `WeakRef` targets and fall back to a strong
  hold. Wasm has no weak primitive and always uses a strong-hold fallback.
  `WeakRef.isWeakSupported` reports whether the target holds weakly.
- Extension packages: 505 public utilities in ten common-code packages under
  `io.github.yuroyami.kitecore.*`, each with tests on every target.
  `text` (case conversion, truncation, masking, safe parsing), `collections`
  (positional access, list surgery, grouping, statistics), `flow` (throttling,
  windowing, bounded-concurrency mapping, backoff retry, high-arity combine,
  derived StateFlow views), `coroutines` (retry, cancellation-safe runCatching,
  racing, parallel collection operations, KeyedMutex, SingleFlight, Debouncer,
  Throttler, suspendLazy), `math` (decimal rounding and locale-free formatting,
  byte-size formatting, interpolation, integer math, bit flags), `encoding`
  (hex, base64, CRC32, FNV-1a, endian-explicit binary reads and writes),
  `structures` (LruCache, TtlCache, RingBuffer, Counter, ObjectPool, TypedMap,
  memoize), `time` (Stopwatch, Deadline, RateLimiter, duration formatting,
  polling helpers), `random` (string generation, Gaussian sampling, weighted
  choice), and `util` (conditional scope functions, Result composition,
  multi-resource use, weakLazy).
- `ProgressFuture<T>`, `asyncWithProgress { }`, and `Progress`: progress-reporting
  futures in commonMain. `ProgressFuture` delegates to `Deferred` and adds a
  conflated `StateFlow<Progress>` carrying a fraction, a sticky phase label,
  and indeterminate support. `slice(weight)` carves sequential sub-windows for
  multi-stage jobs. `report(done, total)` covers counter-style parallel work.
  Success completes progress to 100%. Failure and cancellation freeze it.
  Throttling, monotonic bars, and ETA are left to `Flow` operators at the
  consumer.

### Removed

- iOS framework binaries are no longer produced by the default build. Maven
  consumers compile against klibs; an XCFramework distribution channel is a
  separate future feature.

### Known gaps

- Node runs skip the `WebWorker` tests: Node has no Web `Worker` global, and
  `worker_threads` is a different API (future feature, not a test shim).
- Android host tests run `commonTest` against JVM stub values of
  `android.os.Build` (fields read as null/0 off-device); device-true values are
  exercised on real Android only.
