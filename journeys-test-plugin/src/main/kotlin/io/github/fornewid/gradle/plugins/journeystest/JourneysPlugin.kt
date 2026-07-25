package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The `io.github.fornewid.journeys-test` plugin.
 *
 * Registers a `journeysTest` task that discovers journey XML under `src/journeysTest`, runs each
 * via the configured agent, and writes a JUnit XML report (build/journey-results) plus verdict
 * JSON and an HTML report (build/journeys). Folder, extension, and task name match Android
 * Studio's managed Journeys, so the same project is shared between both.
 *
 * ```kotlin
 * plugins { id("io.github.fornewid.journeys-test") }
 * journeys { agentCommand = "claude --no-session-persistence --allowedTools 'Bash(android *)' 'Bash(adb *)' -p" }
 * ```
 */
class JourneysPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext =
            project.extensions.create("journeys", JourneysExtension::class.java).apply {
                agentCommand.convention(project.providers.environmentVariable(JourneyConfig.ENV_AGENT_COMMAND))
                prompt.convention(JourneyConfig.DEFAULT_PROMPT)
                journeysDir.convention(project.layout.projectDirectory.dir(JourneyConfig.DEFAULT_JOURNEYS_DIR))
                outputDir.convention(project.layout.buildDirectory.dir(JourneyConfig.DEFAULT_OUTPUT_DIR))
                reportsDir.convention(project.layout.buildDirectory.dir(JourneyConfig.DEFAULT_REPORTS_DIR))
                timeoutSeconds.convention(JourneyConfig.DEFAULT_TIMEOUT_SECONDS)
            }

        project.tasks.register("journeysTest", JourneysTestTask::class.java) { task ->
            task.group = "verification"
            task.description =
                "Runs src/journeysTest/*.journey.xml against a connected device via the configured CLI agent."
            task.agentCommand.set(ext.agentCommand)
            task.prompt.set(ext.prompt)
            task.timeoutSeconds.set(ext.timeoutSeconds)
            task.journeysDir.set(ext.journeysDir)
            task.outputDir.set(ext.outputDir)
            task.reportsDir.set(ext.reportsDir)
            task.workingDir.set(project.layout.projectDirectory)
        }
    }
}
