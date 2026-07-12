/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnceFlagTest {

    @Test
    fun runOnceRunsTheBlockOnlyOnFirstCall() {
        val flag = OnceFlag()
        var runs = 0
        assertTrue(flag.runOnce { runs++ })
        assertFalse(flag.runOnce { runs++ })
        assertFalse(flag.runOnce { runs++ })
        assertEquals(1, runs)
    }

    @Test
    fun hasRunTransitionsFromFalseToTrue() {
        val flag = OnceFlag()
        assertFalse(flag.hasRun)
        flag.runOnce { }
        assertTrue(flag.hasRun)
    }

    @Test
    fun resetAllowsTheBlockToRunAgain() {
        val flag = OnceFlag()
        var runs = 0
        flag.runOnce { runs++ }
        flag.reset()
        assertFalse(flag.hasRun)
        assertTrue(flag.runOnce { runs++ })
        assertEquals(2, runs)
    }

    @Test
    fun throwingBlockStillConsumesTheFlag() {
        val flag = OnceFlag()
        assertFailsWith<IllegalStateException> {
            flag.runOnce { throw IllegalStateException("boom") }
        }
        assertTrue(flag.hasRun)
        var runs = 0
        assertFalse(flag.runOnce { runs++ })
        assertEquals(0, runs)
    }

    @Test
    fun resetOnFreshFlagIsHarmless() {
        val flag = OnceFlag()
        flag.reset()
        assertFalse(flag.hasRun)
        assertTrue(flag.runOnce { })
    }
}
