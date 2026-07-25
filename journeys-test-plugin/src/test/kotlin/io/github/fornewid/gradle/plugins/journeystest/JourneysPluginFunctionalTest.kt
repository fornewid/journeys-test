package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Drives a real Gradle build, so the outcome a consumer sees is what gets asserted. */
class JourneysPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        write("settings.gradle.kts", """rootProject.name = "functional"""")
    }

    @Test
    fun `fails the build and records a JUnit failure when a journey fails`() {
        givenProject(verdict = """{"journey":"login","results":[{"action":"a","status":"FAILED","comment":"nope"}]}""")

        val result = runner("journeysTest").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":journeysTest")?.outcome)
        assertTrue(result.output.contains("journey(s) failed"), result.output)
        assertTrue(junitXml().contains("<failure"), junitXml())
    }

    @Test
    fun `passes and writes the reports when every action passed`() {
        givenProject(verdict = """{"journey":"login","results":[{"action":"a","status":"PASSED"}]}""")

        val result = runner("journeysTest").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":journeysTest")?.outcome)
        assertTrue(junitXml().contains("tests=\"1\""), junitXml())
        assertTrue(File(projectDir, "build/journeys/login.verdict.json").isFile)
        assertTrue(File(projectDir, "build/journeys/report.html").isFile)
    }

    @Test
    fun `reruns with the configuration cache`() {
        givenProject(verdict = """{"journey":"login","results":[{"action":"a","status":"PASSED"}]}""")

        runner("journeysTest", "--configuration-cache").build()
        val second = runner("journeysTest", "--configuration-cache").build()

        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
        assertEquals(TaskOutcome.SUCCESS, second.task(":journeysTest")?.outcome)
    }

    @Test
    fun `explains what to configure when no agent command is set`() {
        givenProject(verdict = "unused", configureAgent = false)

        val result = runner("journeysTest").buildAndFail()

        assertTrue(result.output.contains("No agent command configured"), result.output)
    }

    private fun givenProject(
        verdict: String,
        configureAgent: Boolean = true,
    ) {
        val agent = write("agent.sh", "echo '<<<VERDICT>>>$verdict<<<END>>>'")
        val journeys =
            if (configureAgent) {
                """journeys { agentCommand.set("bash ${agent.absolutePath}") }"""
            } else {
                // explicitly clear it, so an ambient JOURNEY_AGENT_CMD cannot leak in
                """journeys { agentCommand.set(null as String?) }"""
            }
        write(
            "build.gradle.kts",
            """
            plugins { id("io.github.fornewid.journeys-test") }
            $journeys
            """.trimIndent(),
        )
        write("src/journeysTest/login.journey.xml", """<journey name="login"/>""")
    }

    private fun junitXml(): String =
        File(projectDir, "build/journey-results")
            .listFiles()
            .orEmpty()
            .single()
            .readText()

    private fun write(
        path: String,
        text: String,
    ): File =
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(text)
        }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
            .forwardOutput()
}
