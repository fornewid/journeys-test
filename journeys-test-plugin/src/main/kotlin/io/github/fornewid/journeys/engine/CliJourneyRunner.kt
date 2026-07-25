package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runs one journey through the configured agent and returns its verdict; holds no judgment
 * logic itself (the agent is the brain).
 *
 * It runs `<agentCommand> '<prompt>'`, where the agent command is just the CLI invocation
 * (e.g. `claude --no-session-persistence --allowedTools 'Bash(adb *)' -p`) and the prompt
 * (built-in, see [EngineConfig.prompt])
 * carries the journeys protocol with `{journey}` replaced by the file's absolute path. The
 * agent drives the device and prints the verdict JSON between the `<<<VERDICT>>>` and
 * `<<<END>>>` markers.
 */
object CliJourneyRunner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val verdictBlock = Regex("<<<VERDICT>>>(.*?)<<<END>>>", RegexOption.DOT_MATCHES_ALL)
    private val processWrapper = """
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

    fun run(journey: File): Verdict {
        require(journey.isFile) { "journey file not found: ${journey.absolutePath}" }

        val agentCommand = EngineConfig.agentCommand()
            ?: error(
                "No agent command configured. Set the agent CLI to use via journeys { agentCommand = ... } " +
                    "in build.gradle (e.g. \"claude --no-session-persistence --allowedTools 'Bash(adb *)' -p\") " +
                    "or the JOURNEY_AGENT_CMD env var. " +
                    "The plugin appends the journey prompt automatically.",
            )
        val path = journey.absolutePath
        val prompt = EngineConfig.prompt().replace("{journey}", path)
        val cmd = "${agentCommand.replace("{journey}", singleQuote(path))} ${singleQuote(prompt)}"
        val timeoutSec = EngineConfig.timeoutSeconds()

        val proc = ProcessBuilder("bash", "-c", processWrapper, "journeys-agent", cmd).start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
        proc.outputStream.close()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader = readAsync(proc.inputStream.bufferedReader(), stdout)
        val stderrReader = readAsync(proc.errorStream.bufferedReader(), stderr)
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            terminate(proc)
            closeOutputStreams(proc)
            joinReadersAfterClose(stdoutReader, stderrReader)
            error(
                "agent did not finish within ${timeoutSec}s (journey=${journey.name}).\n" +
                "--- last 2KB of output ---\n${combinedOutput(stdout, stderr).takeLast(2048)}",
            )
        }
        if (!joinReadersUntil(deadline, stdoutReader, stderrReader)) {
            closeOutputStreams(proc)
            joinReadersAfterClose(stdoutReader, stderrReader)
            error(
                "agent output did not close within ${timeoutSec}s (journey=${journey.name}).\n" +
                    "--- last 2KB of output ---\n${combinedOutput(stdout, stderr).takeLast(2048)}",
            )
        }
        val out = stdout.toString()
        val block = verdictBlock.find(out)?.groupValues?.get(1)?.trim()
            ?: error(
                "no verdict block found (exit=${proc.exitValue()}, journey=${journey.name}).\n" +
                    "Check that the agent printed <<<VERDICT>>>…<<<END>>> to stdout.\n" +
                    "--- last 2KB of output ---\n${combinedOutput(stdout, stderr).takeLast(2048)}",
            )
        return json.decodeFromString<Verdict>(block.withoutMarkdownFence())
    }

    private fun readAsync(reader: java.io.BufferedReader, output: StringBuilder): Thread =
        Thread {
            try {
                reader.useLines { lines ->
                    lines.forEach { output.appendLine(it) }
                }
            } catch (_: IOException) {
                // The main thread closes streams when the configured timeout expires.
            }
        }.apply {
            isDaemon = true
            start()
        }

    private fun terminate(proc: Process) {
        proc.destroy()
        if (!proc.waitFor(1, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            proc.waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun closeOutputStreams(proc: Process) {
        runCatching { proc.inputStream.close() }
        runCatching { proc.errorStream.close() }
    }

    private fun joinReadersUntil(deadline: Long, vararg readers: Thread): Boolean {
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

    private fun combinedOutput(stdout: StringBuilder, stderr: StringBuilder): String =
        buildString {
            append(stdout)
            if (isNotEmpty() && stderr.isNotEmpty()) appendLine()
            append(stderr)
        }

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

    /** Wrap a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
