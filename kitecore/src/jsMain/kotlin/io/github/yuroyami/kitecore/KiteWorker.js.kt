/* Copyright 2026 yuroyami — Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CompletableDeferred

/**
 * Inline Web Worker offload primitive for Kotlin/JS.
 *
 * Spins up a Worker from a Blob URL — no separate bundle or webpack config — and
 * runs a self-contained JS job function off the single main thread. This is the
 * "inception" pattern (build a worker script at runtime, inject it, message it)
 * packaged once so projects stop hand-rolling it.
 *
 * The job runs in the Worker scope with **no access to your Kotlin module**: pass
 * everything it needs through the `payload` String (JSON-encode for structure).
 * `jobJs` is a JS function expression `(payload) => result`, sync or async.
 *
 * ```
 * val w = KiteWorker.of("(n) => { let s = 0; for (let i = 0; i < +n; i++) s += i; return '' + s }")
 * val sum = w.call("100000000")   // runs off the main thread
 * w.close()
 * ```
 *
 * For the zero-dependency, build-time-generated variant see kmp-ssot's
 * `web { generateIoWorker = true }`.
 */
class KiteWorker private constructor(private val worker: dynamic) {

    /** Run the job with [payload], suspending until the worker replies. */
    suspend fun call(payload: String): String {
        val deferred = CompletableDeferred<String>()
        worker.onmessage = { event: dynamic ->
            val data = event.data
            if (data.ok == true) {
                deferred.complete(data.result.toString())
            } else {
                deferred.completeExceptionally(RuntimeException(data.error.toString()))
            }
            Unit
        }
        worker.onerror = { e: dynamic ->
            deferred.completeExceptionally(RuntimeException("KiteWorker error: " + e))
            Unit
        }
        worker.postMessage(payload)
        return deferred.await()
    }

    /** Terminate the underlying Worker. */
    fun close() {
        worker.terminate()
    }

    companion object {
        /** Build a reusable worker from a JS job expression `(payload) => result`. */
        fun of(jobJs: String): KiteWorker = KiteWorker(makeBlobWorker(bootstrap(jobJs)))

        private fun bootstrap(jobJs: String): String =
            "self.onmessage = async function(e) {" +
                "  try {" +
                "    var job = (" + jobJs + ");" +
                "    var result = await job(e.data);" +
                "    self.postMessage({ ok: true, result: result });" +
                "  } catch (err) {" +
                "    self.postMessage({ ok: false, error: String((err && err.stack) || err) });" +
                "  }" +
                "};"

        private fun makeBlobWorker(script: String): dynamic =
            js("new Worker(URL.createObjectURL(new Blob([script], { type: 'application/javascript' })))")
    }
}

/** One-shot convenience: build a worker, run [payload] through [jobJs], close it. */
suspend fun kiteOffload(jobJs: String, payload: String): String {
    val w = KiteWorker.of(jobJs)
    return try {
        w.call(payload)
    } finally {
        w.close()
    }
}
