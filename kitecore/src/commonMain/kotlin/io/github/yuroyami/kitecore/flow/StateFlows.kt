/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Like [map][kotlinx.coroutines.flow.map] for a [StateFlow], but the result is still a
 * `StateFlow`, and no `CoroutineScope` is needed.
 *
 * ### Example
 * ```kotlin
 * val items = MutableStateFlow(listOf(Item(price = 3), Item(price = 4)))
 * val total: StateFlow<Int> = items.mapState { it.sumOf { item -> item.price } }
 * total.value   // 7
 * ```
 *
 * ### Compared with `map` and `stateIn`
 * `map` returns a plain `Flow`, which has no `value`. `map(...).stateIn(scope, ...)`
 * needs a scope and starts a coroutine. `mapState` computes the value when you read
 * it, so it needs neither and is never out of date.
 *
 * ### Watch out
 * - [transform] runs on every read of `value` and on every new source value. Keep it
 *   fast, and free of side effects.
 * - Collectors get a new value only when the result changes by `equals`.
 *
 * @see combineStates
 */
public fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(value) }) { collector ->
        map(transform).distinctUntilChanged().collect(collector)
    }

/**
 * Like [combine][kotlinx.coroutines.flow.combine] for two [StateFlow]s, but the result is
 * a `StateFlow`, and no `CoroutineScope` is needed.
 *
 * ```kotlin
 * val canPay: StateFlow<Boolean> =
 *     combineStates(total, balance) { t, b -> b >= t }
 * ```
 *
 * The rules are the same as for [mapState]: [transform] runs on every read, so keep it
 * fast and free of side effects. There are overloads for three and four sources.
 */
public fun <A, B, R> combineStates(
    a: StateFlow<A>,
    b: StateFlow<B>,
    transform: (A, B) -> R,
): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(a.value, b.value) }) { collector ->
        combine(a, b, transform).distinctUntilChanged().collect(collector)
    }

/** The three-source form of [combineStates]. The same rules apply. */
public fun <A, B, C, R> combineStates(
    a: StateFlow<A>,
    b: StateFlow<B>,
    c: StateFlow<C>,
    transform: (A, B, C) -> R,
): StateFlow<R> =
    DerivedStateFlow(valueOf = { transform(a.value, b.value, c.value) }) { collector ->
        combine(a, b, c, transform).distinctUntilChanged().collect(collector)
    }

/** The four-source form of [combineStates]. The same rules apply. */
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
