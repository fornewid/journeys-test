package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VerdictTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun `parses a verdict and reads every field`() {
        val verdict =
            json.decodeFromString<Verdict>(
                """
                {"journey":"login","results":[
                  {"action":"Tap login","status":"PASSED","reasoning":"button was visible",
                   "commands":["adb shell input tap 1 2"],"artifacts":["shots/01.png"]}
                ]}
                """.trimIndent(),
            )
        assertEquals("login", verdict.journey)
        val action = verdict.results.single()
        assertEquals(ActionStatus.PASSED, action.status)
        assertEquals("button was visible", action.reasoning)
        assertEquals(listOf("adb shell input tap 1 2"), action.commands)
        assertEquals(listOf("shots/01.png"), action.artifacts)
    }

    @Test
    fun `unknown and missing status values do not fail parsing`() {
        val verdict =
            json.decodeFromString<Verdict>(
                """{"journey":"j","results":[{"action":"a","status":"WAT"},{"action":"b"}]}""",
            )
        assertTrue(verdict.results.all { it.status == ActionStatus.UNKNOWN })
    }

    @Test
    fun `ignores fields this version does not know`() {
        val verdict =
            json.decodeFromString<Verdict>(
                """{"journey":"j","results":[{"action":"a","status":"PASSED","futureField":42}]}""",
            )
        assertTrue(verdict.allPassed)
    }

    @Test
    fun `passes only when every action passed`() {
        fun verdict(vararg statuses: ActionStatus) = Verdict("j", statuses.mapIndexed { i, s -> ActionResult("a$i", s) })

        assertTrue(verdict(ActionStatus.PASSED, ActionStatus.PASSED).allPassed)
        assertFalse(verdict(ActionStatus.PASSED, ActionStatus.FAILED).allPassed)
        assertFalse(verdict(ActionStatus.PASSED, ActionStatus.SKIPPED).allPassed)
        assertEquals(1, verdict(ActionStatus.PASSED, ActionStatus.FAILED).passedCount)
    }

    @Test
    fun `an empty verdict does not count as passing`() {
        assertFalse(Verdict("j", emptyList()).allPassed)
    }

    @Test
    fun `the failure report names the failed action`() {
        val verdict =
            Verdict(
                "j",
                listOf(
                    ActionResult("tap", ActionStatus.PASSED),
                    ActionResult("verify", ActionStatus.FAILED, comment = "not on screen"),
                ),
            )
        val report = verdict.report()
        assertTrue(report.contains("FAILED"), report)
        assertTrue(report.contains("verify"), report)
        assertTrue(report.contains("not on screen"), report)
    }
}
