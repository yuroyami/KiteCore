/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class) // TestScope.runCurrent

package io.github.yuroyami.kitecore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ProgressFutureTest {

    @Test
    fun reports_flow_through_and_success_autocompletes_to_one() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress {
            report(0.25f, "xref")
            gate.await()
            "done"
        }
        runCurrent()
        assertEquals(0.25f, f.progress.value.fraction)
        assertEquals("xref", f.progress.value.phase)
        gate.complete(Unit)
        assertEquals("done", f.await())
        assertEquals(1f, f.progress.value.fraction) // auto-completed
        assertEquals("xref", f.progress.value.phase) // phase preserved
    }

    @Test
    fun starts_indeterminate() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress { gate.await() }
        runCurrent()
        assertNull(f.progress.value.fraction)
        gate.complete(Unit)
        f.await()
    }

    @Test
    fun slices_map_into_their_window_and_advance_sequentially() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress {
            val header = slice(0.1f)
            header.report(1f) // -> 0.10
            val xref = slice(0.4f, "xref") // entry report -> 0.10, phase "xref"
            xref.report(0.5f) // 0.10 + 0.5 * 0.40 -> 0.30
            gate.await()
        }
        runCurrent()
        assertEquals(0.3f, f.progress.value.fraction!!, absoluteTolerance = 1e-4f)
        assertEquals("xref", f.progress.value.phase)
        gate.complete(Unit)
        f.await()
    }

    @Test
    fun nested_slices_compose() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress {
            val outer = slice(0.5f)
            val inner = outer.slice(0.5f) // window [0, 0.25] of the whole bar
            inner.report(0.5f) // -> 0.125
            gate.await()
        }
        runCurrent()
        assertEquals(0.125f, f.progress.value.fraction!!, absoluteTolerance = 1e-4f)
        gate.complete(Unit)
        f.await()
    }

    @Test
    fun oversized_and_nan_weights_clamp_instead_of_throwing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress {
            slice(0.8f)
            val second = slice(0.8f) // only 0.2 left -> clamped
            second.report(1f) // -> 1.0, not 1.6
            slice(Float.NaN).report(1f) // zero-width, no throw
            gate.await()
        }
        runCurrent()
        assertEquals(1f, f.progress.value.fraction!!, absoluteTolerance = 1e-4f)
        gate.complete(Unit)
        f.await()
    }

    @Test
    fun done_total_overload_handles_zero_total() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = asyncWithProgress {
            report(done = 5, total = 0) // unknown total -> indeterminate
            gate.await()
        }
        runCurrent()
        assertNull(f.progress.value.fraction)
        gate.complete(Unit)
        f.await()
    }

    @Test
    fun failure_freezes_progress_and_await_rethrows() = runTest {
        supervisorScope { // keep the failing child from cancelling the test scope
            val f = asyncWithProgress {
                report(0.4f, "mid")
                throw IllegalStateException("boom")
            }
            assertFailsWith<IllegalStateException> { f.await() }
            assertEquals(0.4f, f.progress.value.fraction) // no auto-1f on failure
            assertEquals("mid", f.progress.value.phase)
        }
    }

    @Test
    fun cancellation_freezes_progress() = runTest {
        val f = asyncWithProgress {
            report(0.6f)
            awaitCancellation()
        }
        runCurrent()
        f.cancel()
        assertFailsWith<CancellationException> { f.await() }
        assertEquals(0.6f, f.progress.value.fraction)
    }

    @Test
    fun equality_drives_stateflow_conflation() {
        // StateFlow dedupes on equals (documented contract); pin our equals.
        assertEquals(Progress(0.5f, "x"), Progress(0.5f, "x"))
        assertNotEquals(Progress(0.5f, "x"), Progress(0.5f, "y"))
        assertNotEquals(Progress(0.5f), Progress(0.6f))
        assertEquals(Progress(null), Progress(Float.NaN)) // NaN -> indeterminate
        assertEquals(0f, Progress(-0.5f).fraction) // clamps, never throws
        assertEquals(1f, Progress(1.5f).fraction)
    }

    @Test
    fun adapter_wraps_an_existing_deferred_and_flow() = runTest {
        val d = CompletableDeferred<String>()
        val p = MutableStateFlow(Progress(0.7f, "external"))
        val f = d.withProgress(p)
        assertEquals(0.7f, f.progress.value.fraction)
        d.complete("ok")
        assertEquals("ok", f.await())
        // The adapter does NOT auto-complete: the flow is externally owned.
        assertEquals(0.7f, f.progress.value.fraction)
    }
}
