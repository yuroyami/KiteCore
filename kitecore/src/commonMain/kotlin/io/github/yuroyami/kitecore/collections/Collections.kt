/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.collections

import kotlin.jvm.JvmName

// -------------------------------------------------------------------------
// Positional access
// -------------------------------------------------------------------------

/**
 * Returns the second element.
 *
 * @throws IndexOutOfBoundsException if the list has fewer than two elements.
 */
public fun <T> List<T>.second(): T {
    if (size < 2) throw IndexOutOfBoundsException("Index 1 is out of bounds for a list of size $size.")
    return this[1]
}

/** Returns the second element, or null if the list has fewer than two elements. */
public fun <T> List<T>.secondOrNull(): T? = getOrNull(1)

/**
 * Returns the third element.
 *
 * @throws IndexOutOfBoundsException if the list has fewer than three elements.
 */
public fun <T> List<T>.third(): T {
    if (size < 3) throw IndexOutOfBoundsException("Index 2 is out of bounds for a list of size $size.")
    return this[2]
}

/** Returns the third element, or null if the list has fewer than three elements. */
public fun <T> List<T>.thirdOrNull(): T? = getOrNull(2)

/**
 * Returns the fourth element.
 *
 * @throws IndexOutOfBoundsException if the list has fewer than four elements.
 */
public fun <T> List<T>.fourth(): T {
    if (size < 4) throw IndexOutOfBoundsException("Index 3 is out of bounds for a list of size $size.")
    return this[3]
}

/** Returns the fourth element, or null if the list has fewer than four elements. */
public fun <T> List<T>.fourthOrNull(): T? = getOrNull(3)

/**
 * Returns the element before the last one.
 *
 * @throws IndexOutOfBoundsException if the list has fewer than two elements.
 */
public fun <T> List<T>.penultimate(): T {
    if (size < 2) throw IndexOutOfBoundsException("Index ${size - 2} is out of bounds for a list of size $size.")
    return this[size - 2]
}

/** Returns the element before the last one, or null if the list has fewer than two elements. */
public fun <T> List<T>.penultimateOrNull(): T? = getOrNull(size - 2)

// -------------------------------------------------------------------------
// Copying transformations
// -------------------------------------------------------------------------

/**
 * Returns a copy of this list with the elements at [i] and [j] exchanged.
 *
 * The receiver is not modified.
 *
 * @throws IndexOutOfBoundsException if [i] or [j] is not a valid index.
 */
public fun <T> List<T>.swapped(i: Int, j: Int): List<T> {
    val result = toMutableList()
    val tmp = result[i]
    result[i] = result[j]
    result[j] = tmp
    return result
}

/**
 * Returns a copy of this list with the element at [fromIndex] moved so that it sits at [toIndex] in the result.
 *
 * Elements between the two positions shift by one. The receiver is not modified.
 *
 * @throws IndexOutOfBoundsException if [fromIndex] or [toIndex] is not a valid index of this list.
 */
public fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    val result = toMutableList()
    val element = result.removeAt(fromIndex)
    result.add(toIndex, element)
    return result
}

/**
 * Returns a copy of this list with the element at [index] replaced by [element].
 *
 * @throws IndexOutOfBoundsException if [index] is not a valid index.
 */
public fun <T> List<T>.replacedAt(index: Int, element: T): List<T> {
    val result = toMutableList()
    result[index] = element
    return result
}

/**
 * Returns a copy of this list grown to [size] elements by appending [element].
 *
 * When [size] is not greater than the current size, including when it is negative,
 * the copy is returned unchanged.
 */
public fun <T> List<T>.padded(size: Int, element: T): List<T> {
    if (size <= this.size) return toList()
    val result = ArrayList<T>(size)
    result.addAll(this)
    repeat(size - this.size) { result.add(element) }
    return result
}

/**
 * Returns a copy of this list rotated by [distance] positions.
 *
 * The element at index `i` ends at index `(i + distance).mod(size)`, so a positive
 * [distance] shifts elements toward higher indexes. Any [distance] is accepted;
 * it is normalized modulo the list size. An empty receiver yields an empty list.
 */
public fun <T> List<T>.rotated(distance: Int): List<T> {
    if (isEmpty()) return emptyList()
    val shift = distance.mod(size)
    if (shift == 0) return toList()
    return subList(size - shift, size) + subList(0, size - shift)
}

/**
 * Returns true if this list begins with the elements of [prefix] in order.
 *
 * An empty [prefix] always matches. Runs in O(prefix.size).
 */
public fun <T> List<T>.startsWith(prefix: List<T>): Boolean {
    if (prefix.size > size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

/**
 * Returns true if this list ends with the elements of [suffix] in order.
 *
 * An empty [suffix] always matches. Runs in O(suffix.size).
 */
public fun <T> List<T>.endsWith(suffix: List<T>): Boolean {
    if (suffix.size > size) return false
    val offset = size - suffix.size
    for (index in suffix.indices) {
        if (this[offset + index] != suffix[index]) return false
    }
    return true
}

/**
 * Returns the first element paired with a list of the remaining elements.
 *
 * @throws NoSuchElementException if the list is empty.
 */
public fun <T> List<T>.headAndTail(): Pair<T, List<T>> {
    if (isEmpty()) throw NoSuchElementException("List is empty.")
    return first() to drop(1)
}

/** Returns the first element paired with the remaining elements, or null if the list is empty. */
public fun <T> List<T>.headAndTailOrNull(): Pair<T, List<T>>? =
    if (isEmpty()) null else first() to drop(1)

/**
 * Returns a list containing this list's elements repeated [times] times in sequence.
 *
 * Zero [times] or an empty receiver yields an empty list.
 *
 * @throws IllegalArgumentException if [times] is negative.
 */
public fun <T> List<T>.repeated(times: Int): List<T> {
    require(times >= 0) { "times must be non-negative but was $times." }
    if (times == 0 || isEmpty()) return emptyList()
    val result = ArrayList<T>(size * times)
    repeat(times) { result.addAll(this) }
    return result
}

/**
 * Returns a list alternating elements of this list and [other], starting with this list.
 *
 * Once the shorter list is exhausted, the remaining elements of the longer list are appended.
 */
public fun <T> List<T>.interleaved(other: List<T>): List<T> {
    val result = ArrayList<T>(size + other.size)
    val common = minOf(size, other.size)
    for (index in 0 until common) {
        result.add(this[index])
        result.add(other[index])
    }
    for (index in common until size) result.add(this[index])
    for (index in common until other.size) result.add(other[index])
    return result
}

/**
 * Returns this list of rows transposed into a list of columns.
 *
 * An empty receiver, or one whose rows are empty, yields an empty list.
 *
 * @throws IllegalArgumentException if the rows do not all have the same size.
 */
public fun <T> List<List<T>>.transposed(): List<List<T>> {
    if (isEmpty()) return emptyList()
    val width = first().size
    require(all { it.size == width }) { "All rows must have the same size; expected $width." }
    if (width == 0) return emptyList()
    return List(width) { column -> List(size) { row -> this[row][column] } }
}

/**
 * Returns each element paired with its successor, where the last element wraps around to the first.
 *
 * The result has the same size as the receiver. A single-element list yields one pair
 * of the element with itself; an empty list yields an empty list.
 */
public fun <T> List<T>.zipWithNextCircular(): List<Pair<T, T>> {
    if (isEmpty()) return emptyList()
    return List(size) { index -> this[index] to this[(index + 1) % size] }
}

// -------------------------------------------------------------------------
// In-place mutations
// -------------------------------------------------------------------------

/**
 * Exchanges the elements at [i] and [j] in place.
 *
 * @throws IndexOutOfBoundsException if [i] or [j] is not a valid index.
 */
public fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

/**
 * Moves the element at [fromIndex] so that it sits at [toIndex], shifting the elements between them.
 *
 * @throws IndexOutOfBoundsException if [fromIndex] or [toIndex] is not a valid index of this list.
 */
public fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    val element = removeAt(fromIndex)
    add(toIndex, element)
}

/**
 * Removes the first element matching [predicate] and returns true, or returns false if none matches.
 *
 * At most one element is removed.
 */
public inline fun <T> MutableList<T>.removeFirstWhere(predicate: (T) -> Boolean): Boolean {
    val iterator = listIterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.remove()
            return true
        }
    }
    return false
}

/**
 * Removes the last element matching [predicate] and returns true, or returns false if none matches.
 *
 * At most one element is removed.
 */
public inline fun <T> MutableList<T>.removeLastWhere(predicate: (T) -> Boolean): Boolean {
    val iterator = listIterator(size)
    while (iterator.hasPrevious()) {
        if (predicate(iterator.previous())) {
            iterator.remove()
            return true
        }
    }
    return false
}

/**
 * Replaces the element at [index] with the result of applying [transform] to it.
 *
 * @throws IndexOutOfBoundsException if [index] is not a valid index.
 */
public inline fun <T> MutableList<T>.updateAt(index: Int, transform: (T) -> T) {
    this[index] = transform(this[index])
}

/**
 * Rotates this list in place by [distance] positions.
 *
 * The element at index `i` ends at index `(i + distance).mod(size)`. Any [distance]
 * is accepted; it is normalized modulo the list size. Lists with fewer than two
 * elements are left unchanged.
 */
public fun <T> MutableList<T>.rotate(distance: Int) {
    if (size < 2) return
    val shifted = rotated(distance)
    for (index in indices) this[index] = shifted[index]
}

/**
 * Grows this list in place to [size] elements by appending [element].
 *
 * Does nothing when [size] is not greater than the current size, including when it is negative.
 */
public fun <T> MutableList<T>.padTo(size: Int, element: T) {
    while (this.size < size) add(element)
}

// -------------------------------------------------------------------------
// Grouping and analysis
// -------------------------------------------------------------------------

/**
 * Splits this iterable into consecutive runs, starting a new run whenever the value of [selector] changes.
 *
 * Elements are kept in encounter order and every element appears in exactly one run.
 * An empty receiver yields an empty list.
 */
public inline fun <T, K> Iterable<T>.chunkedBy(selector: (T) -> K): List<List<T>> {
    val result = ArrayList<List<T>>()
    var current = ArrayList<T>()
    var currentKey: K? = null
    var hasCurrent = false
    for (element in this) {
        val key = selector(element)
        if (hasCurrent && key != currentKey) {
            result.add(current)
            current = ArrayList()
        }
        current.add(element)
        currentKey = key
        hasCurrent = true
    }
    if (hasCurrent) result.add(current)
    return result
}

/**
 * Splits this iterable into segments separated by elements matching [isDelimiter], dropping the delimiters.
 *
 * The result always has one more segment than there are delimiters, so consecutive
 * delimiters and delimiters at either end produce empty segments. An empty receiver
 * yields a single empty segment.
 */
public inline fun <T> Iterable<T>.splitBy(isDelimiter: (T) -> Boolean): List<List<T>> {
    val result = ArrayList<List<T>>()
    var current = ArrayList<T>()
    for (element in this) {
        if (isDelimiter(element)) {
            result.add(current)
            current = ArrayList()
        } else {
            current.add(element)
        }
    }
    result.add(current)
    return result
}

/**
 * Returns the set of elements that occur more than once.
 *
 * Ordering follows the first repeat occurrence of each element. An empty or
 * duplicate-free receiver yields an empty set.
 */
public fun <T> Iterable<T>.duplicates(): Set<T> {
    val seen = HashSet<T>()
    val result = LinkedHashSet<T>()
    for (element in this) {
        if (!seen.add(element)) result.add(element)
    }
    return result
}

/**
 * Returns a map of each distinct element to the number of times it occurs.
 *
 * Keys are ordered by first occurrence. An empty receiver yields an empty map.
 */
public fun <T> Iterable<T>.frequencies(): Map<T, Int> {
    val result = LinkedHashMap<T, Int>()
    for (element in this) {
        result[element] = (result[element] ?: 0) + 1
    }
    return result
}

/**
 * Returns a map of each distinct value of [selector] to the number of elements producing it.
 *
 * Keys are ordered by first occurrence. An empty receiver yields an empty map.
 */
public inline fun <T, K> Iterable<T>.countBy(selector: (T) -> K): Map<K, Int> {
    val result = LinkedHashMap<K, Int>()
    for (element in this) {
        val key = selector(element)
        result[key] = (result[key] ?: 0) + 1
    }
    return result
}

/**
 * Returns the most frequent element, or null if the receiver is empty.
 *
 * When several elements share the highest count, the one encountered first wins.
 */
public fun <T> Iterable<T>.mode(): T? {
    var best: T? = null
    var bestCount = 0
    for ((element, count) in frequencies()) {
        if (count > bestCount) {
            best = element
            bestCount = count
        }
    }
    return best
}

/**
 * Returns the smallest and largest element as a pair, or null if the receiver is empty.
 *
 * Both values are found in a single pass. A single-element receiver pairs the element with itself.
 */
public fun <T : Comparable<T>> Iterable<T>.minMaxOrNull(): Pair<T, T>? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var min = iterator.next()
    var max = min
    while (iterator.hasNext()) {
        val element = iterator.next()
        if (element < min) min = element
        if (element > max) max = element
    }
    return min to max
}

/**
 * Returns every pairing of an element of this iterable with an element of [other].
 *
 * Pairs are ordered with the receiver as the outer loop, so all pairings of the first
 * element come before those of the second. [other] is materialized once, so a
 * single-pass iterable is safe to pass. Either side being empty yields an empty list.
 */
public fun <T, R> Iterable<T>.cartesianProduct(other: Iterable<R>): List<Pair<T, R>> {
    val others = other.toList()
    val result = ArrayList<Pair<T, R>>()
    for (a in this) {
        for (b in others) {
            result.add(a to b)
        }
    }
    return result
}

/**
 * Splits this iterable into a pair of lists using [predicate], which also receives each element's index.
 *
 * The first list holds elements for which [predicate] returned true, the second the rest.
 * Encounter order is preserved in both lists.
 */
public inline fun <T> Iterable<T>.partitionIndexed(predicate: (index: Int, T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    var index = 0
    for (element in this) {
        if (predicate(index, element)) first.add(element) else second.add(element)
        index++
    }
    return first to second
}

/**
 * Returns a map of each element to its index in this iterable.
 *
 * When an element occurs more than once, the index of its last occurrence wins.
 * Keys are ordered by first occurrence.
 */
public fun <T> Iterable<T>.associateWithIndex(): Map<T, Int> {
    val result = LinkedHashMap<T, Int>()
    var index = 0
    for (element in this) {
        result[element] = index
        index++
    }
    return result
}

/**
 * Returns a set of the results of applying [transform] to each element.
 *
 * Ordering follows the first occurrence of each transformed value.
 */
public inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> {
    val result = LinkedHashSet<R>()
    for (element in this) {
        result.add(transform(element))
    }
    return result
}

/**
 * Returns true if no element occurs more than once.
 *
 * Short-circuits on the first repeat. An empty receiver is distinct.
 */
public fun <T> Iterable<T>.isDistinct(): Boolean {
    val seen = HashSet<T>()
    for (element in this) {
        if (!seen.add(element)) return false
    }
    return true
}

/**
 * Returns true if at least one element occurs more than once.
 *
 * Short-circuits on the first repeat. An empty receiver has no duplicates.
 */
public fun <T> Iterable<T>.anyDuplicate(): Boolean = !isDistinct()

// -------------------------------------------------------------------------
// Products
// -------------------------------------------------------------------------

/**
 * Returns the product of all elements, accumulated as a [Long].
 *
 * An empty receiver yields 1. The result wraps on overflow.
 */
@JvmName("productOfInt")
public fun Iterable<Int>.product(): Long {
    var result = 1L
    for (element in this) result *= element
    return result
}

/**
 * Returns the product of all elements.
 *
 * An empty receiver yields 1. The result wraps on overflow.
 */
@JvmName("productOfLong")
public fun Iterable<Long>.product(): Long {
    var result = 1L
    for (element in this) result *= element
    return result
}

/**
 * Returns the product of all elements.
 *
 * An empty receiver yields 1.0.
 */
@JvmName("productOfDouble")
public fun Iterable<Double>.product(): Double {
    var result = 1.0
    for (element in this) result *= element
    return result
}

// -------------------------------------------------------------------------
// Emptiness
// -------------------------------------------------------------------------

/** Returns this collection if it is not empty, or null otherwise. */
public fun <C : Collection<*>> C.takeIfNotEmpty(): C? = if (isEmpty()) null else this

// -------------------------------------------------------------------------
// Sets
// -------------------------------------------------------------------------

/**
 * Adds [element] if absent or removes it if present, and returns whether the set contains it afterwards.
 *
 * Returns true when the element was added, false when it was removed.
 */
public fun <T> MutableSet<T>.toggle(element: T): Boolean {
    return if (add(element)) {
        true
    } else {
        remove(element)
        false
    }
}

/**
 * Returns a copy of this set with [element] added if absent or removed if present.
 *
 * The receiver is not modified.
 */
public fun <T> Set<T>.toggled(element: T): Set<T> =
    if (element in this) this - element else this + element

/**
 * Returns the middle element, or the earlier of the two middle elements for even sizes.
 *
 * @throws NoSuchElementException when the list is empty.
 */
public fun <T> List<T>.middle(): T =
    if (isEmpty()) throw NoSuchElementException("List is empty.") else this[(size - 1) / 2]

/** Returns the middle element, or `null` when the list is empty. Even sizes use the earlier middle. */
public fun <T> List<T>.middleOrNull(): T? = if (isEmpty()) null else this[(size - 1) / 2]

/** Returns true when every element equals the first one. An empty iterable returns true. */
public fun <T> Iterable<T>.allEqual(): Boolean {
    val iterator = iterator()
    if (!iterator.hasNext()) return true
    val first = iterator.next()
    while (iterator.hasNext()) if (iterator.next() != first) return false
    return true
}

/** Returns every index at which [element] occurs, in ascending order. */
public fun <T> List<T>.indicesOf(element: T): List<Int> {
    val result = ArrayList<Int>()
    for (i in indices) if (this[i] == element) result.add(i)
    return result
}

/**
 * Splits the list into the elements before [index] and the elements from [index] onward.
 *
 * @throws IndexOutOfBoundsException when [index] is outside `0..size`.
 */
public fun <T> List<T>.splitAt(index: Int): Pair<List<T>, List<T>> {
    if (index < 0 || index > size) throw IndexOutOfBoundsException("index $index is outside 0..$size")
    return subList(0, index).toList() to subList(index, size).toList()
}
