/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.buildplugins.abi

import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import org.jetbrains.kotlin.abi.tools.AbiFilters
import org.jetbrains.kotlin.abi.tools.AbiTools
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/** Writes the JVM ABI dump of the module's compiled classes, in the same format as the Kotlin Gradle plugin. */
@TaskAction
fun dumpJvmAbi(
    @Input classes: CompilationArtifact,
    @Output dumpFile: Path,
) {
    val classFiles = classes.artifact.walk()
        .filter { it.isRegularFile() && it.extension == "class" }
        .map { it.toFile() }
        .sortedBy { it.path }
        .toList()
    val dump = StringBuilder()
    AbiTools.getInstance(AbiTools::class.java.classLoader).printJvmDump(dump, classFiles, AbiFilters.EMPTY)
    dumpFile.createParentDirectories().writeText(dump)
}

/** Fails when the built dump differs from the committed one. Line endings are ignored. */
@TaskAction
fun checkJvmAbi(
    moduleName: String,
    @Input builtDump: Path,
    // The committed dump is a baseline: reading it must not make `update` run first.
    @Input(inferTaskDependency = false) committedDump: Path,
) {
    check(committedDump.exists()) {
        "No ABI dump at $committedDump. Run `./kotlin do updateAbi -m $moduleName` to create it."
    }
    val expected = committedDump.readText().lines()
    val actual = builtDump.readText().lines()
    if (expected == actual) return
    val diff = AbiTools.getInstance(AbiTools::class.java.classLoader)
        .filesDiff(committedDump.toFile(), builtDump.toFile())
    error(
        "The public API of $moduleName changed.\n$diff\n\n" +
            "If the change is intended, run `./kotlin do updateAbi -m $moduleName` and commit the dump."
    )
}

/** Overwrites the committed dump with the built one. */
@TaskAction
fun updateJvmAbi(
    @Input builtDump: Path,
    @Output committedDump: Path,
) {
    builtDump.copyTo(committedDump.createParentDirectories(), overwrite = true)
}
