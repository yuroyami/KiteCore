/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore.structures

/**
 * A resettable flag that runs a block at most once between resets.
 *
 * The flag is set before the block is invoked, so a block that throws still
 * consumes the flag and later calls do not run.
 *
 * This class is not thread-safe.
 *
 * @constructor Creates a flag that has not run yet.
 */
public class OnceFlag {

    /** `true` once [runOnce] has accepted a block, until [reset]. */
    public var hasRun: Boolean = false
        private set

    /**
     * Runs [block] if no block has run since construction or the last
     * [reset], and returns `true` only for that first call.
     *
     * The flag is set before [block] is invoked; if [block] throws, the
     * exception propagates and the flag stays set.
     */
    public fun runOnce(block: () -> Unit): Boolean {
        if (hasRun) return false
        hasRun = true
        block()
        return true
    }

    /** Clears the flag so the next [runOnce] runs its block again. */
    public fun reset() {
        hasRun = false
    }
}
