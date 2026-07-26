package io.github.fornewid.journeys.engine

import java.io.File

/**
 * Runs one journey through the configured agent and returns its verdict; holds no judgment
 * logic itself (the agent is the brain).
 *
 * The agent is invoked as `<agentCommand> '<prompt>'` (see [AgentProcess]), where the prompt
 * carries the journeys protocol with `{journey}` replaced by the file's absolute path. The agent
 * drives the device and prints the verdict JSON between the `<<<VERDICT>>>` and `<<<END>>>` markers.
 */
object CliJourneyRunner {
    private val verdictBlock =
        Regex(
            "${Regex.escape(JourneyConfig.MARKER_START)}(.*?)${Regex.escape(JourneyConfig.MARKER_END)}",
            RegexOption.DOT_MATCHES_ALL,
        )

    /** @param key the journey's path relative to the journeys dir, without the suffix. */
    fun run(
        journey: File,
        key: String,
        config: JourneyConfig,
    ): Verdict {
        require(journey.isFile) { "journey file not found: ${journey.absolutePath}" }

        val path = journey.absolutePath
        val outcome =
            AgentProcess.run(
                config = config,
                prompt = config.prompt.replace(JourneyConfig.JOURNEY_PLACEHOLDER, path),
                logName = key,
                timeoutSeconds = config.timeoutSeconds,
                journeyPath = path,
            )

        fun fail(reason: String): Nothing =
            error("$reason (journey=$key).\nSee ${outcome.log}\n--- last 2KB of output ---\n${outcome.tail()}")

        if (outcome.timedOut) fail("agent did not finish within ${config.timeoutSeconds}s")
        if (outcome.exitCode != 0) fail("agent exited with code ${outcome.exitCode}")

        // The prompt itself names both markers, so take the last block rather than the first.
        val block =
            verdictBlock
                .findAll(outcome.stdout)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: fail("no verdict block found; check that the agent printed the markers to stdout")
        return JourneyJson.lenient.decodeFromString<Verdict>(block.withoutMarkdownFence())
    }

    /** Agents often wrap JSON in a markdown fence; unwrap it before parsing. */
    private fun String.withoutMarkdownFence(): String {
        val value = trim()
        if (!value.startsWith("```") || !value.endsWith("```")) return value
        return value.substringAfter('\n', value).removeSuffix("```").trim()
    }
}
