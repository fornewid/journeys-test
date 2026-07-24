package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import java.io.File

/**
 * JUnit Platform TestEngine that discovers `*.journey.xml` and runs each as one test,
 * delegating execution to [CliJourneyRunner]. Configured via [EngineConfig].
 */
class JourneyTestEngine : TestEngine {

    override fun getId(): String = ENGINE_ID

    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val root = EngineDescriptor(uniqueId, "Journeys Test")
        val dir = File(EngineConfig.journeysDir())
        if (dir.isDirectory) {
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".journey.xml") }
                .sortedBy { it.name }
                .forEach { file ->
                    val name = file.name.removeSuffix(".journey.xml")
                    root.addChild(JourneyDescriptor(uniqueId.append("journey", name), name, file))
                }
        }
        return root
    }

    override fun execute(request: ExecutionRequest) {
        val root = request.rootTestDescriptor
        val listener = request.engineExecutionListener
        val outDir = File(EngineConfig.outputDir()).apply { mkdirs() }
        val verdicts = mutableListOf<Verdict>()

        listener.executionStarted(root)
        root.children.filterIsInstance<JourneyDescriptor>().forEach { jd ->
            listener.executionStarted(jd)
            val result = try {
                val verdict = CliJourneyRunner.run(jd.file)
                verdicts += verdict
                outDir.resolve("${jd.displayName}.verdict.json").writeText(prettyJson.encodeToString(verdict))
                if (verdict.allPassed) TestExecutionResult.successful()
                else TestExecutionResult.failed(AssertionError("Journey '${verdict.journey}' FAILED:\n${verdict.report()}"))
            } catch (t: Throwable) {
                TestExecutionResult.failed(t)
            }
            listener.executionFinished(jd, result)
        }
        if (verdicts.isNotEmpty()) ResultReport.write(verdicts, outDir.resolve("report.html"))
        listener.executionFinished(root, TestExecutionResult.successful())
    }

    private class JourneyDescriptor(id: UniqueId, name: String, val file: File) :
        AbstractTestDescriptor(id, name, FileSource.from(file)) {
        override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    }

    companion object {
        const val ENGINE_ID = "journeys-test"
        private val prettyJson = Json { prettyPrint = true }
    }
}
