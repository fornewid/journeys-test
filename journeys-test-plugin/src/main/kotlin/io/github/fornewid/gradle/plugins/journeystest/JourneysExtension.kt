package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** The `journeys { ... }` configuration block. */
abstract class JourneysExtension {
    /**
     * The agent CLI invocation, without a prompt (the plugin appends the journey prompt).
     * Examples: `claude --no-session-persistence --allowedTools 'Bash(adb *)' -p`,
     * `codex exec --ephemeral --sandbox workspace-write
     * -c 'sandbox_workspace_write.network_access=true'`, `agy -p`.
     */
    abstract val agentCommand: Property<String>

    /**
     * Instruction sent to the agent; `{journey}` is replaced with the journey file's absolute path.
     * Defaults to a built-in prompt matching Android Studio's journeys protocol; set this only to customize it.
     */
    abstract val prompt: Property<String>

    /** Journey XML root. Default `src/journeysTest` (matches Android Studio). */
    abstract val journeysDir: DirectoryProperty

    /** Verdict JSON + HTML report output. Default `build/journeys`. */
    abstract val outputDir: DirectoryProperty

    /** JUnit XML report output. Default `build/journey-results`. */
    abstract val reportsDir: DirectoryProperty

    /** Agent timeout for one journey, in seconds. Default 300. */
    abstract val timeoutSeconds: Property<Long>

    /**
     * How long to queue behind other builds using the device before failing, in seconds.
     * Default 600, above `timeoutSeconds` so a neighbour is never given up on mid-journey.
     */
    abstract val deviceWaitSeconds: Property<Long>
}
