package io.github.fornewid.journeys.engine

import java.io.File

/** The drafts a run produced, with [cutShort] set when the agent did not get to finish. */
internal class DraftResult(
    val created: List<File>,
    val cutShort: String?,
)

/** What the agent is asked to draft journeys about. */
sealed interface DraftScope {
    /** Journeys that reach whatever the working tree (or [since]..HEAD) changed. */
    data class Changes(
        val since: String? = null,
    ) : DraftScope

    /** Journeys for a change described in prose, when the code is not written yet. */
    data class About(
        val description: String,
    ) : DraftScope

    /** One smoke journey per screen, by exploring the running app. */
    data object Explore : DraftScope
}

/**
 * Asks the agent to write journey drafts into the drafts directory.
 *
 * Drafts are deliberately kept out of the journeys source directory: they are a starting point for
 * a human, not tests. Automated exploration only ever establishes implicit oracles (it crashed, or
 * it did not), so the prompt forbids asserting functional correctness — a person adds that when
 * promoting the draft.
 */
internal object DraftGenerator {
    /** @return the drafts that appeared, and why the set may be incomplete. */
    fun generate(
        config: JourneyConfig,
        draftsDir: File,
        scope: DraftScope,
    ): DraftResult {
        draftsDir.mkdirs()
        val before = draftsDir.journeyFiles()

        val outcome =
            AgentProcess.run(
                config,
                prompt(draftsDir, scope),
                logName = "drafts/_generate",
                timeoutSeconds = config.draftTimeoutSeconds,
            )

        // Drafts the agent managed to write are kept whichever way it ended: they are a starting
        // point for a person, who is going to read them before promoting anything, and an agent
        // that explored an app for twenty minutes and then ran out of time has still done the work.
        val created = draftsDir.journeyFiles() - before.toSet()
        if (created.isEmpty()) {
            val reason =
                if (outcome.timedOut) {
                    "agent did not finish within ${config.draftTimeoutSeconds}s while drafting"
                } else {
                    "agent exited with code ${outcome.exitCode} and wrote no drafts"
                }
            error("$reason.\nSee ${outcome.log}\n--- last 2KB of output ---\n${outcome.tail()}")
        }
        val cutShort =
            when {
                outcome.timedOut ->
                    "the agent ran out of its ${config.draftTimeoutSeconds}s budget, so this may not be " +
                        "the whole set. Raise draftTimeoutSeconds to give it longer. See ${outcome.log}"
                outcome.exitCode != 0 ->
                    "the agent exited with code ${outcome.exitCode}, so this may not be the whole set. " +
                        "See ${outcome.log}"
                else -> null
            }
        return DraftResult(created, cutShort)
    }

    private fun File.journeyFiles(): List<File> =
        walkTopDown()
            .filter { it.isFile && it.name.endsWith(JourneyConfig.JOURNEY_FILE_SUFFIX) }
            .sortedBy { it.path }
            .toList()

    private fun prompt(
        draftsDir: File,
        scope: DraftScope,
    ): String =
        buildString {
            append(
                when (scope) {
                    is DraftScope.Changes -> {
                        val diff = scope.since?.let { "git diff $it...HEAD" } ?: "git diff HEAD"
                        "Run `$diff` to see what changed in this Android project, and read the changed " +
                            "code to work out which screens or flows it affects. Write one journey per " +
                            "affected flow that reaches it from a cold start. "
                    }
                    is DraftScope.About -> {
                        "An upcoming change is described as: ${scope.description}. Read the project's code " +
                            "to work out which screens or flows it affects, and write one journey per " +
                            "affected flow that reaches it from a cold start. "
                    }
                    DraftScope.Explore ->
                        // Naming the app is the whole job here. The other scopes read the project's
                        // code and pick up its package on the way; this one has nothing to go on, and
                        // a device usually has several apps installed — left to guess, it explores
                        // whichever one happens to be on screen.
                        "Find this project's application id in its Gradle files. Force-stop and " +
                            "cold-launch that package on the connected device, and explore only that " +
                            "app. Write one short journey per screen you can reach, each starting by " +
                            "force-stopping and cold-launching that same package. "
                },
            )
            append(
                """
                Write each journey as XML in this exact shape, one file per journey, into $draftsDir:

                <journey name="short-kebab-name">
                  <description>one sentence on what this covers</description>
                  <actions>
                    <action>Tap the search icon</action>
                    <action>Verify the search results are shown</action>
                  </actions>
                </journey>

                Name each file <short-kebab-name>${JourneyConfig.JOURNEY_FILE_SUFFIX}.

                Keep every action a single user-visible step, phrased the way a tester would say it.
                Actions that start with check or verify only inspect the screen that is already showing.

                These drafts are a starting point for a person, so only assert what you can observe
                without knowing the product's intent: that a screen opens, that an element is present,
                that the app does not crash or hang. Do NOT assert business rules, computed values,
                ordering, or anything whose correctness you would have to guess. Leave that to the
                person who promotes the draft.

                Prefer a handful of short journeys over one long one, and stop after 5 journeys.
                Print the list of files you wrote.
                """.trimIndent(),
            )
        }
}
