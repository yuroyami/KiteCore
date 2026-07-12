/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class FlowsTest {

    @Test
    fun withPreviousPairsEachValueWithItsPredecessor() = runTest {
        assertEquals(
            listOf(null to 1, 1 to 2, 2 to 3),
            flowOf(1, 2, 3).withPrevious().toList(),
        )
        assertEquals(emptyList(), emptyFlow<Int>().withPrevious().toList())
    }

    @Test
    fun pairwiseEmitsConsecutivePairs() = runTest {
        assertEquals(listOf(1 to 2, 2 to 3), flowOf(1, 2, 3).pairwise().toList())
        assertEquals(emptyList(), flowOf(1).pairwise().toList())
    }

    @Test
    fun pairwiseTransformCombinesNeighbours() = runTest {
        assertEquals(listOf(3, 5), flowOf(1, 2, 3).pairwise { a, b -> a + b }.toList())
    }

    @Test
    fun windowedSlidesByStep() = runTest {
        val source = flowOf(1, 2, 3, 4, 5)
        assertEquals(
            listOf(listOf(1, 2, 3), listOf(2, 3, 4), listOf(3, 4, 5)),
            source.windowed(3).toList(),
        )
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), source.windowed(2, step = 2).toList())
        assertEquals(listOf(listOf(1, 2), listOf(4, 5)), source.windowed(2, step = 3).toList())
    }

    @Test
    fun takeWhileInclusiveEmitsTheStoppingValue() = runTest {
        assertEquals(listOf(1, 2, 3), flowOf(1, 2, 3, 4).takeWhileInclusive { it < 3 }.toList())
        assertEquals(listOf(1, 2), flowOf(1, 2).takeWhileInclusive { it < 10 }.toList())
    }

    @Test
    fun takeWhileInclusiveCancelsInfiniteUpstream() = runTest {
        val naturals = flow {
            var i = 0
            while (true) emit(i++)
        }
        assertEquals(listOf(0, 1, 2), naturals.takeWhileInclusive { it < 2 }.toList())
    }

    @Test
    fun startWithPrependsValues() = runTest {
        assertEquals(listOf(0, 1, 2), flowOf(1, 2).startWith(0).toList())
        assertEquals(listOf(-1, 0, 1), flowOf(1).startWith(-1, 0).toList())
    }

    @Test
    fun concatWithAppendsFlowsInOrder() = runTest {
        assertEquals(listOf(1, 2), flowOf(1).concatWith(flowOf(2)).toList())
        assertEquals(listOf(1, 2, 3), flowOf(1).concatWith(flowOf(2), flowOf(3)).toList())
    }

    @Test
    fun concatWithSkipsOthersWhenUpstreamFails() = runTest {
        var otherStarted = false
        val other = flow {
            otherStarted = true
            emit(2)
        }
        val failing = flow {
            emit(1)
            throw IllegalStateException("boom")
        }
        assertFailsWith<IllegalStateException> { failing.concatWith(other).toList() }
        assertFalse(otherStarted)
    }

    @Test
    fun mapToUnitReplacesValues() = runTest {
        assertEquals(listOf(Unit, Unit), flowOf(1, 2).mapToUnit().toList())
    }

    @Test
    fun asResultWrapsValuesAndTerminalFailure() = runTest {
        val boom = IllegalStateException("boom")
        val results = flow {
            emit(1)
            emit(2)
            throw boom
        }.asResult().toList()
        assertEquals(listOf(Result.success(1), Result.success(2)), results.take(2))
        assertSame(boom, results[2].exceptionOrNull())
        assertEquals(3, results.size)
    }

    @Test
    fun asResultPassesCleanCompletion() = runTest {
        assertEquals(listOf(Result.success(1)), flowOf(1).asResult().toList())
    }

    @Test
    fun filterSuccessKeepsOnlySuccessValues() = runTest {
        val boom = IllegalStateException("boom")
        val source = flowOf(Result.success(1), Result.failure(boom), Result.success(2))
        assertEquals(listOf(1, 2), source.filterSuccess().toList())
    }

    @Test
    fun filterFailureKeepsOnlyExceptions() = runTest {
        val boom = IllegalStateException("boom")
        val source = flowOf(Result.success(1), Result.failure(boom))
        assertEquals(listOf<Throwable>(boom), source.filterFailure().toList())
    }

    @Test
    fun mapIndexedPassesPositions() = runTest {
        assertEquals(
            listOf("0:a", "1:b"),
            flowOf("a", "b").mapIndexed { index, value -> "$index:$value" }.toList(),
        )
    }

    @Test
    fun mapIndexedRestartsPerCollection() = runTest {
        val indexed = flowOf("a").mapIndexed { index, _ -> index }
        assertEquals(listOf(0), indexed.toList())
        assertEquals(listOf(0), indexed.toList())
    }

    @Test
    fun filterIndexedCountsAllValues() = runTest {
        assertEquals(
            listOf("a", "c"),
            flowOf("a", "b", "c").filterIndexed { index, _ -> index % 2 == 0 }.toList(),
        )
    }

    @Test
    fun onEachIndexedRunsActionAndKeepsValues() = runTest {
        val seen = mutableListOf<Pair<Int, String>>()
        val result = flowOf("a", "b").onEachIndexed { index, value -> seen.add(index to value) }.toList()
        assertEquals(listOf("a", "b"), result)
        assertEquals(listOf(0 to "a", 1 to "b"), seen)
    }

    @Test
    fun onFirstRunsOnceWithFirstValue() = runTest {
        val seen = mutableListOf<Int>()
        val result = flowOf(1, 2, 3).onFirst { seen.add(it) }.toList()
        assertEquals(listOf(1, 2, 3), result)
        assertEquals(listOf(1), seen)
    }

    @Test
    fun runningCountNumbersEmissions() = runTest {
        assertEquals(listOf(1, 2, 3), flowOf("a", "b", "c").runningCount().toList())
        assertEquals(emptyList(), emptyFlow<String>().runningCount().toList())
    }

    @Test
    fun intersperseInsertsSeparators() = runTest {
        assertEquals(listOf(1, 0, 2, 0, 3), flowOf(1, 2, 3).intersperse(0).toList())
        assertEquals(listOf(7), flowOf(7).intersperse(0).toList())
    }

    @Test
    fun flattenIterableEmitsEveryElement() = runTest {
        assertEquals(
            listOf(1, 2, 3),
            flowOf(listOf(1, 2), emptyList(), listOf(3)).flattenIterable().toList(),
        )
    }

    @Test
    fun firstOrDefaultReturnsFirstOrFallback() = runTest {
        assertEquals(1, flowOf(1, 2).firstOrDefault(9))
        assertEquals(9, emptyFlow<Int>().firstOrDefault(9))
        assertNull(flowOf<Int?>(null, 3).firstOrDefault(5))
    }

    @Test
    fun lastOrDefaultReturnsLastOrFallback() = runTest {
        assertEquals(3, flowOf(1, 2, 3).lastOrDefault(9))
        assertEquals(9, emptyFlow<Int>().lastOrDefault(9))
    }

    @Test
    fun flowFromSuspendRunsBlockPerCollection() = runTest {
        var calls = 0
        val source = flowFromSuspend { ++calls }
        assertEquals(listOf(1), source.toList())
        assertEquals(listOf(2), source.toList())
        assertEquals(2, calls)
    }

    @Test
    fun deferFlowResolvesProviderPerCollection() = runTest {
        var calls = 0
        val source = deferFlow {
            calls++
            flowOf(calls)
        }
        assertEquals(listOf(1), source.toList())
        assertEquals(listOf(2), source.toList())
    }

    @Test
    fun combine6CombinesLatestValues() = runTest {
        val result = combine6(
            flowOf(1), flowOf(2), flowOf(3), flowOf(4), flowOf(5), flowOf(6),
        ) { v1, v2, v3, v4, v5, v6 -> v1 + v2 + v3 + v4 + v5 + v6 }.toList()
        assertEquals(listOf(21), result)
    }

    @Test
    fun combine6ReadsLatestValueOnNewCollection() = runTest {
        val state = MutableStateFlow(10)
        val combined = combine6(
            state, flowOf(1), flowOf(1), flowOf(1), flowOf(1), flowOf(1),
        ) { v1, v2, v3, v4, v5, v6 -> v1 + v2 + v3 + v4 + v5 + v6 }
        assertEquals(15, combined.first())
        state.value = 20
        assertEquals(25, combined.first())
    }

    @Test
    fun combine7CombinesLatestValues() = runTest {
        val result = combine7(
            flowOf(1), flowOf(2), flowOf(3), flowOf(4), flowOf(5), flowOf(6), flowOf(7),
        ) { v1, v2, v3, v4, v5, v6, v7 -> "$v1$v2$v3$v4$v5$v6$v7" }.toList()
        assertEquals(listOf("1234567"), result)
    }

    @Test
    fun combine8CombinesLatestValues() = runTest {
        val result = combine8(
            flowOf(1), flowOf(2), flowOf(3), flowOf(4), flowOf(5), flowOf(6), flowOf(7), flowOf(8),
        ) { v1, v2, v3, v4, v5, v6, v7, v8 -> v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 }.toList()
        assertEquals(listOf(36), result)
    }

    @Test
    fun combine9CombinesLatestValues() = runTest {
        val result = combine9(
            flowOf(1), flowOf(2), flowOf(3), flowOf(4), flowOf(5),
            flowOf(6), flowOf(7), flowOf(8), flowOf(9),
        ) { v1, v2, v3, v4, v5, v6, v7, v8, v9 -> "$v1$v2$v3$v4$v5$v6$v7$v8$v9" }.toList()
        assertEquals(listOf("123456789"), result)
    }

    @Test
    fun invalidArgumentsFailBeforeCollection() {
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().windowed(size = 0) }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().throttleFirst(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().chunked(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().mapAsync(0) { it } }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().mapAsyncUnordered(0) { it } }
        assertFailsWith<IllegalArgumentException> {
            emptyFlow<Int>().retryWithBackoff(retries = -1, initialDelay = 1.seconds)
        }
        assertFailsWith<IllegalArgumentException> { intervalFlow(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { countdownFlow(-1, 1.seconds) }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().timeoutBetweenEmissions(Duration.ZERO) }
    }

    @Test
    fun chunkedGroupsBySizeAndEmitsTheTail() = runTest {
        val chunks = flowOf(1, 2, 3, 4, 5).chunked(2).toList()
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), chunks)
        assertFailsWith<IllegalArgumentException> { flowOf(1).chunked(0) }
    }
}
