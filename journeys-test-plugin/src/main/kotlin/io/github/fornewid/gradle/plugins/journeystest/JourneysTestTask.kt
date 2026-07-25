package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyConfig
import io.github.fornewid.journeys.engine.JourneyLauncher
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

/**
 * Runs the project's journeys through the configured CLI agent.
 *
 * Every value the engine needs is passed explicitly, so the task never mutates JVM-wide state
 * and parallel runs in a multi-module build stay independent.
 */
@UntrackedTask(because = "Journeys run against live device state, so they must re-run every build")
abstract class JourneysTestTask : DefaultTask() {
    /** Agent CLI invocation, without a prompt; the plugin appends [prompt]. */
    @get:Input
    @get:Optional
    abstract val agentCommand: Property<String>

    /** Instruction sent to the agent; `{journey}` becomes the journey file's absolute path. */
    @get:Input
    abstract val prompt: Property<String>

    @get:Input
    abstract val timeoutSeconds: Property<Long>

    /** Run only this journey: its path under the journeys dir, without the `.journey.xml` suffix. */
    @get:Input
    @get:Optional
    @get:Option(option = "journey", description = "Run only this journey (path under the journeys dir, without .journey.xml)")
    abstract val journey: Property<String>

    @get:Internal
    abstract val journeysDir: DirectoryProperty

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val reportsDir: DirectoryProperty

    /** Directory the agent runs in, and the base for resolving relative artifact paths. */
    @get:Internal
    abstract val workingDir: DirectoryProperty

    @TaskAction
    fun runJourneys() {
        val output = outputDir.get().asFile
        val reports = reportsDir.get().asFile
        val failures =
            JourneyLauncher.run(
                JourneyConfig(
                    journeysDir = journeysDir.get().asFile,
                    outputDir = output,
                    reportsDir = reports,
                    workingDir = workingDir.get().asFile,
                    agentCommand = agentCommand.orNull,
                    prompt = prompt.get(),
                    timeoutSeconds = timeoutSeconds.get(),
                ),
                journeyFilter = journey.orNull,
            )
        if (failures > 0) {
            throw GradleException(
                "$failures journey(s) failed. See $reports (JUnit XML) and ${output.resolve("report.html")}.",
            )
        }
    }
}
