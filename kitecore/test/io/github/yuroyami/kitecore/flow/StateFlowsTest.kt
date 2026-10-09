/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class StateFlowsTest {

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
}
