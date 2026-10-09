/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.buildplugins.abi

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface AbiCheckSettings {
    /** Directory of the committed JVM ABI dump, relative to the module directory. The file is `<module>.api`. */
    val dumpDir: String get() = "api/jvm"
}
