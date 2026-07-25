package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs one journey through the configured agent and returns its verdict; holds no judgment
 * logic itself (the agent is the brain).
 *
 * It runs `<agentCommand> '<prompt>'`, where the agent command is just the CLI invocation
 * (e.g. `claude -p --allowedTools=Bash`) and the prompt carries the journeys protocol with
 * `{journey}` replaced by the file's absolute path. The agent drives the device and prints the
 * verdict JSON between the `<<<VERDICT>>>` and `<<<END>>>` markers.
 */
object CliJourneyRunner {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true // unrecognized status values become ActionStatus.UNKNOWN
        }
    private val verdictBlock = Regex("<<<VERDICT>>>(.*?)<<<END>>>", RegexOption.DOT_MATCHES_ALL)

    /** @param key the journey's path relative to the journeys dir, without the suffix. */
    fun run(
        journey: File,
        key: String,
        config: JourneyConfig,
    ): Verdict {
        require(journey.isFile) { "journey file not found: ${journey.absolutePath}" }

        val agentCommand =
            config.agentCommand
                ?: error(
                    "No agent command configured. Set the agent CLI to use via journeys { agentCommand = ... } " +
                        "in build.gradle (e.g. \"claude -p --allowedTools=Bash\") or the JOURNEY_AGENT_CMD env var. " +
                        "The plugin appends the journey prompt automatically.",
                )
        val path = journey.absolutePath
        val prompt = config.prompt.replace("{journey}", path)
        val command = "${agentCommand.replace("{journey}", quote(path))} ${quote(prompt)}"

        // The agent's output goes straight to a file, so waiting can time out and the log survives.
        val log = config.outputDir.resolve("$key.agent.log").apply { parentFile?.mkdirs() }
        val process =
            ProcessBuilder("bash", "-lc", command)
                .directory(config.workingDir)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        process.outputStream.close() // the agent gets EOF instead of waiting on stdin

        if (!process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
            error("agent did not finish within ${config.timeoutSeconds}s (journey=$key). See $log")
        }
        val output = if (log.isFile) log.readText() else ""
        if (process.exitValue() != 0) {
            error("agent exited with code ${process.exitValue()} (journey=$key).${tail(log, output)}")
        }
        // The prompt itself names both markers, so take the last block rather than the first.
        val block =
            verdictBlock
                .findAll(output)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: error(
                    "no verdict block found (journey=$key). " +
                        "Check that the agent printed <<<VERDICT>>>…<<<END>>>.${tail(log, output)}",
                )
        return json.decodeFromString<Verdict>(block)
    }

    private fun tail(
        log: File,
        output: String,
    ) = "\nSee $log\n--- last 2KB of output ---\n${output.takeLast(2048)}"

    /** Wraps a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
