package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyLauncher
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The `io.github.fornewid.journeys-test` plugin.
 *
 * Registers a `journeysTest` task that discovers journey XML under `src/journeysTest`, runs each
 * via the configured agent, and writes a JUnit XML report (build/journey-results) plus verdict
 * JSON and an HTML report (build/journeys). Folder, extension, and task name match Android
 * Studio's managed Journeys, so the same project is shared between both — no module to reimplement.
 *
 * ```kotlin
 * plugins { id("io.github.fornewid.journeys-test") }
 * journeys { agentCommand = "claude --no-session-persistence --allowedTools 'Bash(adb *)' -p" }
 * ```
 */
class JourneysPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("journeys", JourneysExtension::class.java).apply {
            journeysDir.convention(project.layout.projectDirectory.dir("src/journeysTest"))
            outputDir.convention(project.layout.buildDirectory.dir("journeys"))
            reportsDir.convention(project.layout.buildDirectory.dir("journey-results"))
            timeoutSeconds.convention(900L)
        }

        // capture the individual providers, not the whole extension, so execution never reaches
        // back into the live Project
        val agentCommand = ext.agentCommand
        val prompt = ext.prompt
        val journeysDir = ext.journeysDir
        val outputDir = ext.outputDir
        val reportsDir = ext.reportsDir
        val timeoutSeconds = ext.timeoutSeconds

        project.tasks.register("journeysTest") { task ->
            task.group = "verification"
            task.description = "Runs src/journeysTest/*.journey.xml against a connected device via the configured CLI agent."
            task.outputs.upToDateWhen { false } // always run: depends on live device state
            task.doLast {
                // pass config to the engine, run the JUnit Platform launcher in-process, then clean up
                val props = buildMap {
                    agentCommand.orNull?.let { put("journey.agent.cmd", it) }
                    prompt.orNull?.let { put("journey.agent.prompt", it) }
                    put("journeys.dir", journeysDir.get().asFile.absolutePath)
                    put("journeys.out", outputDir.get().asFile.absolutePath)
                    put("journeys.reports", reportsDir.get().asFile.absolutePath)
                    put("journey.agent.timeoutSec", timeoutSeconds.get().toString())
                }
                props.forEach { (k, v) -> System.setProperty(k, v) }
                val previousCl = Thread.currentThread().contextClassLoader
                try {
                    Thread.currentThread().contextClassLoader = JourneysPlugin::class.java.classLoader
                    val failures = JourneyLauncher.run()
                    if (failures > 0) {
                        throw GradleException(
                            "$failures journey(s) failed. See build/journey-results (JUnit XML) and build/journeys/report.html.",
                        )
                    }
                } finally {
                    Thread.currentThread().contextClassLoader = previousCl
                    props.keys.forEach(System::clearProperty)
                }
            }
        }
    }
}
