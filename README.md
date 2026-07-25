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
- Antigravity: `agy -p`

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

## Sample

Start an Android emulator or connect a device, then run the compact offline Compose sample:

```bash
./gradlew :sample:journeysTest
```

The task builds and installs the sample APK, then Codex runs the onboarding-to-article journey.
Override the agent with `-PjourneyAgentCommand=...`; see `sample/README.md` for the Claude command.

## Result view

Running `journeysTest` also writes `build/journeys/report.html`: each step's pass/fail and, if the agent captured screenshots, those screens, all in one page.

<img src="docs/result-view.png" alt="Result view example" width="480">
