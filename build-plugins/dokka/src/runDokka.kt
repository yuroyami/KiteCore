/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.buildplugins.dokka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.ModuleSources
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Runs the Dokka CLI on the common source set only: the module's `src` directory plus the sources that
 * other tasks generate for it. Platform source sets (`src@jvm`, ...) are left out on purpose.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction(ExecutionAvoidance.Disabled)
fun runDokka(
    @Input settings: DokkaSettings,
    moduleVersion: String?,
    @Input sources: ModuleSources,
    @Input moduleSourceDir: Path,
    @Input classpath: Classpath,
    @Input dokkaCli: Classpath,
    @Input dokkaPlugins: Classpath,
    @Output workDir: Path,
    @Output outputDir: Path,
) {
    println("Dokka source directories offered by the build:")
    sources.sourceDirectories.forEach { println("  $it") }

    // Platform directories carry an @ qualifier; generated common sources live in the build directory.
    val commonRoots = sources.sourceDirectories
        .filter { it == moduleSourceDir || !it.startsWith(moduleSourceDir.parent) }
        .filter { it.exists() }
        .map { it.absolutePathString() }
    println("Dokka common source roots:")
    commonRoots.forEach { println("  $it") }

    val baseConfig = DokkaBaseConfiguration(
        customStyleSheets = settings.customStyleSheets.map { it.absolutePathString() },
        templatesDir = settings.templatesDir?.absolutePathString(),
        footerMessage = settings.footerMessage,
    )
    val configuration = DokkaConfiguration(
        moduleName = settings.moduleName,
        moduleVersion = moduleVersion,
        outputDir = outputDir.absolutePathString(),
        sourceSets = listOf(
            DokkaSourceSet(
                sourceSetID = SourceSetId(scopeId = settings.moduleName, sourceSetName = "commonMain"),
                displayName = "common",
                // The build only offers a JVM classpath. A "common" analysis cannot resolve JVM jars.
                analysisPlatform = "jvm",
                sourceRoots = commonRoots,
                classpath = classpath.resolvedFiles.map { it.absolutePathString() },
                includes = settings.includes.map { it.absolutePathString() },
            )
        ),
        pluginsClasspath = dokkaPlugins.resolvedFiles.map { it.absolutePathString() },
        pluginsConfiguration = listOf(
            PluginConfiguration(
                fqPluginName = "org.jetbrains.dokka.base.DokkaBase",
                serializationFormat = "JSON",
                values = json.encodeToString(baseConfig),
            )
        ),
    )

    outputDir.deleteRecursively()
    outputDir.createDirectories()
    val configFile = workDir.createDirectories().resolve("dokka-configuration.json")
    configFile.writeText(json.encodeToString(configuration))

    // Dokka's analysis uses sun.misc.Unsafe, which JDK 24 and later report with four warning lines.
    val jvmOptions = if (Runtime.version().feature() >= 24) listOf("--sun-misc-unsafe-memory-access=allow") else emptyList()
    val command = listOf(ProcessHandle.current().info().command().orElse("java")) + jvmOptions + listOf(
        "-jar",
        dokkaCli.resolvedFiles.single { it.fileName.toString().startsWith("dokka-cli") }.pathString,
        configFile.pathString,
    )
    val process = ProcessBuilder(command).start()
    val pumps = listOf(
        thread { process.inputStream.copyTo(System.out) },
        thread { process.errorStream.copyTo(System.err) },
    )
    val exitCode = process.waitFor()
    pumps.forEach { it.join() }
    check(exitCode == 0) { "Dokka stopped with exit code $exitCode. See the log above." }
}

private val json = Json { encodeDefaults = true; explicitNulls = false }

@Serializable
private data class DokkaConfiguration(
    val moduleName: String,
    val moduleVersion: String?,
    val outputDir: String,
    val sourceSets: List<DokkaSourceSet>,
    val pluginsClasspath: List<String>,
    val pluginsConfiguration: List<PluginConfiguration>,
)

@Serializable
private data class SourceSetId(val scopeId: String, val sourceSetName: String)

@Serializable
private data class DokkaSourceSet(
    val sourceSetID: SourceSetId,
    val displayName: String,
    val analysisPlatform: String,
    val sourceRoots: List<String>,
    val classpath: List<String>,
    val includes: List<String>,
)

@Serializable
private data class PluginConfiguration(
    val fqPluginName: String,
    val serializationFormat: String,
    val values: String,
)

@Serializable
private data class DokkaBaseConfiguration(
    val customStyleSheets: List<String>,
    val templatesDir: String? = null,
    val footerMessage: String? = null,
)
