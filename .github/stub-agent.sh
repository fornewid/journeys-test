#!/usr/bin/env bash
# Stands in for a CLI agent when no credentials are configured, so CI can check that the
# emulator, the install, the task and the report all work without judging anything.
# It never looks at the device: every verdict it writes says so.
set -euo pipefail

# The plugin appends the prompt as the last argument, and the prompt carries the journey's
# absolute path — the only thing this needs in order to answer about the right journey.
prompt="${*: -1}"
file=$(grep -oE '/[^[:space:]]+\.journey\.xml' <<<"$prompt" | head -1)
journey=$(sed -n 's/.*<journey name="\([^"]*\)".*/\1/p' "$file" | head -1)

printf '<<<VERDICT>>>{"journey":"%s","results":[{"action":"stub agent: the plumbing ran, the app was not driven","status":"PASSED","reasoning":"No agent credentials in CI, so nothing was verified on the device."}]}<<<END>>>\n' "$journey"
