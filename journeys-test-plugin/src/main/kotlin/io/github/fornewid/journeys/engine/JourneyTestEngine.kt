package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.UniqueIdSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import java.io.File

/**
 * JUnit Platform TestEngine that discovers `*.journey.xml` and runs each as one test,
 * delegating execution to [CliJourneyRunner]. Its configuration travels on the request
 * (see [JourneyConfig]), so nothing is read from or written to global state.
 */
class JourneyTestEngine : TestEngine {
    override fun getId(): String = ENGINE_ID

    override fun discover(
        request: EngineDiscoveryRequest,
        uniqueId: UniqueId,
    ): TestDescriptor {
        val root = EngineDescriptor(uniqueId, "Journeys Test")
        val dir = JourneyConfig.from(request.configurationParameters).journeysDir
        if (dir.isDirectory) {
            dir
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(JourneyConfig.JOURNEY_FILE_SUFFIX) }
                // key by path relative to the journeys dir, so same-named files in different
                // directories stay distinct
                .map { file -> keyOf(dir, file) to file }
                .sortedBy { (key, _) -> key }
                .forEach { (key, file) ->
                    root.addChild(JourneyDescriptor(uniqueId.append(SEGMENT_TYPE, key), key, file))
                }
        }
        selectOnly(root, request)
        return root
    }

    override fun execute(request: ExecutionRequest) {
        val root = request.rootTestDescriptor
        val listener = request.engineExecutionListener
        val config = JourneyConfig.from(request.configurationParameters)
        val verdicts = mutableListOf<Verdict>()

        listener.executionStarted(root)
        root.children.filterIsInstance<JourneyDescriptor>().forEach { journey ->
            listener.executionStarted(journey)
            val result =
                try {
                    val verdict = CliJourneyRunner.run(journey.file, journey.key, config)
                    verdicts += verdict
                    config.outputDir
                        .resolve("${journey.key}.verdict.json")
                        .apply { parentFile?.mkdirs() }
                        .writeText(prettyJson.encodeToString(verdict))
                    if (verdict.allPassed) {
                        TestExecutionResult.successful()
                    } else {
                        TestExecutionResult.failed(
                            AssertionError("Journey '${verdict.journey}' FAILED:\n${verdict.report()}"),
                        )
                    }
                } catch (t: Throwable) {
                    TestExecutionResult.failed(t)
                }
            listener.executionFinished(journey, result)
        }
        if (verdicts.isNotEmpty()) {
            ResultReport.write(verdicts, config.outputDir.resolve("report.html"), config.workingDir)
        }
        listener.executionFinished(root, TestExecutionResult.successful())
    }

    /** Keeps only the journeys named by unique-id selectors, so a single journey can be re-run. */
    private fun selectOnly(
        root: EngineDescriptor,
        request: EngineDiscoveryRequest,
    ) {
        val selected = request.getSelectorsByType(UniqueIdSelector::class.java).map { it.uniqueId }
        if (selected.isEmpty()) return
        root.children
            .toList()
            .filterNot { child -> selected.any { it == child.uniqueId || it.hasPrefix(child.uniqueId) } }
            .forEach(root::removeChild)
    }

    private fun keyOf(
        dir: File,
        file: File,
    ): String = file.relativeTo(dir).invariantSeparatorsPath.removeSuffix(JourneyConfig.JOURNEY_FILE_SUFFIX)

    private class JourneyDescriptor(
        id: UniqueId,
        val key: String,
        val file: File,
    ) : AbstractTestDescriptor(id, key, FileSource.from(file)) {
        override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    }

    companion object {
        const val ENGINE_ID = "journeys-test"
        const val SEGMENT_TYPE = "journey"
        private val prettyJson = Json { prettyPrint = true }
    }
}
