package io.github.fornewid.gradle.plugins.journeystest

import io.github.fornewid.journeys.engine.JourneyConfig
import io.github.fornewid.journeys.engine.JourneyJson
import io.github.fornewid.journeys.engine.Verdict
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

/** Lists the drafts waiting in `build/journeys/drafts`, with the last result each one got. */
@UntrackedTask(because = "Reports on files that other tasks write during the same build")
abstract class JourneysDraftListTask : DefaultTask() {
    @get:Internal
    abstract val draftsDir: DirectoryProperty

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun list() {
        val drafts = draftsDir.get().asFile
        val files =
            drafts
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(JourneyConfig.JOURNEY_FILE_SUFFIX) }
                .sortedBy { it.path }
                .toList()

        if (files.isEmpty()) {
            logger.lifecycle("No drafts in $drafts. Write some with: ./gradlew ${path.replace("DraftList", "Draft")}")
            return
        }

        logger.lifecycle("Drafts in $drafts (${files.size})")
        val width = files.maxOf { key(drafts, it).length }
        files.forEach { file ->
            val key = key(drafts, file)
            logger.lifecycle("  ${key.padEnd(width)}  ${actionCount(file)}  ${lastResult(key)}")
        }
        logger.lifecycle("")
        logger.lifecycle("Run them:  ./gradlew ${path.replace("DraftList", "Test")} --drafts")
        logger.lifecycle("Promote:   ./gradlew ${path.replace("List", "Promote")} --draft=${key(drafts, files.first())}")
    }

    private fun key(
        drafts: File,
        file: File,
    ): String = file.toRelativeString(drafts).removeSuffix(JourneyConfig.JOURNEY_FILE_SUFFIX)

    private fun actionCount(file: File): String {
        val count = ACTION.findAll(file.readText()).count()
        return "$count action${if (count == 1) "" else "s"}".padEnd(10)
    }

    /** The verdict a previous run left for this draft, if any. */
    private fun lastResult(key: String): String {
        val verdictFile = outputDir.get().asFile.resolve("$key.verdict.json")
        if (!verdictFile.isFile) return "not run"
        return runCatching {
            val verdict = JourneyJson.lenient.decodeFromString<Verdict>(verdictFile.readText())
            if (verdict.allPassed) "PASSED" else "FAILED (${verdict.passedCount}/${verdict.results.size})"
        }.getOrDefault("unreadable verdict")
    }

    private companion object {
        val ACTION = Regex("<action>", RegexOption.IGNORE_CASE)
    }
}
