/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thrown when a [WebWorker] job throws, the worker script fails to load
 * (e.g. a syntax error in `jobJs`), or the worker is closed mid-call.
 */
public class WebWorkerException(message: String) : RuntimeException(message)

/**
 * A Web Worker that runs a self-contained JavaScript job off the main thread on
 * Kotlin/JS.
 *
 * The worker is created at runtime from a Blob URL rather than a separate script
 * file. The job runs in the Worker scope with no access to the Kotlin module.
 * Pass everything it needs through the `payload` String and JSON-encode it for
 * structure. `jobJs` is a JavaScript function expression `(payload) => result`,
 * synchronous or asynchronous.
 *
 * Each reply carries a correlation id. A reply to a cancelled call is dropped
 * instead of being delivered to the next caller, so [call] is safe to cancel.
 * Wrap [call] in `withTimeout` to apply a deadline.
 *
 * ```kotlin
 * suspend fun sumTo(n: String): String {
 *     val w = WebWorker.of("(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }")
 *     return try { w.call(n) } finally { w.close() }  // call() runs off the main thread
 * }
 * ```
 */
public class WebWorker private constructor(private val worker: dynamic) {

    // Serializes call(): the protocol is one request/reply in flight per worker.
    private val callLock = Mutex()

    private var nextId = 0
    private var pendingId = -1
    private var pending: CompletableDeferred<String>? = null
    private var loadError: String? = null
    private var closed = false

    init {
        // Handlers live for the worker's lifetime. Replies are matched by id;
        // anything stale (a reply to a cancelled call) is dropped.
        worker.onmessage = { event: dynamic ->
            val data = event.data
            if (data.id == pendingId) {
                val p = pending
                pending = null
                if (data.ok == true) {
                    p?.complete("" + data.result)
                } else {
                    p?.completeExceptionally(WebWorkerException("" + data.error))
                }
            }
            Unit
        }
        worker.onerror = { e: dynamic ->
            // Fires for script load/parse failures and uncaught worker errors.
            val detail: String =
                if (e != null && e.message != null) "" + e.message
                else "worker script failed to load or crashed"
            val msg = "WebWorker error: $detail"
            loadError = msg
            val p = pending
            pending = null
            p?.completeExceptionally(WebWorkerException(msg))
            Unit
        }
    }

    /**
     * Runs the job with [payload] and suspends until the worker replies.
     *
     * Calls on one worker instance are serialized, so this is safe to call
     * concurrently. A late reply to a cancelled call is discarded, so this is
     * safe to cancel.
     *
     * @throws WebWorkerException if the job throws, the worker script failed
     *   to load, or [close] is called while this call is in flight.
     * @throws IllegalStateException if the worker is already closed.
     */
    public suspend fun call(payload: String): String = callLock.withLock {
        check(!closed) { "WebWorker is closed" }
        loadError?.let { throw WebWorkerException(it) }
        val id = nextId++
        val deferred = CompletableDeferred<String>()
        pendingId = id
        pending = deferred
        val message: dynamic = js("({})")
        message.id = id
        message.payload = payload
        worker.postMessage(message)
        try {
            deferred.await()
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /**
     * Terminates the underlying Worker. An in-flight [call] fails with
     * [WebWorkerException]. Idempotent.
     */
    public fun close() {
        if (closed) return
        closed = true
        val p = pending
        pending = null
        p?.completeExceptionally(WebWorkerException("WebWorker closed while a call was in flight"))
        worker.terminate()
    }

    public companion object {
        /** Builds a reusable worker from a JavaScript job expression `(payload) => result`. */
        public fun of(jobJs: String): WebWorker = WebWorker(makeBlobWorker(bootstrap(jobJs)))

        // Protocol v2: request { id, payload } -> reply { id, ok, result | error },
        // result normalized with '' + so undefined round-trips as "undefined".
        // Keep in sync with the wasmJs WebWorker AND kmp-ssot's
        // generateIoWorkerSource (protocol v2); fix bugs in all copies.
        private fun bootstrap(jobJs: String): String =
            "self.onmessage = async function(e) {" +
                "  var id = e.data.id;" +
                "  try {" +
                "    var job = (" + jobJs + ");" +
                "    var result = await job(e.data.payload);" +
                "    self.postMessage({ id: id, ok: true, result: '' + result });" +
                "  } catch (err) {" +
                "    self.postMessage({ id: id, ok: false, error: String((err && err.stack) || err) });" +
                "  }" +
                "};"

        private fun makeBlobWorker(script: String): dynamic =
            js(
                "(function(s){" +
                    " var u = URL.createObjectURL(new Blob([s], { type: 'application/javascript' }));" +
                    " var w = new Worker(u);" +
                    " URL.revokeObjectURL(u);" +
                    " return w;" +
                    " })(script)"
            )
    }
}

/** Builds a worker, runs [payload] through [jobJs], and then closes the worker. */
public suspend fun runInWorker(jobJs: String, payload: String): String {
    val w = WebWorker.of(jobJs)
    return try {
        w.call(payload)
    } finally {
        w.close()
    }
}
