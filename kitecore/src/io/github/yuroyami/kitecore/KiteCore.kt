/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.kitecore

// `./kotlin check version` fails when VERSION differs from settings.publishing.version in module.yaml.
// `./kotlin do updateVersion` writes the module.yaml version here.

/** Build facts about the KiteCore library. */
public object KiteCore {
    /**
     * The KiteCore version that your code was compiled against, such as `"0.2.0"`.
     *
     * It is a `const`, so the compiler copies the value into your code.
     */
    public const val VERSION: String = "0.2.0"
}
