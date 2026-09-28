/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class SyncTest {

    @Test
    fun singleFlightSharesOneExecutionAcrossConcurrentCallers() = runTest {
        val flight = SingleFlight<String, Int>()
        var executions = 0
        val first = async {
            flight.run("key") {
                executions++
                delay(50)
                42
            }
        }
        val second = async {
            flight.run("key") {
                executions++
                delay(50)
                43
            }
        }
        assertEquals(42, first.await())
        assertEquals(42, second.await())
        assertEquals(1, executions)
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun singleFlightSharesTheFailureAcrossConcurrentCallers() = runTest {
        supervisorScope {
            val flight = SingleFlight<String, Int>()
            var executions = 0
            val first = async {
                flight.run("key") {
                    executions++
                    delay(50)
                    throw IllegalStateException("boom")
                }
            }
            val second = async {
                flight.run("key") {
                    executions++
                    7
                }
            }
            val firstFailure = assertFailsWith<IllegalStateException> { first.await() }
            val secondFailure = assertFailsWith<IllegalStateException> { second.await() }
            // Identity is not asserted: JVM stack trace recovery may rethrow copies.
            assertEquals("boom", firstFailure.message)
            assertEquals("boom", secondFailure.message)
            assertEquals(1, executions)
            assertEquals(0, flight.inFlightKeyCount())
        }
    }

    @Test
    fun singleFlightRunsAgainAfterCompletion() = runTest {
        val flight = SingleFlight<String, Int>()
        var executions = 0
        assertEquals(1, flight.run("key") { executions++; 1 })
        assertEquals(2, flight.run("key") { executions++; 2 })
        assertEquals(2, executions)
    }

    @Test
    fun singleFlightReportsInFlightState() = runTest {
        val flight = SingleFlight<String, Int>()
        val runner = launch {
            flight.run("key") {
                delay(100)
                1
            }
        }
        runCurrent()
        assertTrue(flight.isInFlight("key"))
        assertFalse(flight.isInFlight("other"))
        assertEquals(1, flight.inFlightKeyCount())
        advanceUntilIdle()
        runner.join()
        assertFalse(flight.isInFlight("key"))
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun singleFlightWaiterSurvivesACancelledExecutorAndRetries() = runTest {
        val flight = SingleFlight<String, Int>()
        var executions = 0
        val executor = launch {
            flight.run("key") {
                executions++
                delay(100)
                1
            }
        }
        runCurrent()
        val waiter = async {
            flight.run("key") {
                executions++
                delay(10)
                2
            }
        }
        runCurrent()
        executor.cancel()
        assertEquals(2, waiter.await())
        assertTrue(executor.isCancelled)
        assertEquals(2, executions)
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun suspendLazyInitializesOnceAcrossConcurrentCallers() = runTest {
        var initializations = 0
        val lazyValue = suspendLazy {
            initializations++
            delay(100)
            "ready"
        }
        assertFalse(lazyValue.isInitialized)
        assertNull(lazyValue.valueOrNull())
        val first = async { lazyValue.get() }
        val second = async { lazyValue.get() }
        assertEquals("ready", first.await())
        assertEquals("ready", second.await())
        assertEquals(1, initializations)
        assertTrue(lazyValue.isInitialized)
        assertEquals("ready", lazyValue.valueOrNull())
    }

    @Test
    fun suspendLazyRetriesAfterAFailedInitialization() = runTest {
        var calls = 0
        val lazyValue = suspendLazy {
            calls++
            if (calls == 1) throw IllegalStateException("first fails")
            "ok"
        }
        val failure = assertFailsWith<IllegalStateException> { lazyValue.get() }
        assertEquals("first fails", failure.message)
        assertFalse(lazyValue.isInitialized)
        assertEquals("ok", lazyValue.get())
        assertTrue(lazyValue.isInitialized)
        assertEquals(2, calls)
    }

    @Test
    fun suspendLazyRetriesAfterACancelledInitialization() = runTest {
        var starts = 0
        val lazyValue = suspendLazy {
            starts++
            delay(100)
            42
        }
        val initializer = launch { lazyValue.get() }
        runCurrent()
        initializer.cancel()
        initializer.join()
        assertTrue(initializer.isCancelled)
        assertFalse(lazyValue.isInitialized)
        assertEquals(42, lazyValue.get())
        assertTrue(lazyValue.isInitialized)
        assertEquals(2, starts)
    }

    @Test
    fun suspendLazyFailureReachesOnlyTheInitializingCallerOnce() = runTest {
        var calls = 0
        val lazyValue = suspendLazy {
            calls++
            delay(50)
            if (calls == 1) throw IllegalStateException("boom") else calls
        }
        // Two concurrent callers: the initializer fails for the first while
        // the second retries the initialization itself.
        val outcome = supervisorScope {
            val first = async { lazyValue.get() }
            val second = async { lazyValue.get() }
            assertFailsWith<IllegalStateException> { first.await() }
            second.await()
        }
        assertEquals(2, outcome)
        assertEquals(2, calls)
        assertTrue(lazyValue.isInitialized)
    }

    @Test
    fun singleFlightSharesATimeoutInsideTheBlockInsteadOfRunningAgain() = runTest {
        val flight = SingleFlight<String, Int>()
        var executions = 0
        val gate = CompletableDeferred<Unit>()
        val owner = async {
            runCatching {
                flight.run("key") {
                    executions++
                    gate.await()
                    withTimeout(1.milliseconds) { awaitCancellation() }
                }
            }
        }
        runCurrent()
        val joiner = async {
            runCatching {
                flight.run("key") {
                    executions++
                    42
                }
            }
        }
        runCurrent()
        gate.complete(Unit)
        assertIs<TimeoutCancellationException>(owner.await().exceptionOrNull())
        assertIs<TimeoutCancellationException>(joiner.await().exceptionOrNull())
        assertEquals(1, executions)
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun singleFlightRejectsAReentrantCallForTheSameKey() = runTest {
        val flight = SingleFlight<String, Int>()
        val failure = assertFailsWith<IllegalStateException> {
            withTimeout(5.seconds) {
                flight.run("key") { flight.run("key") { 1 } }
            }
        }
        assertFalse(failure is CancellationException, "expected a fast failure, got $failure")
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun singleFlightRejectsAReentrantCallFromAChildCoroutine() = runTest {
        val flight = SingleFlight<String, Int>()
        val failure = assertFailsWith<IllegalStateException> {
            withTimeout(5.seconds) {
                flight.run("key") {
                    coroutineScope { async { flight.run("key") { 1 } }.await() }
                }
            }
        }
        assertFalse(failure is CancellationException, "expected a fast failure, got $failure")
    }

    @Test
    fun singleFlightAllowsOtherKeysFromInsideABlock() = runTest {
        val flight = SingleFlight<String, Int>()
        val value = flight.run("outer") { flight.run("inner") { 2 } + 1 }
        assertEquals(3, value)
    }

    @Test
    fun singleFlightHoldsUpUnderRealThreads() = runTest {
        val flight = SingleFlight<Int, Int>()
        val executions = MutableStateFlow(0)
        val results = withContext(Dispatchers.Default) {
            (0 until 400).map { caller ->
                async {
                    val key = caller % 8
                    flight.run(key) {
                        executions.update { it + 1 }
                        yield()
                        key * 10
                    }
                }
            }.awaitAll()
        }
        assertEquals((0 until 400).map { (it % 8) * 10 }, results)
        assertTrue(executions.value in 8..400, "executions = ${executions.value}")
        assertEquals(0, flight.inFlightKeyCount())
    }

    @Test
    fun suspendLazyRejectsAReentrantGet() = runTest {
        lateinit var lazyValue: SuspendLazy<Int>
        lazyValue = suspendLazy { lazyValue.get() + 1 }
        val failure = assertFailsWith<IllegalStateException> {
            withTimeout(5.seconds) { lazyValue.get() }
        }
        assertFalse(failure is CancellationException, "expected a fast failure, got $failure")
        assertFalse(lazyValue.isInitialized)
    }

    @Test
    fun suspendLazyRejectsAReentrantGetFromAChildCoroutine() = runTest {
        lateinit var lazyValue: SuspendLazy<Int>
        lazyValue = suspendLazy {
            coroutineScope { async { lazyValue.get() }.await() } + 1
        }
        val failure = assertFailsWith<IllegalStateException> {
            withTimeout(5.seconds) { lazyValue.get() }
        }
        assertFalse(failure is CancellationException, "expected a fast failure, got $failure")
    }

    @Test
    fun suspendLazyInitializesOnceUnderRealThreads() = runTest {
        val initializations = MutableStateFlow(0)
        val lazyValue = suspendLazy {
            initializations.update { it + 1 }
            yield()
            "value"
        }
        val values = withContext(Dispatchers.Default) {
            (0 until 200).map { async { lazyValue.get() } }.awaitAll()
        }
        assertTrue(values.all { it == "value" })
        assertEquals(1, initializations.value)
    }
}
