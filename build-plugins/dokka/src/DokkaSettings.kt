/* Copyright 2026 yuroyami. Apache License, Version 2.0 (see LICENSE). */

package io.github.yuroyami.buildplugins.dokka

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface DokkaSettings {
    /** Name shown as the title of the API site. */
    val moduleName: String

    /** Markdown files with module and package docs, such as `Module.md`. */
    val includes: List<Path> get() = emptyList()

    /** Extra CSS files for the HTML pages. */
    val customStyleSheets: List<Path> get() = emptyList()

    /** Directory with FreeMarker templates that replace Dokka's own, such as `includes/header.ftl`. */
    val templatesDir: Path?

    /** Text shown in the page footer. */
    val footerMessage: String?
}
