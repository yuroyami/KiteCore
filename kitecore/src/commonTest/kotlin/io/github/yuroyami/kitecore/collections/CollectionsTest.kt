/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CollectionsTest {

    // -- Positional access --------------------------------------------------

    @Test
    fun second_returns_element_or_throws() {
        assertEquals("b", listOf("a", "b", "c").second())
        assertFailsWith<IndexOutOfBoundsException> { listOf("a").second() }
        assertFailsWith<IndexOutOfBoundsException> { emptyList<String>().second() }
    }

    @Test
    fun secondOrNull_returns_element_or_null() {
        assertEquals(2, listOf(1, 2, 3).secondOrNull())
        assertNull(listOf(1).secondOrNull())
    }

    @Test
    fun third_returns_element_or_throws() {
        assertEquals("c", listOf("a", "b", "c").third())
        assertFailsWith<IndexOutOfBoundsException> { listOf("a", "b").third() }
    }

    @Test
    fun thirdOrNull_returns_element_or_null() {
        assertEquals(3, listOf(1, 2, 3).thirdOrNull())
        assertNull(listOf(1, 2).thirdOrNull())
    }

    @Test
    fun fourth_returns_element_or_throws() {
        assertEquals("d", listOf("a", "b", "c", "d").fourth())
        assertFailsWith<IndexOutOfBoundsException> { listOf("a", "b", "c").fourth() }
    }

    @Test
    fun fourthOrNull_returns_element_or_null() {
        assertEquals(4, listOf(1, 2, 3, 4).fourthOrNull())
        assertNull(listOf(1, 2, 3).fourthOrNull())
    }

    @Test
    fun penultimate_returns_element_before_last_or_throws() {
        assertEquals(2, listOf(1, 2, 3).penultimate())
        assertEquals(1, listOf(1, 2).penultimate())
        assertFailsWith<IndexOutOfBoundsException> { listOf(1).penultimate() }
        assertFailsWith<IndexOutOfBoundsException> { emptyList<Int>().penultimate() }
    }

    @Test
    fun penultimateOrNull_returns_element_or_null() {
        assertEquals(2, listOf(1, 2, 3).penultimateOrNull())
        assertNull(listOf(1).penultimateOrNull())
        assertNull(emptyList<Int>().penultimateOrNull())
    }

    // -- Copying transformations --------------------------------------------

    @Test
    fun swapped_exchanges_two_positions_in_a_copy() {
        val original = listOf(1, 2, 3)
        assertEquals(listOf(3, 2, 1), original.swapped(0, 2))
        assertEquals(listOf(1, 2, 3), original.swapped(1, 1))
        assertEquals(listOf(1, 2, 3), original, "receiver must stay untouched")
        assertFailsWith<IndexOutOfBoundsException> { original.swapped(0, 3) }
    }

    @Test
    fun moved_relocates_an_element_in_a_copy() {
        val original = listOf(1, 2, 3, 4)
        assertEquals(listOf(2, 3, 1, 4), original.moved(0, 2))
        assertEquals(listOf(4, 1, 2, 3), original.moved(3, 0))
        assertEquals(listOf(1, 2, 3, 4), original, "receiver must stay untouched")
        assertFailsWith<IndexOutOfBoundsException> { original.moved(4, 0) }
    }

    @Test
    fun replacedAt_substitutes_one_element_in_a_copy() {
        val original = listOf(1, 2, 3)
        assertEquals(listOf(1, 9, 3), original.replacedAt(1, 9))
        assertEquals(listOf(1, 2, 3), original, "receiver must stay untouched")
        assertFailsWith<IndexOutOfBoundsException> { original.replacedAt(3, 9) }
    }

    @Test
    fun padded_appends_up_to_the_requested_size() {
        assertEquals(listOf(1, 2, 0, 0), listOf(1, 2).padded(4, 0))
        assertEquals(listOf(1, 2), listOf(1, 2).padded(2, 0))
        assertEquals(listOf(1, 2), listOf(1, 2).padded(-1, 0))
        assertEquals(listOf(7, 7), emptyList<Int>().padded(2, 7))
    }

    @Test
    fun rotated_shifts_elements_toward_higher_indexes() {
        val original = listOf(1, 2, 3, 4)
        assertEquals(listOf(4, 1, 2, 3), original.rotated(1))
        assertEquals(listOf(2, 3, 4, 1), original.rotated(-1))
        assertEquals(listOf(3, 4, 1, 2), original.rotated(6))
        assertEquals(listOf(1, 2, 3, 4), original.rotated(0))
        assertEquals(emptyList(), emptyList<Int>().rotated(5))
    }

    @Test
    fun startsWith_matches_prefixes() {
        val list = listOf(1, 2, 3)
        assertTrue(list.startsWith(listOf(1, 2)))
        assertTrue(list.startsWith(emptyList()))
        assertTrue(list.startsWith(listOf(1, 2, 3)))
        assertFalse(list.startsWith(listOf(2)))
        assertFalse(list.startsWith(listOf(1, 2, 3, 4)))
    }

    @Test
    fun endsWith_matches_suffixes() {
        val list = listOf(1, 2, 3)
        assertTrue(list.endsWith(listOf(2, 3)))
        assertTrue(list.endsWith(emptyList()))
        assertTrue(list.endsWith(listOf(1, 2, 3)))
        assertFalse(list.endsWith(listOf(1)))
        assertFalse(list.endsWith(listOf(0, 1, 2, 3)))
    }

    @Test
    fun headAndTail_splits_first_element_from_rest() {
        assertEquals(1 to listOf(2, 3), listOf(1, 2, 3).headAndTail())
        assertEquals(1 to emptyList(), listOf(1).headAndTail())
        assertFailsWith<NoSuchElementException> { emptyList<Int>().headAndTail() }
    }

    @Test
    fun headAndTailOrNull_returns_null_on_empty() {
        assertEquals(1 to listOf(2), listOf(1, 2).headAndTailOrNull())
        assertNull(emptyList<Int>().headAndTailOrNull())
    }

    @Test
    fun repeated_concatenates_copies() {
        assertEquals(listOf(1, 2, 1, 2, 1, 2), listOf(1, 2).repeated(3))
        assertEquals(emptyList(), listOf(1, 2).repeated(0))
        assertEquals(emptyList(), emptyList<Int>().repeated(5))
        assertFailsWith<IllegalArgumentException> { listOf(1).repeated(-1) }
    }

    @Test
    fun interleaved_alternates_then_appends_the_longer_remainder() {
        assertEquals(listOf(1, 2, 3, 4, 5), listOf(1, 3, 5).interleaved(listOf(2, 4)))
        assertEquals(listOf(1, 2, 3, 4), listOf(1).interleaved(listOf(2, 3, 4)))
        assertEquals(listOf(1, 2), listOf(1, 2).interleaved(emptyList()))
        assertEquals(listOf(9), emptyList<Int>().interleaved(listOf(9)))
    }

    @Test
    fun transposed_turns_rows_into_columns() {
        assertEquals(
            listOf(listOf(1, 4), listOf(2, 5), listOf(3, 6)),
            listOf(listOf(1, 2, 3), listOf(4, 5, 6)).transposed(),
        )
        assertEquals(emptyList(), emptyList<List<Int>>().transposed())
        assertEquals(emptyList(), listOf(emptyList<Int>(), emptyList()).transposed())
        assertFailsWith<IllegalArgumentException> { listOf(listOf(1, 2), listOf(3)).transposed() }
    }

    @Test
    fun zipWithNextCircular_wraps_the_last_element_to_the_first() {
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 1), listOf(1, 2, 3).zipWithNextCircular())
        assertEquals(listOf(7 to 7), listOf(7).zipWithNextCircular())
        assertEquals(emptyList(), emptyList<Int>().zipWithNextCircular())
    }

    // -- In-place mutations --------------------------------------------------

    @Test
    fun swap_exchanges_two_positions_in_place() {
        val list = mutableListOf(1, 2, 3)
        list.swap(0, 2)
        assertEquals(listOf(3, 2, 1), list)
        assertFailsWith<IndexOutOfBoundsException> { list.swap(0, 5) }
    }

    @Test
    fun move_relocates_an_element_in_place() {
        val list = mutableListOf(1, 2, 3)
        list.move(2, 0)
        assertEquals(listOf(3, 1, 2), list)
        assertFailsWith<IndexOutOfBoundsException> { list.move(3, 0) }
    }

    @Test
    fun removeFirstWhere_removes_only_the_first_match() {
        val list = mutableListOf(1, 2, 3, 2)
        assertTrue(list.removeFirstWhere { it % 2 == 0 })
        assertEquals(listOf(1, 3, 2), list)
        assertFalse(list.removeFirstWhere { it > 10 })
        assertEquals(listOf(1, 3, 2), list)
    }

    @Test
    fun removeLastWhere_removes_only_the_last_match() {
        val list = mutableListOf(1, 2, 3, 2)
        assertTrue(list.removeLastWhere { it % 2 == 0 })
        assertEquals(listOf(1, 2, 3), list)
        assertFalse(list.removeLastWhere { it > 10 })
        assertEquals(listOf(1, 2, 3), list)
    }

    @Test
    fun updateAt_transforms_a_single_element_in_place() {
        val list = mutableListOf(1, 2, 3)
        list.updateAt(1) { it * 10 }
        assertEquals(listOf(1, 20, 3), list)
        assertFailsWith<IndexOutOfBoundsException> { list.updateAt(3) { it } }
    }

    @Test
    fun rotate_shifts_elements_in_place() {
        val list = mutableListOf(1, 2, 3, 4)
        list.rotate(1)
        assertEquals(listOf(4, 1, 2, 3), list)
        list.rotate(-1)
        assertEquals(listOf(1, 2, 3, 4), list)
        val single = mutableListOf(1)
        single.rotate(3)
        assertEquals(listOf(1), single)
    }

    @Test
    fun padTo_appends_in_place_up_to_the_requested_size() {
        val list = mutableListOf(1)
        list.padTo(3, 9)
        assertEquals(listOf(1, 9, 9), list)
        list.padTo(2, 9)
        assertEquals(listOf(1, 9, 9), list, "shrinking size must be a no-op")
    }

    // -- Grouping and analysis ------------------------------------------------

    @Test
    fun chunkedBy_starts_a_new_run_when_the_key_changes() {
        assertEquals(
            listOf(listOf(1, 1), listOf(2, 2), listOf(3), listOf(1)),
            listOf(1, 1, 2, 2, 3, 1).chunkedBy { it },
        )
        assertEquals(
            listOf(listOf(1, 3), listOf(2, 4), listOf(5)),
            listOf(1, 3, 2, 4, 5).chunkedBy { it % 2 },
        )
        assertEquals(emptyList(), emptyList<Int>().chunkedBy { it })
    }

    @Test
    fun splitBy_drops_delimiters_and_keeps_empty_segments() {
        assertEquals(
            listOf(listOf(1), listOf(2, 3), emptyList(), listOf(4)),
            listOf(1, 0, 2, 3, 0, 0, 4).splitBy { it == 0 },
        )
        assertEquals(
            listOf(emptyList(), listOf(1), emptyList()),
            listOf(0, 1, 0).splitBy { it == 0 },
        )
        assertEquals(listOf(emptyList()), emptyList<Int>().splitBy { it == 0 })
    }

    @Test
    fun duplicates_returns_repeated_elements_once() {
        assertEquals(setOf(1, 2), listOf(1, 2, 1, 3, 2, 1).duplicates())
        assertEquals(emptySet(), listOf(1, 2, 3).duplicates())
        assertEquals(emptySet(), emptyList<Int>().duplicates())
    }

    @Test
    fun frequencies_counts_each_distinct_element() {
        assertEquals(mapOf("a" to 2, "b" to 1), listOf("a", "b", "a").frequencies())
        assertEquals(emptyMap(), emptyList<String>().frequencies())
    }

    @Test
    fun countBy_counts_elements_per_selector_value() {
        assertEquals(
            mapOf(3 to 2, 5 to 1),
            listOf("one", "two", "three").countBy { it.length },
        )
        assertEquals(emptyMap(), emptyList<String>().countBy { it.length })
    }

    @Test
    fun mode_returns_the_most_frequent_element() {
        assertEquals(1, listOf(1, 2, 1, 3).mode())
        assertEquals(1, listOf(1, 2).mode(), "first encountered wins ties")
        assertNull(emptyList<Int>().mode())
    }

    @Test
    fun minMaxOrNull_finds_both_extremes_in_one_pass() {
        assertEquals(1 to 9, listOf(3, 9, 1, 4).minMaxOrNull())
        assertEquals(5 to 5, listOf(5).minMaxOrNull())
        assertNull(emptyList<Int>().minMaxOrNull())
    }

    @Test
    fun cartesianProduct_pairs_every_combination_in_order() {
        assertEquals(
            listOf(1 to "a", 1 to "b", 2 to "a", 2 to "b"),
            listOf(1, 2).cartesianProduct(listOf("a", "b")),
        )
        assertEquals(emptyList(), listOf(1).cartesianProduct(emptyList<String>()))
        assertEquals(emptyList(), emptyList<Int>().cartesianProduct(listOf("a")))
    }

    @Test
    fun partitionIndexed_splits_by_index_aware_predicate() {
        val (evenIndexes, oddIndexes) = listOf("a", "b", "c", "d").partitionIndexed { index, _ -> index % 2 == 0 }
        assertEquals(listOf("a", "c"), evenIndexes)
        assertEquals(listOf("b", "d"), oddIndexes)
    }

    @Test
    fun associateWithIndex_maps_elements_to_indexes() {
        assertEquals(mapOf("a" to 0, "b" to 1), listOf("a", "b").associateWithIndex())
        assertEquals(
            mapOf("a" to 2, "b" to 1),
            listOf("a", "b", "a").associateWithIndex(),
            "last occurrence wins",
        )
    }

    @Test
    fun mapToSet_deduplicates_transformed_values() {
        assertEquals(setOf(1, 0), listOf(1, 2, 3, 4).mapToSet { it % 2 })
        assertEquals(emptySet(), emptyList<Int>().mapToSet { it })
    }

    @Test
    fun isDistinct_detects_absence_of_duplicates() {
        assertTrue(listOf(1, 2, 3).isDistinct())
        assertFalse(listOf(1, 2, 1).isDistinct())
        assertTrue(emptyList<Int>().isDistinct())
    }

    @Test
    fun anyDuplicate_detects_presence_of_duplicates() {
        assertTrue(listOf(1, 2, 1).anyDuplicate())
        assertFalse(listOf(1, 2, 3).anyDuplicate())
        assertFalse(emptyList<Int>().anyDuplicate())
    }

    // -- Products --------------------------------------------------------------

    @Test
    fun product_of_ints_accumulates_as_long() {
        assertEquals(24L, listOf(2, 3, 4).product())
        assertEquals(1L, emptyList<Int>().product())
        assertEquals(4_000_000_000L, listOf(2_000_000_000, 2).product(), "must not overflow Int")
    }

    @Test
    fun product_of_longs_multiplies_all_elements() {
        assertEquals(30L, listOf(2L, 3L, 5L).product())
        assertEquals(1L, emptyList<Long>().product())
    }

    @Test
    fun product_of_doubles_multiplies_all_elements() {
        assertEquals(3.75, listOf(2.5, 1.5).product(), 1e-9)
        assertEquals(1.0, emptyList<Double>().product(), 1e-9)
    }

    // -- Emptiness ---------------------------------------------------------------

    @Test
    fun takeIfNotEmpty_returns_receiver_or_null() {
        val list = listOf(1, 2)
        assertSame(list, list.takeIfNotEmpty())
        assertNull(emptyList<Int>().takeIfNotEmpty())
        assertNull(emptySet<Int>().takeIfNotEmpty())
    }

    // -- Sets ----------------------------------------------------------------------

    @Test
    fun toggle_flips_membership_in_place() {
        val set = mutableSetOf(1, 2)
        assertTrue(set.toggle(3), "added, so contained afterwards")
        assertEquals(setOf(1, 2, 3), set)
        assertFalse(set.toggle(1), "removed, so absent afterwards")
        assertEquals(setOf(2, 3), set)
    }

    @Test
    fun toggled_flips_membership_in_a_copy() {
        val set = setOf(1, 2)
        assertEquals(setOf(1, 2, 3), set.toggled(3))
        assertEquals(setOf(2), set.toggled(1))
        assertEquals(setOf(1, 2), set, "receiver must stay untouched")
    }

    @Test
    fun middleReturnsTheCentralElement() {
        assertEquals(2, listOf(1, 2, 3).middle())
        assertEquals(2, listOf(1, 2, 3, 4).middle())
        assertFailsWith<NoSuchElementException> { emptyList<Int>().middle() }
        assertNull(emptyList<Int>().middleOrNull())
        assertEquals(1, listOf(1).middleOrNull())
    }

    @Test
    fun allEqualAndIndicesOf() {
        assertTrue(listOf(3, 3, 3).allEqual())
        assertFalse(listOf(3, 4).allEqual())
        assertTrue(emptyList<Int>().allEqual())
        assertEquals(listOf(0, 2), listOf("a", "b", "a").indicesOf("a"))
        assertEquals(emptyList(), listOf("a").indicesOf("z"))
    }

    @Test
    fun splitAtDividesAroundTheIndex() {
        assertEquals(listOf(1) to listOf(2, 3), listOf(1, 2, 3).splitAt(1))
        assertEquals(emptyList<Int>() to listOf(1, 2), listOf(1, 2).splitAt(0))
        assertEquals(listOf(1, 2) to emptyList(), listOf(1, 2).splitAt(2))
        assertFailsWith<IndexOutOfBoundsException> { listOf(1).splitAt(5) }
    }
}
