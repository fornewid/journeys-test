package io.github.fornewid.journeys.engine

import kotlinx.serialization.Serializable

/** Verdict the agent emits (as JSON) for one journey run. */
@Serializable
data class Verdict(
    val journey: String,
    val results: List<ActionResult>,
) {
    val allPassed: Boolean get() = results.isNotEmpty() && results.all { it.status == "PASSED" }

    fun report(): String = results.joinToString("\n") { r ->
        val head = "${r.status.padEnd(7)} ${r.action}"
        if (r.comment.isNullOrBlank()) head else "$head — ${r.comment}"
    }
}

@Serializable
data class ActionResult(
    val action: String,
    val status: String, // PASSED | FAILED | SKIPPED
    val commands: List<String> = emptyList(),
    val comment: String? = null,
    val reasoning: String? = null,
    val artifacts: List<String> = emptyList(),
)
