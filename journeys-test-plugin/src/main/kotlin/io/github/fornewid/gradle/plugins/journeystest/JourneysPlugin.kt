package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyConfig
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The `io.github.fornewid.journeys-test` plugin.
 *
 * Registers `journeysTest`, which discovers journey XML under `src/journeysTest`, runs each via the
 * configured agent, and writes a JUnit XML report (build/journey-results) plus verdict JSON and an
 * HTML report (build/journeys). Folder, extension, and task name match Android Studio's managed
 * Journeys, so the same project is shared between both.
 *
 * It also registers a drafting flow, so an agent can propose journeys for whatever just changed:
 * `journeysDraft` writes drafts to build/journeys/drafts, `journeysDraftList` shows them,
 * `journeysTest --drafts` runs them, and `journeysDraftPromote` moves one into `src/journeysTest`.
 * Drafts live under `build/` precisely so they never run as tests until a person promotes them.
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
        val draftsDir = ext.outputDir.dir(JourneyConfig.DRAFTS_DIR)

        project.tasks.register("journeysTest", JourneysTestTask::class.java) { task ->
            task.group = GROUP
            task.description =
                "Runs src/journeysTest/*.journey.xml against a connected device via the configured CLI agent."
            task.agentCommand.set(ext.agentCommand)
            task.prompt.set(ext.prompt)
            task.timeoutSeconds.set(ext.timeoutSeconds)
            task.journeysDir.set(ext.journeysDir)
            task.draftsDir.set(draftsDir)
            task.outputDir.set(ext.outputDir)
            task.reportsDir.set(ext.reportsDir)
            task.workingDir.set(project.layout.projectDirectory)
        }

        project.tasks.register("journeysDraft", JourneysDraftTask::class.java) { task ->
            task.group = GROUP
            task.description = "Asks the agent to draft journeys for what changed, into build/journeys/drafts."
            task.agentCommand.set(ext.agentCommand)
            task.timeoutSeconds.set(ext.timeoutSeconds)
            task.journeysDir.set(ext.journeysDir)
            task.draftsDir.set(draftsDir)
            task.outputDir.set(ext.outputDir)
            task.reportsDir.set(ext.reportsDir)
            task.workingDir.set(project.layout.projectDirectory)
        }

        project.tasks.register("journeysDraftList", JourneysDraftListTask::class.java) { task ->
            task.group = GROUP
            task.description = "Lists the drafts in build/journeys/drafts and how each one last did."
            task.draftsDir.set(draftsDir)
            task.outputDir.set(ext.outputDir)
        }

        project.tasks.register("journeysDraftPromote", JourneysDraftPromoteTask::class.java) { task ->
            task.group = GROUP
            task.description = "Moves a draft into src/journeysTest, turning it into a real journey."
            task.draftsDir.set(draftsDir)
            task.journeysDir.set(ext.journeysDir)
        }
    }

    private companion object {
        const val GROUP = "verification"
    }
}
