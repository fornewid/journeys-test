package io.github.fornewid.journeys.engine

import org.junit.platform.engine.ConfigurationParameters
import java.io.File

/**
 * Everything the engine needs for one run.
 *
 * The task builds this and the launcher hands it to the engine as JUnit Platform configuration
 * parameters, so it is request-scoped rather than JVM-global: parallel runs never interfere.
 * This type also holds the single source of the parameter keys and default values that the
 * plugin's conventions reuse.
 */
data class JourneyConfig(
    val journeysDir: File,
    val outputDir: File,
    val reportsDir: File,
    /** Directory the agent runs in, and the base for resolving relative artifact paths. */
    val workingDir: File,
    val agentCommand: String? = null,
    val prompt: String = DEFAULT_PROMPT,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) {
    /** Key/value pairs to put on the discovery request; the engine reads them back with [from]. */
    fun toParameters(): Map<String, String> =
        buildMap {
            put(KEY_JOURNEYS_DIR, journeysDir.absolutePath)
            put(KEY_OUTPUT_DIR, outputDir.absolutePath)
            put(KEY_REPORTS_DIR, reportsDir.absolutePath)
            put(KEY_WORKING_DIR, workingDir.absolutePath)
            agentCommand?.let { put(KEY_AGENT_COMMAND, it) }
            put(KEY_PROMPT, prompt)
            put(KEY_TIMEOUT_SECONDS, timeoutSeconds.toString())
        }

    companion object {
        const val DEFAULT_JOURNEYS_DIR = "src/journeysTest"
        const val DEFAULT_OUTPUT_DIR = "journeys"
        const val DEFAULT_REPORTS_DIR = "journey-results"
        const val DEFAULT_TIMEOUT_SECONDS = 900L
        const val JOURNEY_FILE_SUFFIX = ".journey.xml"

        const val KEY_JOURNEYS_DIR = "journeys.dir"
        const val KEY_OUTPUT_DIR = "journeys.out"
        const val KEY_REPORTS_DIR = "journeys.reports"
        const val KEY_WORKING_DIR = "journeys.workingDir"
        const val KEY_AGENT_COMMAND = "journeys.agent.cmd"
        const val KEY_PROMPT = "journeys.agent.prompt"
        const val KEY_TIMEOUT_SECONDS = "journeys.agent.timeoutSec"

        /** Instruction appended to the agent command; `{journey}` is the file's absolute path. */
        const val DEFAULT_PROMPT: String =
            "Read the journey at {journey} and run each <action> in order on the connected Android device. " +
                "Use the android layout and android screen capture commands to inspect the screen, and adb " +
                "to tap, type, and swipe. Actions that start with check or verify only inspect the current " +
                "screen. Judge each action PASSED or FAILED, and stop at the first FAILED. Then print only " +
                "the verdict as JSON between the markers <<<VERDICT>>> and <<<END>>>, with fields journey and " +
                "results, where each result has action, status (PASSED or FAILED), and reasoning."

        /**
         * Reads the config back on the engine side. Values come from the discovery request, and
         * JUnit itself falls back to system properties and `junit-platform.properties`, so running
         * the engine standalone keeps working; environment variables are the last fallback.
         */
        fun from(
            params: ConfigurationParameters,
            workingDir: File = File(".").absoluteFile,
        ): JourneyConfig {
            fun get(
                key: String,
                env: String,
            ): String? =
                params.get(key).orElse(null)?.takeIf { it.isNotBlank() }
                    ?: System.getenv(env)?.takeIf { it.isNotBlank() }

            val base = get(KEY_WORKING_DIR, "JOURNEYS_WORKING_DIR")?.let(::File) ?: workingDir

            fun dir(
                key: String,
                env: String,
                default: String,
            ) = get(key, env)?.let(::File) ?: base.resolve(default)

            return JourneyConfig(
                journeysDir = dir(KEY_JOURNEYS_DIR, "JOURNEYS_DIR", DEFAULT_JOURNEYS_DIR),
                outputDir = dir(KEY_OUTPUT_DIR, "JOURNEYS_OUT", "build/$DEFAULT_OUTPUT_DIR"),
                reportsDir = dir(KEY_REPORTS_DIR, "JOURNEYS_REPORTS", "build/$DEFAULT_REPORTS_DIR"),
                workingDir = base,
                agentCommand = get(KEY_AGENT_COMMAND, "JOURNEY_AGENT_CMD"),
                prompt = get(KEY_PROMPT, "JOURNEY_AGENT_PROMPT") ?: DEFAULT_PROMPT,
                timeoutSeconds =
                    get(KEY_TIMEOUT_SECONDS, "JOURNEY_AGENT_TIMEOUT_SEC")?.toLongOrNull()
                        ?: DEFAULT_TIMEOUT_SECONDS,
            )
        }
    }
}
