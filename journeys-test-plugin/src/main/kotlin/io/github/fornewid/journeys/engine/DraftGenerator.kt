package io.github.fornewid.journeys.engine

import java.io.File

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
    /** @return the drafts that appeared, in the order they were found. */
    fun generate(
        config: JourneyConfig,
        draftsDir: File,
        scope: DraftScope,
    ): List<File> {
        draftsDir.mkdirs()
        val before = draftsDir.journeyFiles()

        val outcome = AgentProcess.run(config, prompt(draftsDir, scope), logName = "drafts/_generate")

        val created = draftsDir.journeyFiles() - before.toSet()
        if (outcome.timedOut) {
            error(
                "agent did not finish within ${config.timeoutSeconds}s while drafting.\n" +
                    "See ${outcome.log}\n--- last 2KB of output ---\n${outcome.tail()}",
            )
        }
        if (outcome.exitCode != 0 && created.isEmpty()) {
            error(
                "agent exited with code ${outcome.exitCode} and wrote no drafts.\n" +
                    "See ${outcome.log}\n--- last 2KB of output ---\n${outcome.tail()}",
            )
        }
        return created
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
                        "Explore the app running on the connected device and write one short journey per " +
                            "screen you can reach, each starting from a cold start. "
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
