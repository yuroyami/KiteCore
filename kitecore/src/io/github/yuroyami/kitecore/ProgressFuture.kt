/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One progress report: how far the operation is (`fraction`) and what it does now (`phase`).
 *
 * Both values live in one object, so a progress bar never shows the fraction of
 * one report with the label of another. You read it from [ProgressFuture.progress].
 * You construct it yourself only for [withProgress].
 *
 * ```kotlin
 * Progress(0.42f, "pages")   // toString(): "Progress(42%, pages)"
 * Progress(null)             // toString(): "Progress(indeterminate)"
 * ```
 *
 * The constructor never throws. A [fraction] outside `0..1` is clamped, and `NaN`
 * becomes `null`. Two reports with equal values are equal, so a [StateFlow] does
 * not wake its collectors for a repeated report.
 *
 * @param fraction the done part of the work, from `0f` to `1f`, not a percent. `null`
 *   means the total is not known yet (an indeterminate bar).
 * @property phase a short label for the current step, such as `"parsing"`, or `null`.
 */
public class Progress(fraction: Float?, public val phase: String? = null) {

    /** The done part of the work in `0..1`, or `null` while the total is not known. */
    public val fraction: Float? = fraction?.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)
        ?.let { if (it == 0f) 0f else it } // -0f becomes 0f, so equal values share a hash code

    override fun equals(other: Any?): Boolean =
        other is Progress && other.fraction == fraction && other.phase == phase

    override fun hashCode(): Int = 31 * (fraction?.hashCode() ?: 0) + (phase?.hashCode() ?: 0)

    override fun toString(): String {
        val f = fraction?.let { "${(it * 100).toInt()}%" } ?: "indeterminate"
        return if (phase == null) "Progress($f)" else "Progress($f, $phase)"
    }
}

/**
 * A running operation that you can await, plus its live `progress`.
 *
 * You get one from [asyncWithProgress], or from [withProgress] for an operation
 * that already has a progress flow. Use one per operation that the user sees,
 * such as "open document" or "download file", not one per inner step.
 *
 * ```kotlin
 * val opening = scope.asyncWithProgress { loadDocument() }
 * launch { opening.progress.collect { bar.show(it.fraction, it.phase) } }
 * val document = opening.await()
 * ```
 *
 * ### Compared with `Deferred`
 * A `ProgressFuture` is not a `Deferred`, because kotlinx-coroutines does not allow
 * other classes to implement `Deferred`. [await] and [cancel] are here. For
 * `awaitAll`, `select` or `invokeOnCompletion`, use [deferred].
 *
 * ### Watch out
 * - [progress] keeps only the latest value. A slow collector skips reports. To limit
 *   UI updates, use a `Flow` operator such as `progress.sample(100.milliseconds)`.
 */
public class ProgressFuture<out T> internal constructor(
    /** The running operation, for every `Deferred` API that this class does not forward. */
    public val deferred: Deferred<T>,
    /**
     * The latest [Progress]. In Compose, read it with `progress.collectAsState()`.
     *
     * From [asyncWithProgress], it starts indeterminate and goes to `1f` on success.
     * On failure or cancellation it keeps its last value, so read the outcome from
     * [await] or [deferred], not from the bar.
     */
    public val progress: StateFlow<Progress>,
) {
    /** Waits for the result, like [Deferred.await]. Throws the failure of the operation. */
    public suspend fun await(): T = deferred.await()

    /** Cancels the operation, like [Deferred.cancel]. [progress] keeps its last value. */
    public fun cancel(cause: CancellationException? = null) {
        deferred.cancel(cause)
    }

    override fun toString(): String = "ProgressFuture(${progress.value}, $deferred)"
}

/**
 * The receiver of an [asyncWithProgress] block: a [CoroutineScope] that can [report]
 * progress and split the bar into [slice]s.
 *
 * Each `ProgressScope` owns a window, which is a part of the whole bar. The block's
 * own scope owns the whole bar. A slice owns a part of its parent's window. A report
 * of `0.5f` in a slice fills half of that slice, not half of the bar.
 *
 * The phase is the label of the current step. It is sticky: a report without a
 * phase keeps the last phase.
 *
 * You cannot implement this interface. Only [asyncWithProgress] creates it.
 *
 * ### Threading
 * - [report] is safe from any thread, for example from parallel workers.
 * - Call [slice] and change the phase only from the block's own sequential code.
 *
 * ### Watch out
 * - Reports from parallel workers can arrive out of order, so the bar can move back
 *   a little. Count finished units in one shared counter and report it with
 *   `report(done, total)`. For a bar that never moves back, filter at the collector.
 * - After the operation completes, fails or is cancelled, reports do nothing.
 */
public sealed interface ProgressScope : CoroutineScope {

    /**
     * Reports how far this scope's window is done.
     *
     * Report in coarse steps, such as per page or per percent, not per byte. Each
     * new value allocates a [Progress] and wakes the collectors. This function never
     * throws.
     *
     * @param fraction the done part of this window, from `0f` to `1f`, not a percent.
     *   `null` means indeterminate. A value outside `0..1` is clamped.
     * @param phase a new label for the current step, or `null` to keep the current label.
     */
    public fun report(fraction: Float?, phase: String? = null)

    /**
     * Reports [done] of [total] units, for example `report(done = 3, total = 10)` for 30%.
     *
     * @param total the unit count. Zero or less means indeterminate, for a total
     *   that is not known yet.
     * @param phase a new label for the current step, or `null` to keep the current label.
     */
    public fun report(done: Long, total: Long, phase: String? = null)

    /**
     * Takes the next part of this window for one step and returns a scope for it.
     *
     * Slices are taken left to right. Three slices of `0.2f`, `0.3f` and `0.5f` cover
     * this window in that order. A slice can have its own slices.
     *
     * ```kotlin
     * val parse = slice(0.4f, "parsing")   // 0% to 40% of this window
     * parse.report(0.5f)                   // bar: 20%, "parsing"
     * val load = slice(0.6f, "loading")    // 40% to 100%. Bar: 40%, "loading"
     * ```
     *
     * **Watch out.** Take a named slice when its step starts, not all slices up front.
     * A named slice reports its start at once, so the bar would jump to the last slice
     * and then back.
     *
     * @param weight the part of this window, from `0f` to `1f`. The weights of all
     *   slices of one scope should add up to 1 or less. A weight larger than the
     *   remaining part is cut to fit.
     * @param phase the label of the step. A named slice reports `0f` with this label
     *   at once. With `null`, the slice keeps the current label.
     */
    public fun slice(weight: Float, phase: String? = null): ProgressScope
}

/**
 * Starts a coroutine like [async], and also publishes its progress as a [StateFlow].
 *
 * The [block] runs with a [ProgressScope] receiver. Call [ProgressScope.report] in it
 * to move the bar, and [ProgressScope.slice] to split the bar into weighted steps.
 * Read the bar from [ProgressFuture.progress] and the result from [ProgressFuture.await].
 *
 * ### Example
 * ```kotlin
 * val opening = scope.asyncWithProgress {
 *     val parse = slice(0.4f, "parsing")   // the first 40% of the bar
 *     val header = parseHeader { parse.report(it) }
 *     // The last 60%. Take a slice when its step starts.
 *     val load = slice(0.6f, "loading")
 *     loadPages(header) { done, total -> load.report(done, total) }
 * }
 * // Prints values such as "Progress(70%, loading)".
 * launch { opening.progress.collect { println(it) } }
 * val document = opening.await()
 * ```
 *
 * ### Lifecycle of the bar
 * | Moment | [ProgressFuture.progress] |
 * | --- | --- |
 * | Start | `Progress(null)`, indeterminate |
 * | Success | `1f`, with the last phase. You do not need a final `report(1f)`. |
 * | Failure or cancellation | the last value, frozen |
 *
 * @param context added to the context of the new coroutine, as in [async].
 * @param start when the coroutine starts, as in [async].
 * @param block the operation. Its return value is the result of [ProgressFuture.await].
 * @see ProgressFuture
 * @see ProgressScope
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
    return ProgressFuture(deferred, state.asStateFlow())
}

/**
 * Joins a [Deferred] and a progress flow that you already have into one [ProgressFuture].
 *
 * Use it for an API that reports progress through a callback, such as a Ktor
 * download with `onDownload`.
 *
 * ```kotlin
 * val state = MutableStateFlow(Progress(null, "downloading"))
 * val download = scope.async {
 *     client.get(url) {
 *         onDownload { sent, total ->
 *             val fraction = total?.let { sent.toFloat() / it }
 *             state.value = Progress(fraction, "downloading")
 *         }
 *     }.body<ByteArray>()
 * }.withProgress(state)
 * ```
 *
 * ### Watch out
 * - This function never changes [progress]. Unlike [asyncWithProgress], it does not
 *   set `1f` on success. Your own code sets every value.
 *
 * @param progress the flow that [ProgressFuture.progress] returns, unchanged.
 */
public fun <T> Deferred<T>.withProgress(progress: StateFlow<Progress>): ProgressFuture<T> =
    ProgressFuture(this, progress)

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

    // Reports stop once the job is no longer active: progress freezes on failure or
    // cancellation, and a late report cannot overwrite the final value.
    private val job: Job? = scope.coroutineContext[Job]

    // Sticky label: a report with a non-null phase updates it, reports without a
    // phase keep it. The contract confines phase changes to the job's own flow, so
    // concurrent reports race only on which label sticks and never crash.
    private var currentPhase: String? = initialPhase

    private var cursor = 0f

    override fun report(fraction: Float?, phase: String?) {
        if (phase != null) currentPhase = phase
        // Constructor clamps to 0..1 and maps NaN to indeterminate.
        val next = Progress(fraction?.let { base + it.coerceIn(0f, 1f) * width }, currentPhase)
        // The activity check runs inside the update loop, so a report that races the
        // completion handler either lands before it or is dropped.
        state.update { current -> if (job?.isActive == false) current else next }
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
