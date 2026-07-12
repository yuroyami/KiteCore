/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet

/**
 * Returns a read-only [StateFlow] view whose value is [transform] applied to this flow's value.
 *
 * The view is not backed by a coroutine: reading [StateFlow.value] applies [transform] to
 * the source's current value, and collection maps the source with equality conflation.
 * [transform] must be fast and pure; it runs on every value read and every source emission.
 */
public fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(value) }) { collector ->
        map(transform).distinctUntilChanged().collect(collector)
    }

/**
 * Returns a read-only [StateFlow] whose value combines [a] and [b] through [transform].
 *
 * The view is not backed by a coroutine: reading its value applies [transform] to the
 * sources' current values. [transform] must be fast and pure.
 */
public fun <A, B, R> combineStates(
    a: StateFlow<A>,
    b: StateFlow<B>,
    transform: (A, B) -> R,
): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(a.value, b.value) }) { collector ->
        combine(a, b, transform).distinctUntilChanged().collect(collector)
    }

/**
 * Returns a read-only [StateFlow] whose value combines [a], [b], and [c] through [transform].
 *
 * The view is not backed by a coroutine: reading its value applies [transform] to the
 * sources' current values. [transform] must be fast and pure.
 */
public fun <A, B, C, R> combineStates(
    a: StateFlow<A>,
    b: StateFlow<B>,
    c: StateFlow<C>,
    transform: (A, B, C) -> R,
): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(a.value, b.value, c.value) }) { collector ->
        combine(a, b, c, transform).distinctUntilChanged().collect(collector)
    }

/**
 * Returns a read-only [StateFlow] whose value combines four sources through [transform].
 *
 * The view is not backed by a coroutine: reading its value applies [transform] to the
 * sources' current values. [transform] must be fast and pure.
 */
public fun <A, B, C, D, R> combineStates(
    a: StateFlow<A>,
    b: StateFlow<B>,
    c: StateFlow<C>,
    d: StateFlow<D>,
    transform: (A, B, C, D) -> R,
): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(a.value, b.value, c.value, d.value) }) { collector ->
        combine(a, b, c, d, transform).distinctUntilChanged().collect(collector)
    }

/**
 * Returns a [StateFlow] that always holds [value].
 *
 * Collectors receive the value once and then suspend forever. Useful as a constant
 * input where an API expects a [StateFlow].
 */
public fun <T> stateFlowOf(value: T): StateFlow<T> =
    DerivedStateFlow(valueOf = { value }) { collector ->
        collector.emit(value)
    }

// StateFlow inheritance carries @SubclassOptInRequired since coroutines 1.9; the
// opt-in stays on this private class, matching the module's ProgressFuture precedent.
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedStateFlow<T>(
    private val valueOf: () -> T,
    private val collectImpl: suspend (FlowCollector<T>) -> Unit,
) : StateFlow<T> {
    override val value: T get() = valueOf()
    override val replayCache: List<T> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        collectImpl(collector)
        awaitCancellation()
    }
}

/**
 * Flips the boolean value and returns the new value.
 *
 * The update is atomic with respect to concurrent updates; the flip may be recomputed
 * under contention but is applied exactly once.
 */
public fun MutableStateFlow<Boolean>.toggle(): Boolean = updateAndGet { !it }

/**
 * Adds [by] to the value and returns the new value.
 *
 * The update is atomic with respect to concurrent updates. Pass a negative [by] to subtract.
 */
public fun MutableStateFlow<Int>.increment(by: Int = 1): Int = updateAndGet { it + by }

/**
 * Subtracts [by] from the value and returns the new value.
 *
 * The update is atomic with respect to concurrent updates. Pass a negative [by] to add.
 */
public fun MutableStateFlow<Int>.decrement(by: Int = 1): Int = updateAndGet { it - by }

/**
 * Adds [by] to the value and returns the new value.
 *
 * The update is atomic with respect to concurrent updates. Pass a negative [by] to subtract.
 */
public fun MutableStateFlow<Long>.increment(by: Long = 1L): Long = updateAndGet { it + by }

/**
 * Subtracts [by] from the value and returns the new value.
 *
 * The update is atomic with respect to concurrent updates. Pass a negative [by] to add.
 */
public fun MutableStateFlow<Long>.decrement(by: Long = 1L): Long = updateAndGet { it - by }

/**
 * Sets the value to [value] and returns the previous value.
 *
 * The swap is atomic with respect to concurrent updates. Collectors observe the new value
 * only if it differs from the previous one, following [MutableStateFlow] equality conflation.
 */
public fun <T> MutableStateFlow<T>.getAndSet(value: T): T = getAndUpdate { value }
