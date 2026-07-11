/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A snapshot of an operation's progress: a [fraction] in `0..1` (or `null` while
 * indeterminate) and an optional human-readable [phase] label.
 *
 * Both live in one immutable value so a UI can never show a fraction from one
 * phase next to the label of another. Equality is by value: [StateFlow] conflates
 * on `equals`, so reporting the same progress twice wakes no collectors.
 *
 * The constructor never throws: an out-of-range fraction is clamped into `0..1`
 * and `NaN` becomes indeterminate — progress must never break the work it
 * measures.
 */
public class KiteProgress(fraction: Float?, public val phase: String? = null) {

    /** Completed fraction in `0..1`, or `null` while the total is unknown. */
    public val fraction: Float? = fraction?.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)

    override fun equals(other: Any?): Boolean =
        other is KiteProgress && other.fraction == fraction && other.phase == phase

    override fun hashCode(): Int = 31 * (fraction?.hashCode() ?: 0) + (phase?.hashCode() ?: 0)

    override fun toString(): String {
        val f = fraction?.let { "${(it * 100).toInt()}%" } ?: "indeterminate"
        return if (phase == null) "KiteProgress($f)" else "KiteProgress($f, $phase)"
    }
}

/**
 * A [Deferred] that additionally exposes live [progress]. Everything `Deferred`
 * offers works here — `await()`, `cancel()`, `awaitAll(...)`, `onAwait` — plus a
 * conflated [StateFlow] of the latest [KiteProgress].
 *
 * Create one with [kiteAsync], or adapt an existing pair with [asKiteFuture].
 * Observe cheaply from Compose via `progress.collectAsState()`. Progress policy
 * (throttling, monotonic bars) is one `Flow` operator away — e.g.
 * `progress.sample(100.milliseconds)` — and deliberately not built in.
 *
 * Intended granularity: one `KiteFuture` per user-visible operation (open a
 * document, download a file), not per inner step.
 */
// Deferred is @SubclassOptInRequired since coroutines 1.9: inheriting it is a
// deliberate, accepted risk (documented fallback: drop the supertype and expose
// the Deferred as a property if a future coroutines version seals Job).
@OptIn(InternalForInheritanceCoroutinesApi::class)
public interface KiteFuture<out T> : Deferred<T> {
    /** Latest progress. Starts indeterminate; set to `fraction = 1f` on success. */
    public val progress: StateFlow<KiteProgress>
}

/**
 * Receiver scope of a [kiteAsync] block: a [CoroutineScope] that can [report]
 * progress and carve the remaining bar into sequential [slice]s.
 *
 * [report] may be called from any thread. [slice] must be called sequentially
 * from the job's own flow (like a `sequence {}` builder) — slices are windows
 * allocated left to right, not a concurrent tree. For parallel work, funnel
 * completions through `report(done, total)` instead: a completion counter is
 * naturally monotonic.
 */
public interface KiteProgressScope : CoroutineScope {

    /**
     * Report progress: [fraction] in `0..1` within this scope's window, or `null`
     * for indeterminate. Never throws; out-of-range clamps. Prefer coarse units —
     * per page or per percent, not per byte (each distinct report allocates and,
     * when observed, wakes collectors).
     *
     * Phase labels are sticky: a non-null [phase] updates this scope's label,
     * and later reports keep it — a stage's label does not need re-passing on
     * every fraction tick. Phase updates should come from the job's own flow.
     */
    public fun report(fraction: Float?, phase: String? = null)

    /** Convenience: `report(done/total)`; indeterminate when `total <= 0`. */
    public fun report(done: Long, total: Long, phase: String? = null)

    /**
     * Allocate the next [weight] (fraction of this scope's window, `0..1`) to a
     * sub-scope; its `report(0..1)` maps into that window. Slices nest. A
     * non-null [phase] labels the slice and is reported immediately at the
     * slice's start; an unnamed slice inherits the current label. Weights beyond
     * the remaining window clamp; they should sum to at most 1.
     */
    public fun slice(weight: Float, phase: String? = null): KiteProgressScope
}

/**
 * [async], but the returned [KiteFuture] reports progress.
 *
 * ```
 * val opening: KiteFuture<PdfDocument> = scope.kiteAsync {
 *     val header = slice(0.05f, "header")
 *     val xref   = slice(0.25f, "xref")
 *     val pages  = slice(0.70f, "pages")
 *     header.report(1f)
 *     val table = parseXref(xref)           // xref.report(...) inside
 *     loadPages(table) { done, total -> pages.report(done, total) }
 * }
 * opening.progress.collect { ui.show(it) }  // or collectAsState() in Compose
 * val doc = opening.await()
 * ```
 *
 * On success, progress is automatically completed to `fraction = 1f` (keeping
 * the last phase). On failure or cancellation it freezes at the last reported
 * value.
 */
public fun <T> CoroutineScope.kiteAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend KiteProgressScope.() -> T,
): KiteFuture<T> {
    val state = MutableStateFlow(KiteProgress(null))
    val deferred = async(context, start) {
        SliceScope(this, state, base = 0f, width = 1f).block()
    }
    deferred.invokeOnCompletion { cause ->
        if (cause == null) state.update { KiteProgress(1f, it.phase) }
    }
    return KiteFutureImpl(deferred, state)
}

/**
 * Adapt an existing [Deferred] and an externally-managed progress flow into a
 * [KiteFuture] — for wrapping APIs that already expose both halves (e.g. a Ktor
 * download whose `onDownload` callback feeds a [MutableStateFlow]).
 */
public fun <T> Deferred<T>.asKiteFuture(progress: StateFlow<KiteProgress>): KiteFuture<T> =
    KiteFutureImpl(this, progress)

// Delegation must override Job/Deferred members that carry opt-in markers
// (attachChild etc. are @InternalCoroutinesApi; getCompleted is
// @ExperimentalCoroutinesApi). The opt-in stays on this private class — it is
// needed to *implement* Deferred, never to *use* KiteFuture.
@OptIn(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    InternalForInheritanceCoroutinesApi::class,
)
private class KiteFutureImpl<T>(
    deferred: Deferred<T>,
    override val progress: StateFlow<KiteProgress>,
) : KiteFuture<T>, Deferred<T> by deferred

/**
 * A window `[base, base + width]` of the overall bar. The root scope is the full
 * bar (`base = 0, width = 1`); every [slice] narrows it. `cursor` tracks how much
 * of this window is already handed out — mutated only by [slice], which the
 * contract confines to sequential calls, so no synchronization is needed.
 */
private class SliceScope(
    scope: CoroutineScope,
    private val state: MutableStateFlow<KiteProgress>,
    private val base: Float,
    private val width: Float,
    initialPhase: String? = null,
) : KiteProgressScope, CoroutineScope by scope {

    // Sticky label: report(phase != null) updates it, reports without a phase
    // keep it. Plain var — the contract confines phase changes to the job's own
    // flow (concurrent reports race only on which label sticks, never crash).
    private var currentPhase: String? = initialPhase

    private var cursor = 0f

    override fun report(fraction: Float?, phase: String?) {
        if (phase != null) currentPhase = phase
        // KiteProgress's constructor NaN-guards and clamps the mapped value.
        state.value = KiteProgress(fraction?.let { base + it.coerceIn(0f, 1f) * width }, currentPhase)
    }

    override fun report(done: Long, total: Long, phase: String?) {
        report(if (total > 0L) done.toFloat() / total.toFloat() else null, phase)
    }

    override fun slice(weight: Float, phase: String?): KiteProgressScope {
        // Float drift can leave the remainder microscopically negative.
        val available = (1f - cursor).coerceAtLeast(0f)
        val w = if (weight.isNaN()) 0f else weight.coerceIn(0f, available)
        val child = SliceScope(this, state, base + cursor * width, w * width, phase ?: currentPhase)
        cursor += w
        if (phase != null) child.report(0f)
        return child
    }
}
