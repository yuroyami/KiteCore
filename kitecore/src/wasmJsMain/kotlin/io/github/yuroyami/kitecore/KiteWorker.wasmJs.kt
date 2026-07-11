/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.yuroyami.kitecore

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thrown when a [KiteWorker] job throws, the worker script fails to load
 * (e.g. a syntax error in `jobJs`), or the worker is closed mid-call.
 */
public class KiteWorkerException(message: String) : RuntimeException(message)

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
 * Inline Web Worker offload primitive for Kotlin/Wasm (wasmJs) — API-identical
 * to the Kotlin/JS `KiteWorker`. Spins up a Worker from a Blob URL and runs a
 * self-contained JS job function off the single main thread.
 *
 * The job runs in the Worker scope with **no access to your Kotlin module**: pass
 * everything it needs through the `payload` String (JSON-encode for structure).
 * `jobJs` is a JS function expression `(payload) => result`, sync or async.
 *
 * Calls are safe to cancel: replies carry a correlation id, so a reply to a
 * cancelled call is dropped instead of being delivered to the next caller.
 * Wrap [call] in `withTimeout` for a deadline.
 */
public class KiteWorker private constructor(private val worker: JsAny) {

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
                    if (ok) p?.complete(body) else p?.completeExceptionally(KiteWorkerException(body))
                }
            },
            onError = { detail ->
                val msg = "KiteWorker error: $detail"
                loadError = msg
                val p = pending
                pending = null
                p?.completeExceptionally(KiteWorkerException(msg))
            },
        )
    }

    /**
     * Run the job with [payload], suspending until the worker replies. Safe to
     * call concurrently — calls are serialized on this worker instance — and
     * safe to cancel — a late reply to a cancelled call is discarded.
     *
     * @throws KiteWorkerException if the job throws, the worker script failed
     *   to load, or [close] is called while this call is in flight.
     * @throws IllegalStateException if the worker is already closed.
     */
    public suspend fun call(payload: String): String = callLock.withLock {
        check(!closed) { "KiteWorker is closed" }
        loadError?.let { throw KiteWorkerException(it) }
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
     * Terminate the underlying Worker. An in-flight [call] fails with
     * [KiteWorkerException]. Idempotent.
     */
    public fun close() {
        if (closed) return
        closed = true
        val p = pending
        pending = null
        p?.completeExceptionally(KiteWorkerException("KiteWorker closed while a call was in flight"))
        workerTerminate(worker)
    }

    public companion object {
        /** Build a reusable worker from a JS job expression `(payload) => result`. */
        public fun of(jobJs: String): KiteWorker = KiteWorker(newBlobWorker(bootstrap(jobJs)))

        // Protocol v2: request { id, payload } -> reply { id, ok, result | error },
        // result normalized with '' + so undefined round-trips as "undefined".
        // Keep in sync with the jsMain KiteWorker AND kmp-ssot's
        // generateIoWorkerSource (also protocol v2 since 2026-07-05); fix bugs
        // in all copies.
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

/** One-shot convenience: build a worker, run [payload] through [jobJs], close it. */
public suspend fun kiteOffload(jobJs: String, payload: String): String {
    val w = KiteWorker.of(jobJs)
    return try {
        w.call(payload)
    } finally {
        w.close()
    }
}
