package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.DraftGenerator
import io.github.fornewid.journeys.engine.DraftScope
import io.github.fornewid.journeys.engine.JourneyConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

/**
 * Asks the agent to draft journeys into `build/journeys/drafts`.
 *
 * Drafts stay out of the journeys source directory on purpose: `journeysTest` never picks them up
 * by accident, and promoting one is a deliberate act (`journeysDraftPromote`).
 */
@UntrackedTask(because = "Drafting reads the working tree and the running app, so it must re-run every build")
abstract class JourneysDraftTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val agentCommand: Property<String>

    @get:Input
    abstract val timeoutSeconds: Property<Long>

    @get:Input
    abstract val deviceWaitSeconds: Property<Long>

    /** Draft for a change described in prose, when the code is not written yet. */
    @get:Input
    @get:Optional
    @get:Option(option = "about", description = "Draft journeys for a change described in prose")
    abstract val about: Property<String>

    /** Compare against this git ref instead of the working tree. */
    @get:Input
    @get:Optional
    @get:Option(option = "since", description = "Draft from the diff against this git ref (default: the working tree)")
    abstract val since: Property<String>

    /** Explore the running app instead of looking at changes. */
    @get:Input
    @get:Optional
    @get:Option(option = "explore", description = "Draft one smoke journey per screen by exploring the running app")
    abstract val explore: Property<Boolean>

    @get:Internal
    abstract val draftsDir: DirectoryProperty

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val reportsDir: DirectoryProperty

    @get:Internal
    abstract val journeysDir: DirectoryProperty

    @get:Internal
    abstract val workingDir: DirectoryProperty

    @TaskAction
    fun draft() {
        val drafts = draftsDir.get().asFile
        val created =
            DraftGenerator.generate(
                config =
                    JourneyConfig(
                        journeysDir = journeysDir.get().asFile,
                        outputDir = outputDir.get().asFile,
                        reportsDir = reportsDir.get().asFile,
                        workingDir = workingDir.get().asFile,
                        agentCommand = agentCommand.orNull,
                        timeoutSeconds = timeoutSeconds.get(),
                        deviceWaitSeconds = deviceWaitSeconds.get(),
                    ),
                draftsDir = drafts,
                scope = scope(),
            )

        if (created.isEmpty()) {
            logger.warn("No drafts were written to $drafts.")
            return
        }
        logger.lifecycle("Drafted ${created.size} journey(s) in $drafts:")
        created.forEach { logger.lifecycle("  ${it.toRelativeString(drafts)}") }
        logger.lifecycle("Run them with: ./gradlew ${path.replace("Draft", "Test")} --drafts")
    }

    private fun scope(): DraftScope =
        when {
            explore.getOrElse(false) -> DraftScope.Explore
            about.isPresent -> DraftScope.About(about.get())
            else -> DraftScope.Changes(since.orNull)
        }
}
