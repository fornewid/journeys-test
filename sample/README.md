# Compact Now in Android sample

This module is a single-activity, offline Compose app inspired by the
[Now in Android sample](https://github.com/fornewid/android-cli/tree/main/nowinandroid).
It keeps one deterministic onboarding-to-article flow for `journeysTest` and intentionally omits
the original app's build logic, flavors, dependency injection, database, networking, Firebase,
benchmarking, lint, and formatting infrastructure.

Run with Codex:

```shell
./gradlew :sample:journeysTest
```

The task builds and installs the debug APK on the connected device before starting the journey.

Run with Claude Code:

```shell
./gradlew :sample:journeysTest \
  -PjourneyAgentCommand="claude --no-session-persistence --allowedTools 'Bash(adb *)' -p"
```
