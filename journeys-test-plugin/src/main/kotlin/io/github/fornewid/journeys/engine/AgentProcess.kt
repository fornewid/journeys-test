package io.github.fornewid.journeys.engine

import java.io.File
import java.util.concurrent.TimeUnit

/** What the agent left behind after one run. */
internal class AgentOutcome(
    val exitCode: Int,
    val timedOut: Boolean,
    val log: File,
    val errorLog: File,
) {
    val stdout: String get() = log.textOrEmpty()

    /** A short tail of both streams, for error messages. */
    fun tail(limit: Int = 2048): String = (stdout + errorLog.textOrEmpty()).takeLast(limit)

    private fun File.textOrEmpty(): String = if (isFile) readText() else ""
}

/**
 * Spawns the configured agent CLI with a prompt and waits for it.
 *
 * Shared by everything that drives an agent: running a journey ([CliJourneyRunner]) and drafting
 * one ([DraftGenerator]). It only runs the process — deciding what the output means is the
 * caller's job.
 *
 * POSIX only: the agent is spawned through `bash -lc`, a login shell, so agent CLIs installed by
 * nvm/homebrew resolve under the Gradle daemon's minimal environment.
 */
internal object AgentProcess {
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

    /**
     * @param prompt sent to the agent as one quoted argument.
     * @param timeoutSeconds how long this piece of work may take. Running a journey and exploring an
     *   app to draft them are different jobs with their own limits, so the caller supplies it.
     * @param journeyPath substituted for [JourneyConfig.JOURNEY_PLACEHOLDER] in the agent command.
     * @param logName base name for the two log files written under the output directory.
     */
    fun run(
        config: JourneyConfig,
        prompt: String,
        logName: String,
        timeoutSeconds: Long,
        journeyPath: String? = null,
    ): AgentOutcome {
        val agentCommand =
            config.agentCommand
                ?: error(
                    "No agent command configured. Set the agent CLI to use via journeys { agentCommand = ... } " +
                        "in build.gradle or the ${JourneyConfig.ENV_AGENT_COMMAND} env var. " +
                        "The plugin appends the prompt automatically.",
                )
        // The agent command is interpolated into the shell string, so its placeholder is quoted on
        // its own; the prompt is quoted as a whole and therefore carries the raw path.
        val resolved =
            journeyPath?.let { agentCommand.replace(JourneyConfig.JOURNEY_PLACEHOLDER, singleQuote(it)) }
                ?: agentCommand
        val command = "$resolved ${singleQuote(prompt)}"

        // stdout and stderr go to separate files: only stdout may carry a verdict, stderr is kept
        // for diagnostics, and writing to files (rather than pipes) means a timeout never has to
        // race a reader that an inherited child is holding open.
        val log = config.outputDir.resolve("$logName.agent.log").apply { parentFile?.mkdirs() }
        val errorLog = config.outputDir.resolve("$logName.agent.err.log")
        // Every path that drives the device comes through here, so this is where the device is
        // claimed — for builds elsewhere on the machine as well as tasks in this one.
        return DeviceMutex.withDevice(
            timeoutSeconds = timeoutSeconds,
            maxWaitSeconds = config.deviceWaitSeconds,
            onWait = { lockFile, waited, holder ->
                System.err.println(
                    if (waited == 0L) {
                        "Waiting for the device: another build is driving it ($lockFile)"
                    } else {
                        "Still waiting for the device after ${waited}s: $holder"
                    },
                )
            },
        ) {
            val process =
                ProcessBuilder("bash", "-c", processWrapper, "journeys-agent", command)
                    .directory(config.workingDir)
                    .redirectOutput(log)
                    .redirectError(errorLog)
                    .start()
            process.outputStream.close() // the agent gets EOF instead of waiting on stdin

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) terminate(process)
            AgentOutcome(
                exitCode = if (finished) process.exitValue() else -1,
                timedOut = !finished,
                log = log,
                errorLog = errorLog,
            )
        }
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(1, TimeUnit.SECONDS)
        }
    }

    /** Wraps a string as a single shell argument (safe even if it contains quotes or spaces). */
    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
