package io.github.fornewid.journeys.engine

import kotlinx.serialization.Serializable

/** Outcome of a single action, as reported by the agent. */
@Serializable
enum class ActionStatus {
    PASSED,
    FAILED,
    SKIPPED,

    /** Anything the agent reported that this version does not recognize. */
    UNKNOWN,
}

/** Verdict the agent emits (as JSON) for one journey run. */
@Serializable
data class Verdict(
    val journey: String,
    val results: List<ActionResult>,
) {
    val passedCount: Int get() = results.count { it.status == ActionStatus.PASSED }

    val allPassed: Boolean get() = results.isNotEmpty() && passedCount == results.size

    fun report(): String =
        results.joinToString("\n") { r ->
            val head = "${r.status.name.padEnd(7)} ${r.action}"
            if (r.comment.isNullOrBlank()) head else "$head — ${r.comment}"
        }
}

@Serializable
data class ActionResult(
    val action: String,
    val status: ActionStatus = ActionStatus.UNKNOWN,
    val commands: List<String> = emptyList(),
    val comment: String? = null,
    val reasoning: String? = null,
    val artifacts: List<String> = emptyList(),
)
