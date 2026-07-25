package io.github.fornewid.journeys.engine

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class CliJourneyRunnerTest {

    @Test
    fun `quotes the journey placeholder as one shell argument`() {
        val journeyDir = Files.createTempDirectory("journey path ")
        val journey = journeyDir.resolve("quoted journey.journey.xml")
        journey.writeText("""<journey name="quoted"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty(
                "journey.agent.cmd",
                """EXPECTED="${journey.toAbsolutePath()}" bash -c 'status=FAILED; [ "${'$'}1" = "${'$'}EXPECTED" ] && status=PASSED; printf "<<<VERDICT>>>{\"journey\":\"quoted\",\"results\":[{\"action\":\"path\",\"status\":\"%s\"}]}<<<END>>>\n" "${'$'}status"' -- {journey}""",
            )
            System.setProperty("journey.agent.timeoutSec", "5")

            val verdict = CliJourneyRunner.run(journey.toFile())

            assertTrue(verdict.allPassed)
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
            Files.deleteIfExists(journeyDir)
        }
    }

    @Test
    fun `cleans up a background child that inherits the output streams`() {
        val journey = Files.createTempFile("inherited-stream", ".journey.xml")
        journey.writeText("""<journey name="inherited-stream"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty(
                "journey.agent.cmd",
                """bash -c 'sleep 10 & child=${'$'}!; echo "<<<VERDICT>>>{\"journey\":\"inherited-stream\",\"results\":[{\"action\":\"done\",\"status\":\"PASSED\",\"reasoning\":\"${'$'}child\"}]}<<<END>>>"' --""",
            )
            System.setProperty("journey.agent.timeoutSec", "3")

            lateinit var verdict: Verdict
            val elapsed = measureTime {
                verdict = CliJourneyRunner.run(journey.toFile())
            }

            assertTrue(verdict.allPassed)
            val childPid = requireNotNull(verdict.results.single().reasoning).toLong()
            assertFalse(
                ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false),
                "background child $childPid survived agent completion",
            )
            assertTrue(elapsed < 3.seconds, "background cleanup took $elapsed")
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
        }
    }

    @Test
    fun `accepts a verdict wrapped in a markdown json fence`() {
        val journey = Files.createTempFile("fenced", ".journey.xml")
        journey.writeText("""<journey name="fenced"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty(
                "journey.agent.cmd",
                """bash -c 'printf "<<<VERDICT>>>\n\140\140\140json\n{\"journey\":\"fenced\",\"results\":[{\"action\":\"done\",\"status\":\"PASSED\"}]}\n\140\140\140\n<<<END>>>\n"' --""",
            )
            System.setProperty("journey.agent.timeoutSec", "5")

            val verdict = CliJourneyRunner.run(journey.toFile())

            assertTrue(verdict.allPassed)
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
        }
    }

    @Test
    fun `parses the verdict from stdout without matching prompt markers in stderr`() {
        val journey = Files.createTempFile("stderr", ".journey.xml")
        journey.writeText("""<journey name="stderr"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty(
                "journey.agent.cmd",
                """bash -c 'echo "between <<<VERDICT>>> and <<<END>>>" >&2; echo "<<<VERDICT>>>{\"journey\":\"stdout\",\"results\":[{\"action\":\"done\",\"status\":\"PASSED\"}]}<<<END>>>"' --""",
            )
            System.setProperty("journey.agent.timeoutSec", "5")

            val verdict = CliJourneyRunner.run(journey.toFile())

            assertTrue(verdict.allPassed)
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
        }
    }

    @Test
    fun `closes agent stdin after passing the prompt as an argument`() {
        val journey = Files.createTempFile("stdin", ".journey.xml")
        journey.writeText("""<journey name="stdin"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty(
                "journey.agent.cmd",
                """bash -c 'cat >/dev/null; echo "<<<VERDICT>>>{\"journey\":\"stdin\",\"results\":[{\"action\":\"done\",\"status\":\"PASSED\"}]}<<<END>>>"' --""",
            )
            System.setProperty("journey.agent.timeoutSec", "5")

            val verdict = CliJourneyRunner.run(journey.toFile())

            assertTrue(verdict.allPassed)
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
        }
    }

    @Test
    fun `terminates an agent that exceeds the configured timeout`() {
        val journey = Files.createTempFile("timeout", ".journey.xml")
        journey.writeText("""<journey name="timeout"><actions /></journey>""")
        val previousCommand = System.getProperty("journey.agent.cmd")
        val previousTimeout = System.getProperty("journey.agent.timeoutSec")

        try {
            System.setProperty("journey.agent.cmd", "bash -c 'echo agent-started; sleep 10' --")
            System.setProperty("journey.agent.timeoutSec", "3")

            val elapsed = measureTime {
                val error = assertFailsWith<IllegalStateException> {
                    CliJourneyRunner.run(journey.toFile())
                }
                assertTrue(error.message.orEmpty().contains("did not finish within 3s"))
                assertTrue(error.message.orEmpty().contains("agent-started"))
            }

            assertTrue(elapsed < 6.seconds, "timeout took $elapsed")
        } finally {
            restoreProperty("journey.agent.cmd", previousCommand)
            restoreProperty("journey.agent.timeoutSec", previousTimeout)
            Files.deleteIfExists(journey)
        }
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
