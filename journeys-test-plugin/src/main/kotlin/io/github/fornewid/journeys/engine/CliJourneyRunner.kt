package io.github.fornewid.journeys.engine

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs one journey through the configured agent and returns its verdict; holds no judgment
 * logic itself (the agent is the brain).
 *
 * It runs `<agentCommand> '<prompt>'`, where the agent command is just the CLI invocation
 * (e.g. `claude --no-session-persistence --allowedTools 'Bash(android *)' 'Bash(adb *)' -p`)
 * and the prompt carries the journeys protocol with `{journey}` replaced by the file's absolute
 * path. The agent drives the device and prints the verdict JSON between the `<<<VERDICT>>>` and
 * `<<<END>>>` markers.
 *
 * POSIX only: the agent is spawned through `bash -lc`, a login shell, so agent CLIs installed by
 * nvm/homebrew resolve under the Gradle daemon's minimal environment.
 */
object CliJourneyRunner {
    private val verdictBlock =
        Regex(
            "${Regex.escape(JourneyConfig.MARKER_START)}(.*?)${Regex.escape(JourneyConfig.MARKER_END)}",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * Runs the agent in its own process group so a timeout kills the whole tree, not just the
     * shell: an abandoned agent would otherwise keep driving the device and burning tokens.
     * `destroy()` (SIGTERM) is what triggers the trap below, so it must come before `destroyForcibly()`.
     */
    private val processWrapper =
        """
        set -m
        bash -lc "${'$'}1" &
        agent_pid=${'$'}!
        cleanup() {
          kill -TERM -- "-${'$'}agent_pid" 2>/dev/null && {
            sleep 0.1
            kill -KILL -- "-${'$'}agent_pid" 2>/dev/null
          }
        }
        trap cleanup EXIT
        trap 'cleanup; exit 143' TERM INT
        wait "${'$'}agent_pid"
        """.trimIndent()

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
                        "in build.gradle or the ${JourneyConfig.ENV_AGENT_COMMAND} env var. " +
                        "The plugin appends the journey prompt automatically.",
                )
        val path = journey.absolutePath
        // The prompt is quoted as a whole, so it carries the raw path; the agent command is
        // interpolated into the shell string, so its placeholder is quoted on its own.
        val prompt = config.prompt.replace(JourneyConfig.JOURNEY_PLACEHOLDER, path)
        val command =
            "${agentCommand.replace(JourneyConfig.JOURNEY_PLACEHOLDER, singleQuote(path))} ${singleQuote(prompt)}"

        // stdout and stderr are redirected to separate files: only stdout may carry the verdict,
        // stderr is kept for diagnostics, and writing to files (rather than pipes) means a
        // timeout never has to race a reader that an inherited child is holding open.
        val log = config.outputDir.resolve("$key.agent.log").apply { parentFile?.mkdirs() }
        val errorLog = config.outputDir.resolve("$key.agent.err.log")
        val process =
            ProcessBuilder("bash", "-c", processWrapper, "journeys-agent", command)
                .directory(config.workingDir)
                .redirectOutput(log)
                .redirectError(errorLog)
                .start()
        process.outputStream.close() // the agent gets EOF instead of waiting on stdin

        fun fail(reason: String): Nothing {
            val tail = (log.textOrEmpty() + errorLog.textOrEmpty()).takeLast(2048)
            error("$reason (journey=$key).\nSee $log\n--- last 2KB of output ---\n$tail")
        }

        if (!process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)) {
            terminate(process)
            fail("agent did not finish within ${config.timeoutSeconds}s")
        }
        if (process.exitValue() != 0) {
            fail("agent exited with code ${process.exitValue()}")
        }
        // The prompt itself names both markers, so take the last block rather than the first.
        val block =
            verdictBlock
                .findAll(log.textOrEmpty())
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: fail("no verdict block found; check that the agent printed the markers to stdout")
        return JourneyJson.lenient.decodeFromString<Verdict>(block.withoutMarkdownFence())
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun File.textOrEmpty(): String = if (isFile) readText() else ""

    /** Agents often wrap JSON in a markdown fence; unwrap it before parsing. */
    private fun String.withoutMarkdownFence(): String {
        val value = trim()
        if (!value.startsWith("```") || !value.endsWith("```")) return value
        return value.substringAfter('\n', value).removeSuffix("```").trim()
    }

    /** Wraps a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
