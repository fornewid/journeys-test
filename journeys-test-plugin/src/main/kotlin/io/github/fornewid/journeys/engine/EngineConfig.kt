package io.github.fornewid.journeys.engine

/**
 * All engine setting keys and defaults in one place. Resolution order for each value:
 * system property, then environment variable, then the default. The plugin injects
 * properties; the defaults apply when the engine runs standalone.
 */
internal object EngineConfig {
    fun journeysDir(): String = get("journeys.dir", "JOURNEYS_DIR") ?: "src/journeysTest"
    fun outputDir(): String = get("journeys.out", "JOURNEYS_OUT") ?: "build/journeys"
    fun reportsDir(): String = get("journeys.reports", "JOURNEYS_REPORTS") ?: "build/journey-results"
    fun agentCommand(): String? = get("journey.agent.cmd", "JOURNEY_AGENT_CMD")
    fun prompt(): String = get("journey.agent.prompt", "JOURNEY_AGENT_PROMPT") ?: DEFAULT_PROMPT
    fun timeoutSeconds(): Long = get("journey.agent.timeoutSec", "JOURNEY_AGENT_TIMEOUT_SEC")?.toLongOrNull() ?: 900L

    /** Built-in instruction appended to the agent command; `{journey}` is the file's absolute path. */
    const val DEFAULT_PROMPT: String =
        "Read the journey at {journey} and run each <action> in order on the connected Android device. " +
            "Use adb uiautomator dump and adb exec-out screencap to inspect the screen, and adb input " +
            "commands to tap, type, swipe, and press keys. Actions that start with check or verify only inspect the current " +
            "screen. Judge each action PASSED or FAILED, and stop at the first FAILED. Then print only " +
            "the verdict as JSON between the markers <<<VERDICT>>> and <<<END>>>, with fields journey and " +
            "results, where each result has action, status (PASSED or FAILED), and reasoning."

    private fun get(prop: String, env: String): String? =
        System.getProperty(prop)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }
}
