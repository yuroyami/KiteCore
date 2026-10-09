# Contributing to KiteCore

This guide covers building and contributing to KiteCore.

## Building

KiteCore builds with the [Kotlin Toolchain](https://kotlin-toolchain.org). The `./kotlin` script in the
repository root downloads the toolchain, the JDKs and the other tools on the first run.

| Task | Command |
| --- | --- |
| Build every target | `./kotlin build` |
| Run the tests | `./kotlin test` (one platform: `./kotlin test -p jvm`) |
| Run the tests, the API check and the version check | `./kotlin check` |
| Make the API reference in `build/dokka/html` | `./kotlin do dokka` |

- Android needs an Android SDK with accepted licenses. Set `ANDROID_HOME` to it, for example
  to the SDK of Android Studio.
- Apple targets build and run their tests only on macOS with Xcode.
- The Wasm tests run in a headless Chrome that the toolchain downloads.
- The toolchain cannot run JS tests yet. The JS target still compiles.
- `./kotlin check` runs every test twice: once for the debug build and once for the release build.

## Rules

- **Public API is guarded.** Explicit API mode is on, and the JVM ABI dump in
  `kitecore/api/jvm/kitecore.api` is committed. `./kotlin check` fails when the public API
  differs from the dump. If you change the public API on purpose, run `./kotlin do updateAbi`
  and commit the new dump in the same commit.
- Every public declaration carries KDoc. README examples must be real outputs
  of the code, not aspirational ones.
- Tests accompany behavior changes, especially on the web targets.

## Releasing

1. Set `settings.publishing.version` in `kitecore/module.yaml`.
2. Run `./kotlin do updateVersion`. It writes the same version into `KiteCore.VERSION`.
3. Run `./kotlin check`.
4. Commit the change and tag the commit as `v<version>`. Pushing the tag starts the Release
   workflow, which publishes to Maven Central.

To publish from your own machine instead, set these four environment variables and run
`./kotlin publish mavenCentral -m kitecore`:

- `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_USERNAME` and `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_PASSWORD`: a
  Maven Central Portal user token.
- `KOTLIN_TOOLCHAIN_SIGNING_KEY`: the ASCII-armored PGP private key.
- `KOTLIN_TOOLCHAIN_SIGNING_KEY_PASSPHRASE`: the passphrase of that key.

To try a build in another project first, run `./kotlin publish mavenLocal -m kitecore`. It
needs only the two signing variables.

## Commits

Small, focused commits with imperative subjects. Reference the issue that the
commit fixes, if there is one.
