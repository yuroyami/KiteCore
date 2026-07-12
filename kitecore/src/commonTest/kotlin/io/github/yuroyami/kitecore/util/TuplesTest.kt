/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TuplesTest {

    @Test
    fun swapped_exchanges_the_components() {
        assertEquals("v" to 1, (1 to "v").swapped())
    }

    @Test
    fun mapFirst_on_pair_transforms_only_the_first_component() {
        assertEquals(2 to "b", (1 to "b").mapFirst { it * 2 })
    }

    @Test
    fun mapSecond_on_pair_transforms_only_the_second_component() {
        assertEquals(1 to "BB", (1 to "bb").mapSecond { it.uppercase() })
    }

    @Test
    fun mapBoth_transforms_both_components_independently() {
        assertEquals(10 to "x!", (5 to "x").mapBoth({ it * 2 }, { "$it!" }))
    }

    @Test
    fun toTriple_appends_a_third_component() {
        assertEquals(Triple(1, "b", 'c'), (1 to "b").toTriple('c'))
    }

    @Test
    fun dropFirst_keeps_the_second_and_third_components() {
        assertEquals("b" to 'c', Triple(1, "b", 'c').dropFirst())
    }

    @Test
    fun dropSecond_keeps_the_first_and_third_components() {
        assertEquals(1 to 'c', Triple(1, "b", 'c').dropSecond())
    }

    @Test
    fun dropThird_keeps_the_first_and_second_components() {
        assertEquals(1 to "b", Triple(1, "b", 'c').dropThird())
    }

    @Test
    fun mapFirst_on_triple_transforms_only_the_first_component() {
        assertEquals(Triple(2, "b", 'c'), Triple(1, "b", 'c').mapFirst { it * 2 })
    }

    @Test
    fun mapSecond_on_triple_transforms_only_the_second_component() {
        assertEquals(Triple(1, "B", 'c'), Triple(1, "b", 'c').mapSecond { it.uppercase() })
    }

    @Test
    fun mapThird_on_triple_transforms_only_the_third_component() {
        assertEquals(Triple(1, "b", "c!"), Triple(1, "b", 'c').mapThird { "$it!" })
    }
}
