/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuroyami.kitecore.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SyncTest {

    @Test
    fun tryWithLockRunsWhenTheMutexIsFree() = runTest {
        val mutex = Mutex()
        assertEquals(1, mutex.tryWithLock { 1 })
        assertFalse(mutex.isLocked)
    }

    @Test
    fun tryWithLockReturnsNullWhenTheMutexIsHeld() = runTest {
        val mutex = Mutex()
        mutex.lock()
        var ran = false
        assertNull(mutex.tryWithLock { ran = true })
        assertFalse(ran)
        mutex.unlock()
    }

    @Test
    fun tryWithLockReleasesTheMutexWhenTheBlockThrows() = runTest {
        val mutex = Mutex()
        assertFailsWith<IllegalStateException> {
            mutex.tryWithLock { throw IllegalStateException("boom") }
        }
        assertFalse(mutex.isLocked)
    }

    @Test
    fun withLockOrNullAcquiresAFreeMutex() = runTest {
        val mutex = Mutex()
        assertEquals("value", mutex.withLockOrNull(10.milliseconds) { "value" })
        assertFalse(mutex.isLocked)
    }

    @Test
    fun withLockOrNullTimesOutOnAContendedMutex() = runTest {
        val mutex = Mutex()
        mutex.lock()
        var ran = false
        assertNull(mutex.withLockOrNull(50.milliseconds) { ran = true })
        assertFalse(ran)
        assertEquals(50, currentTime)
        mutex.unlock()
        assertEquals("late", mutex.withLockOrNull(50.milliseconds) { "late" })
    }

    @Test
    fun withLockOrNullUsesTryLockForZeroTimeout() = runTest {
        val mutex = Mutex()
        assertEquals(1, mutex.withLockOrNull(Duration.ZERO) { 1 })
        mutex.lock()
        assertNull(mutex.withLockOrNull(Duration.ZERO) { 2 })
        assertEquals(0, currentTime)
        mutex.unlock()
    }

    @Test
    fun tryWithPermitRunsWhenAPermitIsAvailable() = runTest {
        val semaphore = Semaphore(1)
        assertEquals(1, semaphore.tryWithPermit { 1 })
        assertEquals(1, semaphore.availablePermits)
    }

    @Test
    fun tryWithPermitReturnsNullWhenNoPermitIsAvailable() = runTest {
        val semaphore = Semaphore(1)
        semaphore.acquire()
        var ran = false
        assertNull(semaphore.tryWithPermit { ran = true })
        assertFalse(ran)
        semaphore.release()
    }

    @Test
    fun withPermitOrNullAcquiresAnAvailablePermit() = runTest {
        val semaphore = Semaphore(1)
        assertEquals("value", semaphore.withPermitOrNull(10.milliseconds) { "value" })
        // The permit was released, so a second call succeeds too.
        assertEquals("again", semaphore.withPermitOrNull(10.milliseconds) { "again" })
    }

    @Test
    fun withPermitOrNullTimesOutWhenPermitsAreExhausted() = runTest {
        val semaphore = Semaphore(1)
        semaphore.acquire()
        var ran = false
        assertNull(semaphore.withPermitOrNull(50.milliseconds) { ran = true })
        assertFalse(ran)
        assertEquals(50, currentTime)
        semaphore.release()
    }

    @Test
    fun withPermitOrNullUsesTryAcquireForZeroTimeout() = runTest {
        val semaphore = Semaphore(1)
        assertEquals(1, semaphore.withPermitOrNull(Duration.ZERO) { 1 })
        semaphore.acquire()
        assertNull(semaphore.withPermitOrNull(Duration.ZERO) { 2 })
        assertEquals(0, currentTime)
        semaphore.release()
    }

    @Test
    fun keyedMutexSerializesCallersOfTheSameKey() = runTest {
        val keyed = KeyedMutex<String>()
        var inside = 0
        var maxInside = 0
        val jobs = List(3) {
            launch {
                keyed.withLock("key") {
                    inside++
                    maxInside = maxOf(maxInside, inside)
                    delay(10)
                    inside--
                }
            }
        }
        jobs.joinAll()
        assertEquals(1, maxInside)
        assertEquals(0, keyed.activeKeyCount())
    }

    @Test
    fun keyedMutexAllowsDifferentKeysToRunConcurrently() = runTest {
        val keyed = KeyedMutex<String>()
        val start = currentTime
        listOf("a", "b").parallelForEach { key ->
            keyed.withLock(key) { delay(100) }
        }
        assertEquals(100, currentTime - start)
        assertEquals(0, keyed.activeKeyCount())
    }

    @Test
    fun keyedMutexReportsLockStatePerKey() = runTest {
        val keyed = KeyedMutex<String>()
        val holder = launch {
            keyed.withLock("held") { delay(100) }
        }
        runCurrent()
        assertTrue(keyed.isLocked("held"))
        assertFalse(keyed.isLocked("other"))
        assertEquals(1, keyed.activeKeyCount())
        advanceUntilIdle()
        holder.join()
        assertFalse(keyed.isLocked("held"))
        assertEquals(0, keyed.activeKeyCount())
    }

    @Test
    fun keyedMutexReleasesEntriesWhenAWaiterIsCancelled() = runTest {
        val keyed = KeyedMutex<String>()
        val holder = launch {
            keyed.withLock("key") { delay(100) }
        }
        runCurrent()
        val waiter = launch {
            keyed.withLock("key") { }
        }
        runCurrent()
        waiter.cancel()
        waiter.join()
        assertTrue(waiter.isCancelled)
        advanceUntilIdle()
        holder.join()
        assertEquals(0, keyed.activeKeyCount())
    }

    @Test
    fun keyedMutexWithLockOrNullTimesOutOnAHeldKey() = runTest {
        val keyed = KeyedMutex<String>()
        val holder = launch {
            keyed.withLock("key") { delay(200) }
        }
        runCurrent()
        var ran = false
        assertNull(keyed.withLockOrNull("key", 50.milliseconds) { ran = true })
        assertFalse(ran)
        assertEquals("free", keyed.withLockOrNull("other", 50.milliseconds) { "free" })
        advanceUntilIdle()
        holder.join()
        assertEquals("late", keyed.withLockOrNull("key", 50.milliseconds) { "late" })
        assertEquals(0, keyed.activeKeyCount())
    }

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
    fun debouncerRunsOnlyTheLastSubmissionInTheWindow() = runTest {
        val debouncer = Debouncer(100.milliseconds, backgroundScope)
        val executed = mutableListOf<Int>()
        debouncer.submit { executed += 1 }
        debouncer.submit { executed += 2 }
        debouncer.submit { executed += 3 }
        advanceTimeBy(150)
        runCurrent()
        assertEquals(listOf(3), executed)
        assertEquals(100.milliseconds, debouncer.window)
    }

    @Test
    fun debouncerRunsEachSubmissionSpacedBeyondTheWindow() = runTest {
        val debouncer = Debouncer(100.milliseconds, backgroundScope)
        val executed = mutableListOf<Int>()
        debouncer.submit { executed += 1 }
        advanceTimeBy(150)
        runCurrent()
        debouncer.submit { executed += 2 }
        advanceTimeBy(150)
        runCurrent()
        assertEquals(listOf(1, 2), executed)
    }

    @Test
    fun debouncerRestartsTheWindowOnEachSubmission() = runTest {
        val debouncer = Debouncer(100.milliseconds, backgroundScope)
        val executed = mutableListOf<Int>()
        debouncer.submit { executed += 1 }
        advanceTimeBy(60)
        runCurrent()
        debouncer.submit { executed += 2 }
        advanceTimeBy(60)
        runCurrent()
        // 120ms after the first submission, but the second one restarted the window.
        assertTrue(executed.isEmpty())
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(2), executed)
    }

    @Test
    fun debouncerCancelDropsThePendingSubmissionOnly() = runTest {
        val debouncer = Debouncer(100.milliseconds, backgroundScope)
        val executed = mutableListOf<Int>()
        debouncer.submit { executed += 1 }
        advanceTimeBy(50)
        runCurrent()
        debouncer.cancel()
        advanceTimeBy(200)
        runCurrent()
        assertTrue(executed.isEmpty())
        assertTrue(debouncer.isActive)
        // The debouncer keeps working after a cancel.
        debouncer.submit { executed += 2 }
        advanceTimeBy(150)
        runCurrent()
        assertEquals(listOf(2), executed)
    }

    @Test
    fun debouncerValidatesTheWindow() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Debouncer((-1).milliseconds, backgroundScope)
        }
    }

    @Test
    fun throttlerRunsOutsideTheWindowAndRejectsInsideIt() = runTest {
        val throttler = Throttler(100.milliseconds, testScheduler.timeSource)
        assertEquals(100.milliseconds, throttler.window)
        assertEquals(1, throttler.run { 1 })
        delay(50)
        var ran = false
        assertNull(throttler.run { ran = true; 2 })
        assertFalse(ran)
        delay(60)
        assertEquals(2, throttler.run { 2 })
    }

    @Test
    fun throttlerReportsAndClearsTheWindow() = runTest {
        val throttler = Throttler(100.milliseconds, testScheduler.timeSource)
        assertFalse(throttler.isThrottled)
        throttler.run { 1 }
        assertTrue(throttler.isThrottled)
        delay(150)
        assertFalse(throttler.isThrottled)
    }

    @Test
    fun throttlerResetPermitsTheNextRun() = runTest {
        val throttler = Throttler(100.milliseconds, testScheduler.timeSource)
        assertEquals(1, throttler.run { 1 })
        assertNull(throttler.run { 2 })
        throttler.reset()
        assertFalse(throttler.isThrottled)
        assertEquals(3, throttler.run { 3 })
    }

    @Test
    fun throttlerCountsAFailedRunAgainstTheWindow() = runTest {
        val throttler = Throttler(100.milliseconds, testScheduler.timeSource)
        assertFailsWith<IllegalStateException> {
            throttler.run<Unit> { throw IllegalStateException("boom") }
        }
        var ran = false
        assertNull(throttler.run { ran = true; 1 })
        assertFalse(ran)
    }

    @Test
    fun throttlerValidatesTheWindow() = runTest {
        assertFailsWith<IllegalArgumentException> { Throttler((-1).milliseconds) }
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
    fun keyedMutexIsExercisedByCancellationInsideTheBlock() = runTest {
        val keyed = KeyedMutex<String>()
        val cancelled = launch {
            keyed.withLock("key") { delay(1000) }
        }
        runCurrent()
        cancelled.cancel()
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        // The entry was cleaned up and the key is usable again.
        assertEquals(0, keyed.activeKeyCount())
        assertEquals("next", keyed.withLock("key") { "next" })
    }
}
