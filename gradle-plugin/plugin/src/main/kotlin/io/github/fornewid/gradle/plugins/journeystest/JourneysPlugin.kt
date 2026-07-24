package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.tasks.JavaExec

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
 * journeys { agentCommand = "claude -p ... {journey}" }
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

        // Run classpath for the engine. Declare standard runtime attributes explicitly since the
        // consuming project may not apply the java plugin (otherwise variant selection is ambiguous).
        val objects = project.objects
        val runner = project.configurations.create("journeysTestRuntime") { config ->
            config.isCanBeConsumed = false
            config.isCanBeResolved = true
            config.attributes { a ->
                a.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                a.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
                a.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
                a.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
                a.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
            }
        }
        project.dependencies.add(runner.name, "io.github.fornewid.journeys-test:journeys-test-engine:$ENGINE_VERSION")

        // capture the individual providers, not the whole extension, so execution never reaches
        // back into the live Project
        val agentCommand = ext.agentCommand
        val prompt = ext.prompt
        val journeysDir = ext.journeysDir
        val outputDir = ext.outputDir
        val reportsDir = ext.reportsDir
        val timeoutSeconds = ext.timeoutSeconds

        project.tasks.register("journeysTest", JavaExec::class.java) { task ->
            task.group = "verification"
            task.description = "Runs src/journeysTest/*.journey.xml against a connected device via the configured CLI agent."
            task.classpath = runner
            task.mainClass.set("io.github.fornewid.journeys.engine.JourneyLauncher")
            task.outputs.upToDateWhen { false } // always run: depends on live device state
            task.doFirst {
                agentCommand.orNull?.let { task.systemProperty("journey.agent.cmd", it) }
                prompt.orNull?.let { task.systemProperty("journey.agent.prompt", it) }
                task.systemProperty("journeys.dir", journeysDir.get().asFile.absolutePath)
                task.systemProperty("journeys.out", outputDir.get().asFile.absolutePath)
                task.systemProperty("journeys.reports", reportsDir.get().asFile.absolutePath)
                task.systemProperty("journey.agent.timeoutSec", timeoutSeconds.get().toString())
            }
        }
    }

    companion object {
        const val ENGINE_VERSION = "0.1.0"
    }
}
