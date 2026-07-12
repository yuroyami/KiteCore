/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class StateFlowsTest {

    @Test
    fun toggleFlipsAndReturnsNewValue() = runTest {
        val state = MutableStateFlow(false)
        assertTrue(state.toggle())
        assertTrue(state.value)
        assertFalse(state.toggle())
        assertFalse(state.value)
    }

    @Test
    fun incrementAddsToIntState() = runTest {
        val state = MutableStateFlow(10)
        assertEquals(11, state.increment())
        assertEquals(14, state.increment(3))
        assertEquals(14, state.value)
    }

    @Test
    fun decrementSubtractsFromIntState() = runTest {
        val state = MutableStateFlow(10)
        assertEquals(9, state.decrement())
        assertEquals(4, state.decrement(5))
        assertEquals(4, state.value)
    }

    @Test
    fun incrementAddsToLongState() = runTest {
        val state = MutableStateFlow(1L)
        assertEquals(2L, state.increment())
        assertEquals(12L, state.increment(10L))
        assertEquals(12L, state.value)
    }

    @Test
    fun decrementSubtractsFromLongState() = runTest {
        val state = MutableStateFlow(5L)
        assertEquals(4L, state.decrement())
        assertEquals(0L, state.decrement(4L))
        assertEquals(0L, state.value)
    }

    @Test
    fun getAndSetReturnsPreviousValue() = runTest {
        val state = MutableStateFlow("old")
        assertEquals("old", state.getAndSet("new"))
        assertEquals("new", state.value)
    }

    @Test
    fun mapStateReflectsSourceValueAndEmissions() = runTest {
        val source = MutableStateFlow(2)
        val doubled = source.mapState { it * 2 }
        assertEquals(4, doubled.value)
        source.value = 5
        assertEquals(10, doubled.value)
        assertEquals(listOf(10), doubled.replayCache)
    }

    @Test
    fun combineStatesTwoThreeAndFourSources() = runTest {
        val a = MutableStateFlow(1)
        val b = MutableStateFlow(10)
        val c = MutableStateFlow(100)
        val d = MutableStateFlow(1000)
        assertEquals(11, combineStates(a, b) { x, y -> x + y }.value)
        assertEquals(111, combineStates(a, b, c) { x, y, z -> x + y + z }.value)
        assertEquals(1111, combineStates(a, b, c, d) { x, y, z, w -> x + y + z + w }.value)
        a.value = 2
        assertEquals(12, combineStates(a, b) { x, y -> x + y }.value)
    }

    @Test
    fun stateFlowOfHoldsAConstant() = runTest {
        val constant = stateFlowOf("fixed")
        assertEquals("fixed", constant.value)
        assertEquals(listOf("fixed"), constant.replayCache)
    }
}
