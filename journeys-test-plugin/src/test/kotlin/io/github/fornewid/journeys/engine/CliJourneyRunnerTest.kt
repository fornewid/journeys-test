package io.github.fornewid.journeys.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Covers the agent-facing failure paths, which a passing journey never exercises. */
class CliJourneyRunnerTest {
    @TempDir
    lateinit var tmp: File

    private var scripts = 0

    private fun journeyFile(dir: File = tmp): File =
        File(dir, "login.journey.xml").apply {
            parentFile.mkdirs()
            writeText("""<journey name="login"><actions /></journey>""")
        }

    private fun config(
        agentCommand: String,
        timeoutSeconds: Long = 30L,
    ) = JourneyConfig(
        journeysDir = tmp,
        outputDir = File(tmp, "out"),
        reportsDir = File(tmp, "reports"),
        workingDir = tmp,
        agentCommand = agentCommand,
        timeoutSeconds = timeoutSeconds,
    )

    /** Writes an agent script and returns the command that runs it. */
    private fun agent(script: String): String =
        "bash " +
            File(tmp, "agent-${scripts++}.sh")
                .apply { writeText(script) }
                .absolutePath

    private val passingVerdict =
        """{"journey":"login","results":[{"action":"a","status":"PASSED"}]}"""

    @Test
    fun `takes the last verdict block, not the echoed prompt`() {
        // The default prompt names both markers, so an agent that echoes it plants a decoy.
        val command =
            agent(
                """
                echo "print the verdict between the markers <<<VERDICT>>> and <<<END>>>, with fields"
                echo '<<<VERDICT>>>$passingVerdict<<<END>>>'
                """.trimIndent(),
            )

        val verdict = CliJourneyRunner.run(journeyFile(), "login", config(command))

        assertEquals("login", verdict.journey)
        assertTrue(verdict.allPassed)
    }

    @Test
    fun `reads the verdict from stdout and ignores markers written to stderr`() {
        val command =
            agent(
                """
                echo "between <<<VERDICT>>> and <<<END>>>" >&2
                echo '<<<VERDICT>>>$passingVerdict<<<END>>>'
                """.trimIndent(),
            )

        assertTrue(CliJourneyRunner.run(journeyFile(), "login", config(command)).allPassed)
    }

    @Test
    fun `accepts a verdict wrapped in a markdown json fence`() {
        val command =
            agent(
                """
                echo '<<<VERDICT>>>'
                echo '```json'
                echo '$passingVerdict'
                echo '```'
                echo '<<<END>>>'
                """.trimIndent(),
            )

        assertTrue(CliJourneyRunner.run(journeyFile(), "login", config(command)).allPassed)
    }

    @Test
    fun `fails when the agent exits non-zero even if it printed a verdict`() {
        val command =
            agent(
                """
                echo '<<<VERDICT>>>$passingVerdict<<<END>>>'
                exit 3
                """.trimIndent(),
            )

        val error =
            assertThrows<IllegalStateException> {
                CliJourneyRunner.run(journeyFile(), "login", config(command))
            }
        assertTrue(error.message!!.contains("exited with code 3"), error.message)
    }

    @Test
    fun `times out instead of hanging when the agent never finishes`() {
        val error =
            assertThrows<IllegalStateException> {
                CliJourneyRunner.run(journeyFile(), "login", config(agent("sleep 30"), timeoutSeconds = 1L))
            }
        assertTrue(error.message!!.contains("did not finish within 1s"), error.message)
    }

    @Test
    fun `kills the whole process group so background children do not survive`() {
        // The child inherits stdout, so leaving it alive would also block the reader.
        val command =
            agent(
                """
                sleep 30 &
                child=${'$'}!
                echo "<<<VERDICT>>>{\"journey\":\"login\",\"results\":[{\"action\":\"a\",\"status\":\"PASSED\",\"reasoning\":\"${'$'}child\"}]}<<<END>>>"
                """.trimIndent(),
            )

        val verdict = CliJourneyRunner.run(journeyFile(), "login", config(command, timeoutSeconds = 10L))

        val childPid =
            verdict.results
                .single()
                .reasoning!!
                .trim()
                .toLong()
        assertFalse(
            ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false),
            "background child $childPid survived the run",
        )
    }

    @Test
    fun `closes the agent's stdin so it never waits for input`() {
        val command =
            agent(
                """
                if read -r _; then state=OPEN; else state=CLOSED; fi
                echo "<<<VERDICT>>>{\"journey\":\"login\",\"results\":[{\"action\":\"a\",\"status\":\"PASSED\",\"reasoning\":\"${'$'}state\"}]}<<<END>>>"
                """.trimIndent(),
            )

        val verdict = CliJourneyRunner.run(journeyFile(), "login", config(command, timeoutSeconds = 10L))

        assertEquals(
            "CLOSED",
            verdict.results
                .single()
                .reasoning
                ?.trim(),
        )
    }

    @Test
    fun `substitutes the journey path as one argument even when it contains spaces`() {
        val journey = journeyFile(File(tmp, "my journeys"))
        val received = File(tmp, "received-path.txt")
        val command =
            agent(
                """
                printf '%s' "${'$'}1" > "${received.absolutePath}"
                echo '<<<VERDICT>>>$passingVerdict<<<END>>>'
                """.trimIndent(),
            )

        CliJourneyRunner.run(journey, "login", config("$command {journey}"))

        assertEquals(journey.absolutePath, received.readText())
    }

    @Test
    fun `keeps the agent log when the run fails`() {
        val error =
            assertThrows<IllegalStateException> {
                CliJourneyRunner.run(journeyFile(), "login", config(agent("echo nothing useful")))
            }

        assertTrue(error.message!!.contains("no verdict block found"), error.message)
        assertTrue(File(tmp, "out/login.agent.log").isFile, "agent log should be kept for debugging")
    }
}
