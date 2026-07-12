/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.yuroyami.kitecore

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thrown when a [WebWorker] job throws, the worker script fails to load
 * (e.g. a syntax error in `jobJs`), or the worker is closed mid-call.
 */
public class WebWorkerException(message: String) : RuntimeException(message)

/*
 * Wasm-side JS glue. Kotlin/Wasm has no `dynamic`, so the Worker object is held
 * as an opaque JsAny and every interaction goes through a js() shim.
 */

private fun newBlobWorker(script: String): JsAny =
    js(
        """(function(s){
        var u = URL.createObjectURL(new Blob([s], { type: 'application/javascript' }));
        var w = new Worker(u);
        URL.revokeObjectURL(u);
        return w;
        })(script)"""
    )

private fun installHandlers(
    worker: JsAny,
    onReply: (Int, Boolean, String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
    worker.onmessage = (e) => { onReply(e.data.id, !!e.data.ok, '' + (e.data.ok ? e.data.result : e.data.error)); };
    worker.onerror = (e) => { onError('' + ((e && e.message) || 'worker script failed to load or crashed')); };
    }"""
)

private fun postJob(worker: JsAny, id: Int, payload: String): Unit =
    js("worker.postMessage({ id: id, payload: payload })")

private fun workerTerminate(worker: JsAny): Unit = js("worker.terminate()")

/**
 * A Web Worker that runs a self-contained JavaScript job off the main thread on
 * Kotlin/Wasm (wasmJs).
 *
 * The API is identical to the Kotlin/JS `WebWorker`. The worker is created at
 * runtime from a Blob URL rather than a separate script file. The job runs in the
 * Worker scope with no access to the Kotlin module. Pass everything it needs
 * through the `payload` String and JSON-encode it for structure. `jobJs` is a
 * JavaScript function expression `(payload) => result`, synchronous or
 * asynchronous.
 *
 * Each reply carries a correlation id. A reply to a cancelled call is dropped
 * instead of being delivered to the next caller, so [call] is safe to cancel.
 * Wrap [call] in `withTimeout` to apply a deadline.
 */
public class WebWorker private constructor(private val worker: JsAny) {

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
        installHandlers(
            worker = worker,
            onReply = { id, ok, body ->
                if (id == pendingId) {
                    val p = pending
                    pending = null
                    if (ok) p?.complete(body) else p?.completeExceptionally(WebWorkerException(body))
                }
            },
            onError = { detail ->
                val msg = "WebWorker error: $detail"
                loadError = msg
                val p = pending
                pending = null
                p?.completeExceptionally(WebWorkerException(msg))
            },
        )
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
        postJob(worker, id, payload)
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
        workerTerminate(worker)
    }

    public companion object {
        /** Builds a reusable worker from a JavaScript job expression `(payload) => result`. */
        public fun of(jobJs: String): WebWorker = WebWorker(newBlobWorker(bootstrap(jobJs)))

        // Protocol v2: request { id, payload } -> reply { id, ok, result | error },
        // result normalized with '' + so undefined round-trips as "undefined".
        // Keep in sync with the jsMain WebWorker AND kmp-ssot's
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
