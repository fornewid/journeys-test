package io.github.fornewid.journeys.engine

import kotlinx.serialization.json.Json
import org.junit.platform.engine.ConfigurationParameters
import java.io.File

/** The JSON settings shared by everything that reads or writes a verdict. */
internal object JourneyJson {
    /** Tolerates unknown fields and unrecognized [ActionStatus] values, which become UNKNOWN. */
    val lenient =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    val pretty = Json { prettyPrint = true }
}

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
    /** How long to queue behind other builds using the device before giving up. */
    val deviceWaitSeconds: Long = DEFAULT_DEVICE_WAIT_SECONDS,
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
            put(KEY_DEVICE_WAIT_SECONDS, deviceWaitSeconds.toString())
        }

    companion object {
        const val DEFAULT_JOURNEYS_DIR = "src/journeysTest"
        const val DEFAULT_OUTPUT_DIR = "journeys"
        const val DEFAULT_REPORTS_DIR = "journey-results"
        const val DEFAULT_TIMEOUT_SECONDS = 900L

        /**
         * How long a build queues behind others using the device, ten minutes by default.
         *
         * This is a policy about how long a build should sit waiting, not a bound derived from how
         * long a turn takes — and it is below [DEFAULT_TIMEOUT_SECONDS], so a neighbour running one
         * journey for longer than this fails the waiting build even though nothing is wrong. Raise
         * it where journeys run long, lower it to fail faster.
         */
        const val DEFAULT_DEVICE_WAIT_SECONDS = 600L
        const val JOURNEY_FILE_SUFFIX = ".journey.xml"

        /** Under [DEFAULT_OUTPUT_DIR]: agent-written drafts, deliberately outside the journeys source dir. */
        const val DRAFTS_DIR = "drafts"

        /** Replaced with the journey file's absolute path, in both the prompt and the agent command. */
        const val JOURNEY_PLACEHOLDER = "{journey}"

        /** The verdict envelope. Named in the prompt and matched when parsing, so they must agree. */
        const val MARKER_START = "<<<VERDICT>>>"
        const val MARKER_END = "<<<END>>>"

        /** Read by the plugin as the convention for `agentCommand`. */
        const val ENV_AGENT_COMMAND = "JOURNEY_AGENT_CMD"

        const val KEY_JOURNEYS_DIR = "journeys.dir"
        const val KEY_OUTPUT_DIR = "journeys.out"
        const val KEY_REPORTS_DIR = "journeys.reports"
        const val KEY_WORKING_DIR = "journeys.workingDir"
        const val KEY_AGENT_COMMAND = "journeys.agent.cmd"
        const val KEY_PROMPT = "journeys.agent.prompt"
        const val KEY_TIMEOUT_SECONDS = "journeys.agent.timeoutSec"
        const val KEY_DEVICE_WAIT_SECONDS = "journeys.device.waitSec"

        /** Instruction appended to the agent command; [JOURNEY_PLACEHOLDER] is the file's absolute path. */
        const val DEFAULT_PROMPT: String =
            "Read the journey at $JOURNEY_PLACEHOLDER and run each <action> in order on the connected " +
                "Android device. Use the android layout and android screen capture commands to inspect " +
                "the screen, and adb to tap, type, and swipe. Actions that start with check or verify " +
                "only inspect the current screen. Judge each action PASSED or FAILED, and stop at the " +
                "first FAILED. Save a screenshot for each action. Then print only the verdict as JSON " +
                "between the markers $MARKER_START and $MARKER_END, with fields journey and results, " +
                "where each result has action, status (PASSED or FAILED), reasoning, and artifacts " +
                "(the paths of the screenshots you saved)."

        /**
         * Reads the config back on the engine side.
         *
         * Values come from the discovery request; JUnit itself falls back to system properties and
         * `junit-platform.properties`, which is what keeps the engine usable standalone (registered
         * through `META-INF/services`) without a second configuration vocabulary.
         */
        fun from(params: ConfigurationParameters): JourneyConfig {
            fun get(key: String): String? = params.get(key).orElse(null)?.takeIf { it.isNotBlank() }

            val base = File(get(KEY_WORKING_DIR) ?: ".").absoluteFile

            fun dir(
                key: String,
                default: String,
            ) = get(key)?.let(::File) ?: base.resolve(default)

            return JourneyConfig(
                journeysDir = dir(KEY_JOURNEYS_DIR, DEFAULT_JOURNEYS_DIR),
                outputDir = dir(KEY_OUTPUT_DIR, DEFAULT_OUTPUT_DIR),
                reportsDir = dir(KEY_REPORTS_DIR, DEFAULT_REPORTS_DIR),
                workingDir = base,
                agentCommand = get(KEY_AGENT_COMMAND),
                prompt = get(KEY_PROMPT) ?: DEFAULT_PROMPT,
                timeoutSeconds = get(KEY_TIMEOUT_SECONDS)?.toLongOrNull() ?: DEFAULT_TIMEOUT_SECONDS,
                deviceWaitSeconds = get(KEY_DEVICE_WAIT_SECONDS)?.toLongOrNull() ?: DEFAULT_DEVICE_WAIT_SECONDS,
            )
        }
    }
}
