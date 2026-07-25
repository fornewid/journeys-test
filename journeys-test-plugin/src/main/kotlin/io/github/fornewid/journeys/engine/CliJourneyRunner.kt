package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
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
 */
object CliJourneyRunner {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true // unrecognized status values become ActionStatus.UNKNOWN
        }
    private val verdictBlock = Regex("<<<VERDICT>>>(.*?)<<<END>>>", RegexOption.DOT_MATCHES_ALL)

    /**
     * Runs the agent in its own process group so a timeout kills the whole tree, not just the
     * shell: an abandoned agent would otherwise keep driving the device and burning tokens.
     */
    private val processWrapper =
        """
        set -m
        bash -lc "${'$'}1" &
        agent_pid=${'$'}!
        cleanup() {
          if kill -TERM -- "-${'$'}agent_pid" 2>/dev/null; then
            sleep 0.1
            kill -KILL -- "-${'$'}agent_pid" 2>/dev/null || true
          fi
        }
        trap cleanup EXIT
        trap 'cleanup; exit 143' TERM INT
        wait "${'$'}agent_pid"
        status=${'$'}?
        exit "${'$'}status"
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
                        "in build.gradle or the JOURNEY_AGENT_CMD env var. " +
                        "The plugin appends the journey prompt automatically.",
                )
        val path = journey.absolutePath
        val prompt = config.prompt.replace("{journey}", path)
        val command = "${agentCommand.replace("{journey}", singleQuote(path))} ${singleQuote(prompt)}"

        val process =
            ProcessBuilder("bash", "-c", processWrapper, "journeys-agent", command)
                .directory(config.workingDir)
                .start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds)
        process.outputStream.close() // the agent gets EOF instead of waiting on stdin

        // stdout and stderr are read separately: only stdout may carry the verdict, while stderr
        // is kept for diagnostics.
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader = readAsync(process.inputStream.bufferedReader(), stdout)
        val stderrReader = readAsync(process.errorStream.bufferedReader(), stderr)

        if (!process.waitFor(config.timeoutSeconds, TimeUnit.SECONDS)) {
            terminate(process)
            closeOutputStreams(process)
            joinReadersAfterClose(stdoutReader, stderrReader)
            fail(config, key, "agent did not finish within ${config.timeoutSeconds}s", stdout, stderr)
        }
        if (!joinReadersUntil(deadline, stdoutReader, stderrReader)) {
            closeOutputStreams(process)
            joinReadersAfterClose(stdoutReader, stderrReader)
            fail(config, key, "agent output did not close within ${config.timeoutSeconds}s", stdout, stderr)
        }
        if (process.exitValue() != 0) {
            fail(config, key, "agent exited with code ${process.exitValue()}", stdout, stderr)
        }
        // The prompt itself names both markers, so take the last block rather than the first.
        val block =
            verdictBlock
                .findAll(stdout)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: fail(
                    config,
                    key,
                    "no verdict block found; check that the agent printed <<<VERDICT>>>…<<<END>>> to stdout",
                    stdout,
                    stderr,
                )
        return json.decodeFromString<Verdict>(block.withoutMarkdownFence())
    }

    /** Keeps the agent's output next to the verdicts for debugging, then fails with a tail of it. */
    private fun fail(
        config: JourneyConfig,
        key: String,
        reason: String,
        stdout: StringBuilder,
        stderr: StringBuilder,
    ): Nothing {
        val output = combinedOutput(stdout, stderr)
        val log = config.outputDir.resolve("$key.agent.log").apply { parentFile?.mkdirs() }
        runCatching { log.writeText(output) }
        error("$reason (journey=$key).\nSee $log\n--- last 2KB of output ---\n${output.takeLast(2048)}")
    }

    private fun readAsync(
        reader: BufferedReader,
        output: StringBuilder,
    ): Thread =
        Thread {
            try {
                reader.useLines { lines -> lines.forEach { output.appendLine(it) } }
            } catch (_: IOException) {
                // The main thread closes streams when the configured timeout expires.
            }
        }.apply {
            isDaemon = true
            start()
        }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun closeOutputStreams(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun joinReadersUntil(
        deadline: Long,
        vararg readers: Thread,
    ): Boolean {
        readers.forEach { reader ->
            if (!reader.isAlive) return@forEach
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return false
            reader.join(TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1))
            if (reader.isAlive) return false
        }
        return true
    }

    private fun joinReadersAfterClose(vararg readers: Thread) {
        readers.forEach { it.join(1_000) }
    }

    private fun combinedOutput(
        stdout: StringBuilder,
        stderr: StringBuilder,
    ): String =
        buildString {
            append(stdout)
            if (isNotEmpty() && stderr.isNotEmpty()) appendLine()
            append(stderr)
        }

    /** Agents often wrap JSON in a markdown fence; unwrap it before parsing. */
    private fun String.withoutMarkdownFence(): String {
        val value = trim()
        if (!value.startsWith("```") || !value.endsWith("```")) return value
        val openingFenceEnd = value.indexOf('\n')
        if (openingFenceEnd < 0) return value
        return value
            .substring(openingFenceEnd + 1)
            .removeSuffix("```")
            .trim()
    }

    /** Wraps a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
