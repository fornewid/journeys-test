package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyConfig
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
import java.io.File

/**
 * Moves a draft out of `build/journeys/drafts` into the journeys source directory, turning it into
 * a real test. Moving rather than copying: a promoted draft is no longer a draft, and `build/` is
 * cleanable anyway.
 */
@UntrackedTask(because = "Moves files that another task wrote during the same build")
abstract class JourneysDraftPromoteTask : DefaultTask() {
    /** Key of the draft to promote: its path under the drafts dir, without the suffix. */
    @get:Input
    @get:Optional
    @get:Option(option = "draft", description = "Key of the draft to promote (path under the drafts dir, without .journey.xml)")
    abstract val draft: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "all", description = "Promote every draft")
    abstract val all: Property<Boolean>

    @get:Input
    @get:Optional
    @get:Option(option = "force", description = "Overwrite a journey that already exists")
    abstract val force: Property<Boolean>

    @get:Internal
    abstract val draftsDir: DirectoryProperty

    @get:Internal
    abstract val journeysDir: DirectoryProperty

    @TaskAction
    fun promote() {
        val drafts = draftsDir.get().asFile
        val destination = journeysDir.get().asFile
        val selected = select(drafts)

        selected.forEach { source ->
            val relative = source.toRelativeString(drafts)
            val target = destination.resolve(relative)
            if (target.exists() && !force.getOrElse(false)) {
                throw GradleException("$target already exists. Re-run with --force to overwrite it.")
            }
            target.parentFile?.mkdirs()
            target.writeText(source.readText())
            source.delete()
            logger.lifecycle("Promoted $relative -> $target")
        }
        logger.lifecycle("Promoted ${selected.size} draft(s). Run them with: ./gradlew ${path.replace("DraftPromote", "Test")}")
    }

    private fun select(drafts: File): List<File> {
        val available =
            drafts
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(JourneyConfig.JOURNEY_FILE_SUFFIX) }
                .sortedBy { it.path }
                .toList()
        if (available.isEmpty()) throw GradleException("No drafts in $drafts.")

        if (all.getOrElse(false)) return available
        val key =
            draft.orNull
                ?: throw GradleException(
                    "Name the draft to promote with --draft=<key>, or pass --all. Available: " +
                        available.joinToString { it.toRelativeString(drafts).removeSuffix(JourneyConfig.JOURNEY_FILE_SUFFIX) },
                )
        val source = drafts.resolve("$key${JourneyConfig.JOURNEY_FILE_SUFFIX}")
        if (!source.isFile) {
            throw GradleException(
                "No draft named '$key'. Available: " +
                    available.joinToString { it.toRelativeString(drafts).removeSuffix(JourneyConfig.JOURNEY_FILE_SUFFIX) },
            )
        }
        return listOf(source)
    }
}
