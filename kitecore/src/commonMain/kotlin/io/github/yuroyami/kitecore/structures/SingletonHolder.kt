/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A base class for singletons that need one construction argument.
 *
 * This is the multiplatform companion-object pattern for a singleton with an
 * argument: the companion object of the singleton class extends this holder
 * and callers obtain the instance through [getInstance].
 *
 * ```kotlin
 * class Analytics private constructor(config: Config) {
 *     companion object : SingletonHolder<Analytics, Config>(::Analytics)
 * }
 *
 * val analytics = Analytics.getInstance(config)
 * ```
 *
 * The creator function runs on the first [getInstance] call and its reference
 * is released afterwards; every later call returns the same instance and
 * ignores its argument.
 *
 * This class is not thread-safe.
 *
 * @param T the singleton type.
 * @param A the construction argument type.
 * @constructor Creates a holder that builds the instance with [creator] on
 * first use.
 */
public open class SingletonHolder<T, A>(creator: (A) -> T) {

    private var creator: ((A) -> T)? = creator
    private var instance: T? = null
    private var created = false

    /**
     * Returns the singleton instance, creating it from [arg] on the first
     * call. Later calls ignore [arg] and return the existing instance.
     */
    public fun getInstance(arg: A): T {
        if (!created) {
            instance = creator!!(arg)
            creator = null
            created = true
        }
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }
}
