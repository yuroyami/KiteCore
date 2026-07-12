/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SingletonHolderTest {

    private class Service private constructor(val label: String) {
        companion object : SingletonHolder<Service, String>(::Service)
    }

    @Test
    fun getInstanceCreatesOnFirstCallWithTheGivenArgument() {
        val holder = SingletonHolder<StringBuilder, String> { StringBuilder(it) }
        val instance = holder.getInstance("seed")
        assertEquals("seed", instance.toString())
    }

    @Test
    fun laterCallsReturnTheSameInstanceAndIgnoreTheArgument() {
        val holder = SingletonHolder<StringBuilder, String> { StringBuilder(it) }
        val first = holder.getInstance("first")
        val second = holder.getInstance("second")
        assertSame(first, second)
        assertEquals("first", second.toString())
    }

    @Test
    fun creatorRunsExactlyOnce() {
        var creations = 0
        val holder = SingletonHolder<Int, Int> { arg ->
            creations++
            arg * 2
        }
        assertEquals(10, holder.getInstance(5))
        assertEquals(10, holder.getInstance(50))
        assertEquals(1, creations)
    }

    @Test
    fun holdersAreIndependent() {
        val first = SingletonHolder<StringBuilder, String> { StringBuilder(it) }
        val second = SingletonHolder<StringBuilder, String> { StringBuilder(it) }
        assertNotSame(first.getInstance("a"), second.getInstance("a"))
    }

    @Test
    fun companionObjectPatternWorks() {
        val instance = Service.getInstance("configured")
        assertEquals("configured", instance.label)
        assertSame(instance, Service.getInstance("ignored"))
    }
}
