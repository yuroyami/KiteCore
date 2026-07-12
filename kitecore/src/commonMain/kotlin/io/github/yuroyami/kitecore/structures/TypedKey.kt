/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A key for a [TypedMap] that carries the type of its value.
 *
 * The type parameter [T] is what makes [TypedMap] type-safe: a value stored
 * under a `TypedKey<T>` is returned as `T`. Keys compare by identity, so two
 * keys with the same [name] are distinct; [name] is a label for diagnostics
 * only. Create each key once and share the instance.
 *
 * @param T the type of the value stored under this key.
 * @constructor Creates a key labeled [name].
 */
public class TypedKey<T>(
    /** The diagnostic label of this key. Does not affect identity. */
    public val name: String,
) {

    /** Returns a diagnostic representation containing [name]. */
    override fun toString(): String = "TypedKey($name)"
}
