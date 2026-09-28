/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

@file:OptIn(ExperimentalCoroutinesApi::class) // TestScope.currentTime

package io.github.yuroyami.kitecore.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class FlowsTest {

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
    fun invalidArgumentsFailBeforeCollection() {
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().throttleFirst(Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { emptyFlow<Int>().mapAsync(0) { it } }
    }
}
