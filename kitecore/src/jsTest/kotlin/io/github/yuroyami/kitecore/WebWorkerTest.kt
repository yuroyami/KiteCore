/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class WebWorkerTest {

    // jsTest runs on both browser (karma) and node. Node has worker_threads but
    // no Web `Worker` global, so the Blob-worker tests only exercise on browser.
    private fun hasWorker(): Boolean = js("typeof Worker !== 'undefined'") as Boolean

    @Test
    fun offload_roundtrip() = runTest {
        if (!hasWorker()) return@runTest
        val result = runInWorker("(n) => '' + (+n * 2)", "21")
        assertEquals("42", result)
    }

    @Test
    fun async_job_is_awaited() = runTest {
        if (!hasWorker()) return@runTest
        val result = runInWorker(
            "async (s) => { await new Promise(r => setTimeout(r, 10)); return s + '!' }",
            "done",
        )
        assertEquals("done!", result)
    }

    @Test
    fun job_error_propagates_with_message() = runTest {
        if (!hasWorker()) return@runTest
        try {
            runInWorker("() => { throw new Error('boom') }", "")
            fail("expected the worker error to propagate")
        } catch (e: WebWorkerException) {
            assertTrue(e.message?.contains("boom") == true, "message was: ${e.message}")
        }
    }

    @Test
    fun reusable_worker_serializes_concurrent_calls() = runTest {
        if (!hasWorker()) return@runTest
        val w = WebWorker.of("(n) => '' + (+n + 1)")
        try {
            // Overlapping call()s on one instance: the internal mutex must keep
            // request/response pairs from crossing.
            val results = (1..8).map { i -> async { w.call(i.toString()) } }.awaitAll()
            assertEquals((2..9).map { it.toString() }, results)
        } finally {
            w.close()
        }
    }

    @Test
    fun undefined_result_becomes_the_string_undefined() = runTest {
        if (!hasWorker()) return@runTest
        assertEquals("undefined", runInWorker("(p) => undefined", "x"))
    }

    @Test
    fun malformed_job_fails_instead_of_hanging() = runTest {
        if (!hasWorker()) return@runTest
        val w = WebWorker.of("this is not valid js at all (((")
        try {
            assertFailsWith<WebWorkerException> { w.call("x") }
        } finally {
            w.close()
        }
    }

    @Test
    fun close_fails_the_inflight_call() = runTest {
        if (!hasWorker()) return@runTest
        val w = WebWorker.of("(p) => new Promise(() => {})") // never resolves
        val inFlight = async(start = CoroutineStart.UNDISPATCHED) { runCatching { w.call("x") } }
        w.close()
        assertTrue(inFlight.await().exceptionOrNull() is WebWorkerException)
    }

    @Test
    fun cancelled_call_does_not_leak_its_result_into_the_next_call() = runTest {
        if (!hasWorker()) return@runTest
        // Job echoes its payload after a real 50ms pause.
        val w = WebWorker.of("async (p) => { await new Promise(r => setTimeout(r, 50)); return 'echo:' + p }")
        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { w.call("A") }
            first.cancel() // cancel while A is in flight in the worker
            val second = w.call("B")
            assertEquals("echo:B", second) // v1 protocol delivered "echo:A" here
        } finally {
            w.close()
        }
    }
}
