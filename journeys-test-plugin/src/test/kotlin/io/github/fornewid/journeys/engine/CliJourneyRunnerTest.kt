package io.github.fornewid.journeys.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Covers the agent-facing failure paths, which a passing journey never exercises. */
class CliJourneyRunnerTest {
    @TempDir
    lateinit var tmp: File

    private fun journeyFile(): File =
        File(tmp, "login.journey.xml").apply {
            writeText("<journey name=\"login\"><actions><action>Tap login</action></actions></journey>")
        }

    private fun config(
        agentCommand: String,
        timeoutSeconds: Long = 60L,
    ) = JourneyConfig(
        journeysDir = tmp,
        outputDir = File(tmp, "out"),
        reportsDir = File(tmp, "reports"),
        workingDir = tmp,
        agentCommand = agentCommand,
        timeoutSeconds = timeoutSeconds,
    )

    private fun agent(script: String): String =
        "bash " +
            File(tmp, "agent.sh").apply { writeText(script) }.absolutePath

    @Test
    fun `takes the last verdict block, not the echoed prompt`() {
        // The default prompt names both markers, so an agent that echoes it plants a decoy.
        val command =
            agent(
                """
                echo "print the verdict between the markers <<<VERDICT>>> and <<<END>>>, with fields"
                echo '<<<VERDICT>>>'
                echo '{"journey":"login","results":[{"action":"Tap login","status":"PASSED"}]}'
                echo '<<<END>>>'
                """.trimIndent(),
            )

        val verdict = CliJourneyRunner.run(journeyFile(), "login", config(command))

        assertEquals("login", verdict.journey)
        assertTrue(verdict.allPassed)
    }

    @Test
    fun `fails when the agent exits non-zero even if it printed a verdict`() {
        val command =
            agent(
                """
                echo '<<<VERDICT>>>{"journey":"login","results":[{"action":"a","status":"PASSED"}]}<<<END>>>'
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
    fun `reports the log path when no verdict is printed`() {
        val error =
            assertThrows<IllegalStateException> {
                CliJourneyRunner.run(journeyFile(), "login", config(agent("echo nothing useful")))
            }
        assertTrue(error.message!!.contains("no verdict block found"), error.message)
        assertTrue(File(tmp, "out/login.agent.log").isFile, "agent log should be kept for debugging")
    }

    @Test
    fun `substitutes the journey path as one argument even when it contains spaces`() {
        val dir = File(tmp, "my journeys").apply { mkdirs() }
        val journey = File(dir, "login.journey.xml").apply { writeText("<journey name=\"login\"/>") }
        val script =
            agent(
                """
                printf '%s' "${'$'}1" > "${File(tmp, "received-path.txt").absolutePath}"
                echo '<<<VERDICT>>>{"journey":"login","results":[{"action":"a","status":"PASSED"}]}<<<END>>>'
                """.trimIndent(),
            )

        CliJourneyRunner.run(journey, "login", config("$script {journey}"))

        assertEquals(journey.absolutePath, File(tmp, "received-path.txt").readText())
    }
}
