package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Covers the draft → list → run → promote flow end to end, through a real Gradle build. */
class JourneysDraftFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val drafts get() = File(projectDir, "build/journeys/drafts")
    private val journeys get() = File(projectDir, "src/journeysTest")

    @BeforeEach
    fun setUp() {
        write("settings.gradle.kts", """rootProject.name = "drafting"""")
    }

    @Test
    fun `drafts land under build, never in the journeys source directory`() {
        givenAgentThatDrafts("login")

        val result = runner("journeysDraft").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":journeysDraft")?.outcome)
        assertTrue(File(drafts, "login.journey.xml").isFile, "draft should be written under build/")
        assertFalse(journeys.exists(), "drafting must not touch the journeys source directory")
        assertTrue(result.output.contains("Drafted 1 journey"), result.output)
    }

    @Test
    fun `journeysTest ignores drafts until they are promoted`() {
        givenAgentThatDrafts("login")
        runner("journeysDraft").build()

        // Nothing committed yet, so the normal run finds no journeys at all.
        val plain = runner("journeysTest").build()
        assertTrue(plain.output.contains("No journeys found"), plain.output)

        // ...but they can be run in place.
        val drafted = runner("journeysTest", "--drafts").build()
        assertEquals(TaskOutcome.SUCCESS, drafted.task(":journeysTest")?.outcome)
        assertTrue(File(projectDir, "build/journeys/login.verdict.json").isFile)
    }

    @Test
    fun `listing shows each draft and the result it last got`() {
        givenAgentThatDrafts("login")
        runner("journeysDraft").build()
        runner("journeysTest", "--drafts").build()

        val result = runner("journeysDraftList").build()

        assertTrue(result.output.contains("login"), result.output)
        assertTrue(result.output.contains("2 actions"), result.output)
        assertTrue(result.output.contains("PASSED"), result.output)
    }

    @Test
    fun `promoting moves the draft into the journeys source directory`() {
        givenAgentThatDrafts("login")
        runner("journeysDraft").build()

        val result = runner("journeysDraftPromote", "--draft=login").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":journeysDraftPromote")?.outcome)
        assertTrue(File(journeys, "login.journey.xml").isFile, "promoted journey should be a real test")
        assertFalse(File(drafts, "login.journey.xml").exists(), "the draft should be gone once promoted")
    }

    @Test
    fun `promoting refuses to overwrite an existing journey unless forced`() {
        givenAgentThatDrafts("login")
        runner("journeysDraft").build()
        write("src/journeysTest/login.journey.xml", """<journey name="login"><actions /></journey>""")

        val failure = runner("journeysDraftPromote", "--draft=login").buildAndFail()
        assertTrue(failure.output.contains("already exists"), failure.output)

        runner("journeysDraftPromote", "--draft=login", "--force").build()
        assertTrue(File(journeys, "login.journey.xml").readText().contains("<action>"))
    }

    @Test
    fun `promoting explains itself when the draft is not there`() {
        givenAgentThatDrafts("login")
        runner("journeysDraft").build()

        val failure = runner("journeysDraftPromote", "--draft=nope").buildAndFail()

        assertTrue(failure.output.contains("No draft named 'nope'"), failure.output)
        assertTrue(failure.output.contains("login"), "should list what is available")
    }

    /** An agent that writes one draft into whatever directory the prompt names. */
    private fun givenAgentThatDrafts(name: String) {
        val agent =
            write(
                "agent.sh",
                """
                dir=${'$'}(printf '%s' "${'$'}1" | grep -oE '/[^[:space:]]+/drafts' | head -1)
                mkdir -p "${'$'}dir"
                cat > "${'$'}dir/$name.journey.xml" <<'XML'
                <journey name="$name">
                  <description>drafted</description>
                  <actions>
                    <action>Launch the app</action>
                    <action>Verify the home screen is shown</action>
                  </actions>
                </journey>
                XML
                echo "wrote $name"
                """.trimIndent(),
            )
        // The same script answers a journey run with a passing verdict.
        val runAgent =
            write(
                "run-agent.sh",
                """echo '<<<VERDICT>>>{"journey":"$name","results":[""" +
                    """{"action":"a","status":"PASSED"},{"action":"b","status":"PASSED"}]}<<<END>>>'""",
            )
        write(
            "build.gradle.kts",
            """
            plugins { id("io.github.fornewid.journeys-test") }
            journeys {
                agentCommand.set(
                    if (gradle.startParameter.taskNames.any { it.contains("Draft") }) {
                        "bash ${agent.absolutePath}"
                    } else {
                        "bash ${runAgent.absolutePath}"
                    },
                )
            }
            """.trimIndent(),
        )
    }

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
