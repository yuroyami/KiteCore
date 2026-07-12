/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

/**
 * Runs [block] and then closes every resource in [resources] in reverse order.
 *
 * The multi-resource counterpart of [kotlin.use]. Reverse order means the
 * resource listed last, which is typically the one layered on top of the
 * others, is closed first. Every resource is closed exactly once regardless of
 * how many closes fail.
 *
 * Failure handling uses [Throwable.addSuppressed], which common Kotlin
 * provides on every target:
 * - When [block] throws, that exception is rethrown and every close failure is
 *   attached to it as a suppressed exception.
 * - When [block] succeeds but closing fails, the first close failure (in the
 *   reverse close order) is thrown after all remaining resources have been
 *   closed, with any later close failures attached to it as suppressed
 *   exceptions.
 *
 * Note that on platforms whose suppressed-exception support is a stdlib
 * emulation the suppressed list is still populated; no failure is ever
 * silently dropped.
 *
 * ```kotlin
 * useAll(source, buffer, cipher) { cipher.process(buffer.read(source)) }
 * ```
 *
 * @param resources the resources to close after [block] completes.
 * @param block the work to perform while all resources are open.
 * @return the value produced by [block].
 */
public inline fun <T> useAll(vararg resources: AutoCloseable, block: () -> T): T {
    var primary: Throwable? = null
    try {
        return block()
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        var closeFailure: Throwable? = null
        for (index in resources.indices.reversed()) {
            try {
                resources[index].close()
            } catch (failure: Throwable) {
                val sink = primary ?: closeFailure
                if (sink == null) closeFailure = failure else sink.addSuppressed(failure)
            }
        }
        if (primary == null && closeFailure != null) throw closeFailure
    }
}

/**
 * Runs [block] and then closes every resource in this collection in reverse iteration order.
 *
 * Behaves exactly like the vararg [useAll] overload, including its reverse
 * close order and its suppression rules; see that overload for the details.
 *
 * @param block the work to perform while all resources are open.
 * @return the value produced by [block].
 */
public inline fun <T> Iterable<AutoCloseable>.useAll(block: () -> T): T =
    useAll(*toList().toTypedArray(), block = block)

/**
 * Closes this resource, swallowing any [Exception] thrown by [AutoCloseable.close].
 *
 * Only [Exception] is swallowed: [Error]s and other non-Exception throwables
 * propagate to the caller. Intended for cleanup paths where a secondary close
 * failure must not mask the failure already being handled.
 *
 * ```kotlin
 * try { work(socket) } finally { socket.closeQuietly() }
 * ```
 */
public fun AutoCloseable.closeQuietly() {
    try {
        close()
    } catch (ignored: Exception) {
        // Swallowed by contract: close failures are irrelevant on this path.
    }
}
