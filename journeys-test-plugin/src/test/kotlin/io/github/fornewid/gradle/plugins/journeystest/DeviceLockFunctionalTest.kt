package io.github.fornewid.gradle.plugins.journeystest

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Journeys drive one connected device, so two modules must never run their agents at the same
 * time even when Gradle is building them in parallel.
 */
class DeviceLockFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `two modules never drive the device at the same time`() {
        val timeline = File(projectDir, "timeline.txt")
        // Each agent stamps when it starts and finishes; overlapping stamps mean both were
        // touching the device at once.
        val agent =
            write(
                "agent.sh",
                """
                printf '%s START %s\n' "${'$'}JOURNEY_MODULE" "${'$'}(date +%s.%N)" >> "${timeline.absolutePath}"
                sleep 2
                printf '%s END %s\n' "${'$'}JOURNEY_MODULE" "${'$'}(date +%s.%N)" >> "${timeline.absolutePath}"
                echo '<<<VERDICT>>>{"journey":"probe","results":[{"action":"a","status":"PASSED"}]}<<<END>>>'
                """.trimIndent(),
            )

        write("settings.gradle.kts", """rootProject.name = "probe"; include(":a", ":b")""")
        listOf("a", "b").forEach { module ->
            write(
                "$module/build.gradle.kts",
                """
                plugins { id("io.github.fornewid.journeys-test") }
                journeys { agentCommand.set("env JOURNEY_MODULE=$module bash ${agent.absolutePath}") }
                """.trimIndent(),
            )
            write(
                "$module/src/journeysTest/probe.journey.xml",
                """<journey name="probe"><actions><action>Launch the app</action></actions></journey>""",
            )
        }

        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("journeysTest", "--parallel", "--max-workers=4", "--stacktrace")
            .forwardOutput()
            .build()

        val stamps =
            timeline
                .readLines()
                .filter { it.isNotBlank() }
                .map { it.split(" ") }
                .groupBy({ it[0] }, { it[1] to it[2].toDouble() })
        assertEquals(2, stamps.size, "both modules should have run: ${timeline.readText()}")

        val spans =
            stamps.values.map { events ->
                events.toMap().let { it.getValue("START") to it.getValue("END") }
            }
        val (first, second) = spans.sortedBy { it.first }
        assertTrue(
            first.second <= second.first,
            "agents overlapped by ${first.second - second.first}s; they must not share the device",
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
}
