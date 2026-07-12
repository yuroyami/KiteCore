/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class) // TestScope.currentTime, advanceTimeBy, runCurrent

package io.github.yuroyami.kitecore.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

class FlowsTimeTest {

    @Test
    fun throttleFirstEmitsThenDropsWithinWindow() = runTest {
        val source = flow {
            emit(1)
            delay(100.milliseconds); emit(2)
            delay(100.milliseconds); emit(3)
            delay(100.milliseconds); emit(4)
            delay(100.milliseconds); emit(5)
        }
        assertEquals(listOf(1, 4), source.throttleFirst(250.milliseconds).toList())
    }

    @Test
    fun throttleFirstPassesSlowSourcesUnchanged() = runTest {
        val source = flow {
            emit(1)
            delay(300.milliseconds); emit(2)
        }
        assertEquals(listOf(1, 2), source.throttleFirst(100.milliseconds).toList())
    }

    @Test
    fun chunkedBatchesByWindow() = runTest {
        val source = flow {
            emit(1); emit(2)
            delay(100.milliseconds); emit(3)
            delay(300.milliseconds); emit(4); emit(5)
        }
        assertEquals(
            listOf(listOf(1, 2, 3), listOf(4, 5)),
            source.chunked(200.milliseconds).toList(),
        )
    }

    @Test
    fun chunkedRespectsMaxSize() = runTest {
        assertEquals(
            listOf(listOf(1, 2), listOf(3, 4), listOf(5)),
            flowOf(1, 2, 3, 4, 5).chunked(1.seconds, maxSize = 2).toList(),
        )
    }

    @Test
    fun timeoutBetweenEmissionsPassesFastFlows() = runTest {
        val source = flow {
            emit(1)
            delay(100.milliseconds); emit(2)
            delay(100.milliseconds); emit(3)
        }
        assertEquals(listOf(1, 2, 3), source.timeoutBetweenEmissions(200.milliseconds).toList())
    }

    @Test
    fun timeoutBetweenEmissionsFailsOnSlowGap() = runTest {
        val received = mutableListOf<Int>()
        val source = flow {
            emit(1)
            delay(500.milliseconds); emit(2)
        }
        assertFailsWith<TimeoutCancellationException> {
            source.timeoutBetweenEmissions(200.milliseconds).collect { received.add(it) }
        }
        assertEquals(listOf(1), received)
    }

    @Test
    fun intervalFlowEmitsSequentialNumbers() = runTest {
        val start = currentTime
        assertEquals(listOf(0L, 1L, 2L), intervalFlow(100.milliseconds).take(3).toList())
        assertEquals(200L, currentTime - start)
    }

    @Test
    fun intervalFlowRespectsInitialDelay() = runTest {
        val start = currentTime
        assertEquals(
            listOf(0L),
            intervalFlow(100.milliseconds, initialDelay = 50.milliseconds).take(1).toList(),
        )
        assertEquals(50L, currentTime - start)
    }

    @Test
    fun countdownFlowCountsDownToZero() = runTest {
        val start = currentTime
        assertEquals(listOf(3, 2, 1, 0), countdownFlow(3, 100.milliseconds).toList())
        assertEquals(300L, currentTime - start)
        assertEquals(listOf(0), countdownFlow(0, 100.milliseconds).toList())
    }

    @Test
    fun timerFlowEmitsUnitAfterDelay() = runTest {
        val start = currentTime
        assertEquals(listOf(Unit), timerFlow(500.milliseconds).toList())
        assertEquals(500L, currentTime - start)
    }

    @Test
    fun neverFlowNeverEmits() = runTest {
        var emissions = 0
        val finished = withTimeoutOrNull(1.hours) {
            neverFlow().collect { emissions++ }
        }
        assertNull(finished)
        assertEquals(0, emissions)
    }

    @Test
    fun takeUntilStopsWhenSignalFires() = runTest {
        val source = flow {
            emit(1)
            delay(100.milliseconds); emit(2)
            delay(200.milliseconds); emit(3)
        }
        assertEquals(listOf(1, 2), source.takeUntil(timerFlow(150.milliseconds)).toList())
    }

    @Test
    fun takeUntilIgnoresEmptySignal() = runTest {
        assertEquals(listOf(1, 2, 3), flowOf(1, 2, 3).takeUntil(emptyFlow<Unit>()).toList())
    }

    @Test
    fun dropUntilDropsBeforeSignal() = runTest {
        val source = flow {
            emit(1)
            delay(100.milliseconds); emit(2)
            delay(100.milliseconds); emit(3)
        }
        assertEquals(listOf(3), source.dropUntil(timerFlow(150.milliseconds)).toList())
    }

    @Test
    fun dropUntilWithEmptySignalDropsEverything() = runTest {
        assertEquals(emptyList(), flowOf(1, 2, 3).dropUntil(emptyFlow<Unit>()).toList())
    }

    @Test
    fun takeForStopsAfterDuration() = runTest {
        assertEquals(
            listOf(0L, 1L, 2L, 3L),
            intervalFlow(100.milliseconds).takeFor(350.milliseconds).toList(),
        )
    }

    @Test
    fun dropForSkipsTheInitialWindow() = runTest {
        assertEquals(
            listOf(3L, 4L),
            intervalFlow(100.milliseconds).dropFor(250.milliseconds).take(2).toList(),
        )
    }

    @Test
    fun withLatestFromSamplesOtherFlow() = runTest {
        val other = flow {
            delay(50.milliseconds); emit("a")
            delay(100.milliseconds); emit("b")
        }
        val source = flow {
            emit(1)
            delay(100.milliseconds); emit(2)
            delay(100.milliseconds); emit(3)
        }
        assertEquals(
            listOf("2a", "3b"),
            source.withLatestFrom(other) { value, latest -> "$value$latest" }.toList(),
        )
    }

    @Test
    fun withLatestFromPairsValues() = runTest {
        val other = flow { emit("x") }
        val source = flow {
            delay(50.milliseconds)
            emit(1)
        }
        assertEquals(listOf(1 to "x"), source.withLatestFrom(other).toList())
    }

    @Test
    fun mapAsyncPreservesOrderWhileRunningConcurrently() = runTest {
        val start = currentTime
        val result = flowOf(300, 100, 200)
            .mapAsync(concurrency = 3) { delay(it.milliseconds); it }
            .toList()
        assertEquals(listOf(300, 100, 200), result)
        assertEquals(300L, currentTime - start)
    }

    @Test
    fun mapAsyncLimitsConcurrency() = runTest {
        val start = currentTime
        val result = flowOf(100, 100, 100)
            .mapAsync(concurrency = 1) { delay(it.milliseconds); it }
            .toList()
        assertEquals(listOf(100, 100, 100), result)
        assertEquals(300L, currentTime - start)
    }

    @Test
    fun mapAsyncPropagatesTransformFailure() = runTest {
        assertFailsWith<IllegalStateException> {
            flowOf(1, 2)
                .mapAsync(concurrency = 2) { if (it == 2) throw IllegalStateException("boom") else it }
                .toList()
        }
    }

    @Test
    fun mapAsyncUnorderedEmitsInCompletionOrder() = runTest {
        val start = currentTime
        val result = flowOf(300, 100, 200)
            .mapAsyncUnordered(concurrency = 3) { delay(it.milliseconds); it }
            .toList()
        assertEquals(listOf(100, 200, 300), result)
        assertEquals(300L, currentTime - start)
    }

    @Test
    fun retryWithBackoffRetriesWithGrowingDelays() = runTest {
        val start = currentTime
        var attempts = 0
        val result = flow {
            attempts++
            if (attempts < 4) throw IllegalStateException("attempt $attempts")
            emit(attempts)
        }.retryWithBackoff(retries = 5, initialDelay = 100.milliseconds).toList()
        assertEquals(listOf(4), result)
        assertEquals(4, attempts)
        assertEquals(100L + 200L + 400L, currentTime - start)
    }

    @Test
    fun retryWithBackoffStopsWhenRetriesAreExhausted() = runTest {
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            flow<Int> {
                attempts++
                throw IllegalStateException("boom")
            }.retryWithBackoff(retries = 2, initialDelay = 10.milliseconds).toList()
        }
        assertEquals(3, attempts)
    }

    @Test
    fun retryWithBackoffRespectsRetryIf() = runTest {
        var attempts = 0
        assertFailsWith<IllegalArgumentException> {
            flow<Int> {
                attempts++
                throw IllegalArgumentException("no retry")
            }.retryWithBackoff(
                retries = 5,
                initialDelay = 10.milliseconds,
                retryIf = { it is IllegalStateException },
            ).toList()
        }
        assertEquals(1, attempts)
    }

    @Test
    fun retryWithBackoffCapsDelayAtMaxDelay() = runTest {
        val start = currentTime
        var attempts = 0
        flow {
            attempts++
            if (attempts < 4) throw IllegalStateException("attempt $attempts")
            emit(Unit)
        }.retryWithBackoff(
            retries = 3,
            initialDelay = 100.milliseconds,
            factor = 10.0,
            maxDelay = 300.milliseconds,
        ).toList()
        assertEquals(100L + 300L + 300L, currentTime - start)
    }

    @Test
    fun collectInLaunchesCollectionInScope() = runTest {
        val collected = mutableListOf<Long>()
        val job = intervalFlow(100.milliseconds).collectIn(this) { collected.add(it) }
        runCurrent()
        assertEquals(listOf(0L), collected)
        advanceTimeBy(250.milliseconds)
        assertEquals(listOf(0L, 1L, 2L), collected)
        job.cancel()
    }
}
