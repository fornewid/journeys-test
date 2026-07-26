# Journeys Test

*English | [한국어](README.ko.md)*

A Gradle plugin that runs journeys on a device with your own CLI agent and reports the results as standard JUnit/Gradle tests.

## What is a Journey?

A journey is an end-to-end test written in natural language. Instead of code, you list steps like "Tap the search icon" or "Verify a result list is shown" in XML, and an agent runs them on the device and judges each step. It is the feature Android Studio offers through Gemini; see the official docs, [Journeys for Android Studio](https://developer.android.com/studio/gemini/journeys).

Journey files live where Android Studio expects them: under a module's `src/journeysTest/`, named `*.journey.xml`.

```
app/src/journeysTest/search.journey.xml
```

```xml
<journey name="search">
  <description>Find an item with search</description>
  <actions>
    <action>Tap the search icon</action>
    <action>Type "compose" into the search field</action>
    <action>Verify a result list is shown</action>
  </actions>
</journey>
```

Android Studio runs the journey on Google's backend. This plugin swaps only the executor for an agent (model) of your choice, with no Google Cloud auth and no specific backend. Because the folder, extension, and task name (`journeysTest`) match Studio, the same files work in both.

## Setup

Apply the plugin and set only the agent CLI you use. The prompt that carries the journeys protocol (run each action in order, check/verify inspect the current screen only, judge PASSED/FAILED, the same as Android Studio Journeys) is built into the plugin and appended to this command automatically.

```kotlin
// build.gradle.kts
plugins { id("io.github.fornewid.journeys-test") version "0.1.0" }

journeys {
    agentCommand.set("claude --no-session-persistence --allowedTools 'Bash(android *)' 'Bash(adb *)' -p")
}
```

Any agent with a headless mode works; just change this value.

- Claude Code: `claude --no-session-persistence --allowedTools 'Bash(android *)' 'Bash(adb *)' -p`
- Codex: `codex exec --ephemeral --sandbox workspace-write -c 'sandbox_workspace_write.network_access=true'`
- Antigravity: `agy --dangerously-skip-permissions --print-timeout 20m -p`

Each example ends with the flag that takes the prompt, so the plugin's prompt is not swallowed by a
preceding option, and grants the agent enough permission to drive the device. Note Antigravity's
`--print-timeout` defaults to 5 minutes, the same as `timeoutSeconds`: keep it above that, or
the agent gives up before the plugin does and no verdict is printed.

To customize the built-in prompt, override `prompt`; its `{journey}` is replaced with the journey file's absolute path.

## Run

```bash
./gradlew journeysTest
```

Two outputs:

- `build/journey-results/*.xml`: standard JUnit XML. Point your CI's test reporter at this path to collect it.
- `build/journeys/report.html`: the per-step result view (below).

## Agent contract

The plugin holds no judgment logic; the agent named by `agentCommand` is the brain (Claude Code, Gemini CLI, and so on, model-agnostic). It drives the device, then prints the verdict as JSON between the `<<<VERDICT>>>` and `<<<END>>>` markers.

```json
{"journey":"...","results":[{"action":"...","status":"PASSED","reasoning":"...","artifacts":["shots/01.png"]}]}
```

## Drafting journeys

Let the agent propose journeys for whatever you just changed, instead of writing the first draft
yourself:

```bash
./gradlew journeysDraft                            # from the working tree diff
./gradlew journeysDraft --since=main               # from a diff against a ref
./gradlew journeysDraft --about="search filter"    # before the code exists
./gradlew journeysDraft --explore                  # one smoke journey per screen
```

Drafts land in `build/journeys/drafts`, never in `src/journeysTest`, so `journeysTest` cannot pick
them up by accident and nothing unreviewed reaches CI. Review them, then promote:

```bash
./gradlew journeysDraftList                        # what is waiting, and how it last did
./gradlew journeysTest --drafts                    # run them where they are
./gradlew journeysDraftPromote --draft=login       # move one into src/journeysTest
```

A draft only asserts what can be observed without knowing your product: a screen opens, an element
is present, nothing crashed or hung. Business rules, computed values and ordering are yours to add
when you promote one. That split is deliberate — automated exploration establishes only implicit
oracles, so a draft that claimed functional correctness would be guessing.

Journeys need the device to themselves. In a multi-module build Gradle would otherwise run two
agents at once, each tapping while the other's app is on screen, so `journeysTest` and
`journeysDraft` run one at a time — within a build, and between builds as well: start one in a
second terminal, or from another checkout, and it waits its turn instead of fighting for the
screen, reporting who has the device while it waits. It queues for ten minutes before giving up;
change that with `deviceWaitSeconds`. One device is assumed; with several attached, set
`ANDROID_SERIAL` per run and each device gets a turn of its own.

## Sample

Start an Android emulator or connect a device, then run the compact offline Compose sample:

```bash
./gradlew :sample:journeysTest
```

The task builds and installs the sample APK, then Claude Code runs the onboarding-to-article journey.
Override the agent with `-PjourneyAgentCommand=...`; see `sample/README.md` for the Claude command.

## Result view

Running `journeysTest` also writes `build/journeys/report.html`: each step's pass/fail and, if the agent captured screenshots, those screens, all in one page.

<img src="docs/result-view.png" alt="Result view example" width="480">
