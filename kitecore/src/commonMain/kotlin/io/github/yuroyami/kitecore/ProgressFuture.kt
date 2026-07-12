/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

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
 * An immutable snapshot of an operation's progress: a [fraction] and an optional
 * [phase] label.
 *
 * Holding both values in one snapshot lets a collector render a fraction and a
 * label that belong to the same report. Equality is by value. [StateFlow]
 * conflates on `equals`, so reporting an equal progress twice wakes no collectors.
 *
 * The constructor never throws. An out-of-range [fraction] is clamped into `0..1`,
 * and `NaN` becomes indeterminate.
 */
public class Progress(fraction: Float?, public val phase: String? = null) {

    /** Completed fraction in `0..1`, or `null` while the total is unknown. */
    public val fraction: Float? = fraction?.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)

    override fun equals(other: Any?): Boolean =
        other is Progress && other.fraction == fraction && other.phase == phase

    override fun hashCode(): Int = 31 * (fraction?.hashCode() ?: 0) + (phase?.hashCode() ?: 0)

    override fun toString(): String {
        val f = fraction?.let { "${(it * 100).toInt()}%" } ?: "indeterminate"
        return if (phase == null) "Progress($f)" else "Progress($f, $phase)"
    }
}

/**
 * A [Deferred] that also exposes live [progress].
 *
 * Every [Deferred] member works here: `await()`, `cancel()`, `awaitAll(...)`, and
 * `onAwait`. In addition, [progress] is a conflated [StateFlow] of the latest
 * [Progress], observable from Compose via `progress.collectAsState()`.
 *
 * Apply progress policy such as throttling or monotonic bars with a `Flow`
 * operator, for example `progress.sample(100.milliseconds)`.
 *
 * Create one with [asyncWithProgress], or adapt an existing pair with
 * [withProgress]. The intended granularity is one `ProgressFuture` per
 * user-visible operation such as opening a document or downloading a file, not
 * per inner step.
 */
// Deferred is @SubclassOptInRequired since coroutines 1.9; the @OptIn below
// opts into inheriting it.
@OptIn(InternalForInheritanceCoroutinesApi::class)
public interface ProgressFuture<out T> : Deferred<T> {
    /** Latest progress. Starts indeterminate; set to `fraction = 1f` on success. */
    public val progress: StateFlow<Progress>
}

/**
 * The receiver scope of an [asyncWithProgress] block: a [CoroutineScope] that can
 * [report] progress and divide the remaining bar into sequential [slice]s.
 *
 * [report] may be called from any thread. [slice] must be called sequentially
 * from the job's own flow; slices are windows allocated left to right, not a
 * concurrent tree. For parallel work, route completions through
 * `report(done, total)`, whose counter is monotonic.
 */
public interface ProgressScope : CoroutineScope {

    /**
     * Reports progress within this scope's window.
     *
     * [fraction] is a value in `0..1`, or `null` for indeterminate. This method
     * never throws; an out-of-range [fraction] is clamped. Prefer coarse units
     * such as per page or per percent rather than per byte, since each distinct
     * report allocates and, when observed, wakes collectors.
     *
     * Phase labels are sticky. A non-null [phase] updates this scope's label, and
     * later reports retain it, so a stage's label does not need re-passing on
     * every fraction tick. Phase updates should come from the job's own flow.
     */
    public fun report(fraction: Float?, phase: String? = null)

    /** Reports `done` of `total` completed units, or indeterminate when `total <= 0`. */
    public fun report(done: Long, total: Long, phase: String? = null)

    /**
     * Allocates the next [weight] of this scope's window to a sub-scope.
     *
     * [weight] is a fraction in `0..1` of this scope's window. The returned scope
     * maps its own `0..1` reports into that window, and its slices nest further. A
     * non-null [phase] labels the slice and is reported immediately at its start;
     * an unnamed slice inherits the current label. A [weight] beyond the remaining
     * window is clamped, and weights should sum to at most 1.
     */
    public fun slice(weight: Float, phase: String? = null): ProgressScope
}

/**
 * Starts a coroutine like [async] and returns a [ProgressFuture] that reports its
 * progress.
 *
 * The [block] runs with a [ProgressScope] receiver, so it can report progress
 * and slice the bar. On success, progress is completed to `fraction = 1f`, keeping
 * the last phase. On failure or cancellation, progress freezes at the last
 * reported value. [context] and [start] behave as in [async].
 *
 * ```kotlin
 * val opening: ProgressFuture<PdfDocument> = scope.asyncWithProgress {
 *     val header = slice(0.05f, "header")
 *     val xref   = slice(0.25f, "xref")
 *     val pages  = slice(0.70f, "pages")
 *     header.report(1f)
 *     val table = parseXref(xref)
 *     loadPages(table) { done, total -> pages.report(done, total) }
 * }
 * opening.progress.collect { ui.show(it) }  // or collectAsState() in Compose
 * val doc = opening.await()
 * ```
 */
public fun <T> CoroutineScope.asyncWithProgress(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend ProgressScope.() -> T,
): ProgressFuture<T> {
    val state = MutableStateFlow(Progress(null))
    val deferred = async(context, start) {
        SliceScope(this, state, base = 0f, width = 1f).block()
    }
    deferred.invokeOnCompletion { cause ->
        if (cause == null) state.update { Progress(1f, it.phase) }
    }
    return ProgressFutureImpl(deferred, state)
}

/**
 * Adapts an existing [Deferred] and an external progress [StateFlow] into a
 * [ProgressFuture].
 *
 * Use it to wrap an API that already exposes both halves, such as a Ktor download
 * whose `onDownload` callback feeds a [MutableStateFlow].
 */
public fun <T> Deferred<T>.withProgress(progress: StateFlow<Progress>): ProgressFuture<T> =
    ProgressFutureImpl(this, progress)

// Delegating to Deferred surfaces members that carry opt-in markers: attachChild
// and friends are @InternalCoroutinesApi, and getCompleted is
// @ExperimentalCoroutinesApi. The @OptIn stays on this private class.
@OptIn(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    InternalForInheritanceCoroutinesApi::class,
)
private class ProgressFutureImpl<T>(
    deferred: Deferred<T>,
    override val progress: StateFlow<Progress>,
) : ProgressFuture<T>, Deferred<T> by deferred

/**
 * A window `[base, base + width]` of the overall bar. The root scope is the full
 * bar (`base = 0, width = 1`), and every [slice] narrows it. `cursor` tracks how
 * much of this window is already allocated. It is mutated only by [slice], which
 * the contract confines to sequential calls, so no synchronization is needed.
 */
private class SliceScope(
    scope: CoroutineScope,
    private val state: MutableStateFlow<Progress>,
    private val base: Float,
    private val width: Float,
    initialPhase: String? = null,
) : ProgressScope, CoroutineScope by scope {

    // Sticky label: a report with a non-null phase updates it, reports without a
    // phase keep it. The contract confines phase changes to the job's own flow, so
    // concurrent reports race only on which label sticks and never crash.
    private var currentPhase: String? = initialPhase

    private var cursor = 0f

    override fun report(fraction: Float?, phase: String?) {
        if (phase != null) currentPhase = phase
        // Constructor clamps to 0..1 and maps NaN to indeterminate.
        state.value = Progress(fraction?.let { base + it.coerceIn(0f, 1f) * width }, currentPhase)
    }

    override fun report(done: Long, total: Long, phase: String?) {
        report(if (total > 0L) done.toFloat() / total.toFloat() else null, phase)
    }

    override fun slice(weight: Float, phase: String?): ProgressScope {
        // Float drift can leave the remainder microscopically negative.
        val available = (1f - cursor).coerceAtLeast(0f)
        val w = if (weight.isNaN()) 0f else weight.coerceIn(0f, available)
        val child = SliceScope(this, state, base + cursor * width, w * width, phase ?: currentPhase)
        cursor += w
        if (phase != null) child.report(0f)
        return child
    }
}
