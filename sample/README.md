# Compact Now in Android sample

This module is a single-activity, offline Compose app inspired by the
[Now in Android sample](https://github.com/fornewid/android-cli/tree/main/nowinandroid).
It keeps one deterministic onboarding-to-article flow for `journeysTest` and intentionally omits
the original app's build logic, flavors, dependency injection, database, networking, Firebase,
benchmarking, lint, and formatting infrastructure.

Start an emulator or connect a device, then run it with the default agent, Claude Code:

```shell
./gradlew :sample:journeysTest
```

The task builds and installs the debug APK on the connected device before starting the journey.

Use a different agent with `-PjourneyAgentCommand=...`:

```shell
# Codex
./gradlew :sample:journeysTest \
  -PjourneyAgentCommand="codex exec --ephemeral --sandbox workspace-write -c 'sandbox_workspace_write.network_access=true'"

# Antigravity — keep --print-timeout above this module's timeoutSeconds (300s), or the agent
# gives up before the plugin does and never prints a verdict
./gradlew :sample:journeysTest \
  -PjourneyAgentCommand="agy --dangerously-skip-permissions --print-timeout 10m -p"
```
