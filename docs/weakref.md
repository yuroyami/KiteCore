# Weak references

`WeakRef<T>` holds a referent without preventing garbage collection, behind one API across every KiteCore target. Where the platform has no weak primitive, it degrades to a strong hold and says so through `isWeakSupported`.

## Create one and read it back

Construct it with the referent; `get()` returns the referent, or `null` once it has been collected:

```kotlin
import io.github.yuroyami.kitecore.WeakRef

class ThumbnailHolder {
    private var cached: WeakRef<Thumbnail>? = null

    fun thumbnail(): Thumbnail {
        cached?.get()?.let { return it }
        val fresh = renderThumbnail()
        cached = WeakRef(fresh)
        return fresh
    }
}
```

Always handle the `null` branch. On weak platforms, collection can happen at any point after the last strong reference to the referent dies.

## Drop the referent early

`clear()` drops the reference immediately; subsequent `get()` calls return `null`. It is idempotent.

```kotlin
val weak = WeakRef(expensiveThing)
weak.clear()
check(weak.get() == null)
```

`clear()` matters most on the strong-hold platforms (Wasm, pre-ES2021 JS): there it is the only way to release the referent.

## Gate correctness on isWeakSupported

`WeakRef.isWeakSupported` reports whether the platform provides real weak semantics. When your design depends on collection actually happening, for example a memory-sensitive cache, branch on it:

```kotlin
import io.github.yuroyami.kitecore.WeakRef

val cache: DocumentCache =
    if (WeakRef.isWeakSupported) WeakDocumentCache()    // entries vanish under memory pressure
    else BoundedDocumentCache(maxEntries = 8)           // bounded by count instead
```

Without the gate, `WeakRef` still never crashes: on strong-hold platforms it behaves like a plain holder whose `get()` returns non-`null` until `clear()`. What you lose is the memory reclamation, so ungated code must not rely on entries disappearing.

## Per-target semantics

| Target | Backing | `isWeakSupported` |
|---|---|---|
| JVM, Android | `java.lang.ref.WeakReference` | `true` |
| iOS, macOS | `kotlin.native.ref.WeakReference` | `true` |
| JS on an ES2021+ runtime | the JS `WeakRef` global | `true` |
| JS on an older runtime | strong hold | `false` |
| wasmJs | strong hold | `false` |

Two JS details:

- Referents backed by JS primitives (`String`, boxed numbers) are not valid targets for the JS `WeakRef` global, so they are held strongly even when `isWeakSupported` is `true`. Wrap such values in your own class when weakness matters.
- `isWeakSupported` reflects the runtime and is detected once at load time.

On Kotlin/Wasm no weak primitive exists at all: the referent is always held strongly, `isWeakSupported` is `false`, and `get()` returns non-`null` until `clear()`.
