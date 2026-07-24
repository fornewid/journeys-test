package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs one journey through the configured agent and returns its verdict; holds no judgment
 * logic itself (the agent is the brain).
 *
 * It runs `<agentCommand> '<prompt>'`, where the agent command is just the CLI invocation
 * (e.g. `claude -p --allowedTools Bash`) and the prompt (built-in, see [EngineConfig.prompt])
 * carries the journeys protocol with `{journey}` replaced by the file's absolute path. The
 * agent drives the device and prints the verdict JSON between the `<<<VERDICT>>>` and
 * `<<<END>>>` markers.
 */
object CliJourneyRunner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val verdictBlock = Regex("<<<VERDICT>>>(.*?)<<<END>>>", RegexOption.DOT_MATCHES_ALL)

    fun run(journey: File): Verdict {
        require(journey.isFile) { "journey file not found: ${journey.absolutePath}" }

        val agentCommand = EngineConfig.agentCommand()
            ?: error(
                "No agent command configured. Set the agent CLI to use via journeys { agentCommand = ... } " +
                    "in build.gradle (e.g. \"claude -p --allowedTools Bash\") or the JOURNEY_AGENT_CMD env var. " +
                    "The plugin appends the journey prompt automatically.",
            )
        val path = journey.absolutePath
        val prompt = EngineConfig.prompt().replace("{journey}", path)
        val cmd = "${agentCommand.replace("{journey}", path)} ${singleQuote(prompt)}"
        val timeoutSec = EngineConfig.timeoutSeconds()

        val proc = ProcessBuilder("bash", "-lc", cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            error("agent did not finish within ${timeoutSec}s (journey=${journey.name})")
        }
        val block = verdictBlock.find(out)?.groupValues?.get(1)?.trim()
            ?: error(
                "no verdict block found (exit=${proc.exitValue()}, journey=${journey.name}).\n" +
                    "Check that the agent printed <<<VERDICT>>>…<<<END>>>.\n--- last 2KB of output ---\n${out.takeLast(2048)}",
            )
        return json.decodeFromString<Verdict>(block)
    }

    /** Wrap a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
