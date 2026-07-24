#!/usr/bin/env bash
# echo-agent.sh — device-free demo agent that turns the pipeline green.
#
# The plugin invokes this with the journey prompt (which contains the file path). It reads the
# journey XML and reports every <action> as PASSED, without touching a device, so the plugin
# wiring (discover -> agent -> verdict -> JUnit report) can be exercised anywhere, including CI.
# For real UI testing, point agentCommand at a real headless agent and connect a device.
set -euo pipefail

JOURNEY=$(printf '%s' "${1:-}" | grep -oE '/[^[:space:]]+\.journey\.xml' | head -1)
[ -n "$JOURNEY" ] || { echo "no journey path found in prompt" >&2; exit 1; }

python3 - "$JOURNEY" <<'PY'
import re, sys, json, html
xml = open(sys.argv[1], encoding="utf-8").read()
name = (re.search(r'name="([^"]+)"', xml) or [None, "journey"])[1]
actions = [html.unescape(a.strip()) for a in re.findall(r"<action>(.*?)</action>", xml, re.S)]
verdict = {
    "journey": name,
    "results": [
        {"action": a, "status": "PASSED", "reasoning": "echo-agent: device-free demo"}
        for a in actions
    ],
}
print("<<<VERDICT>>>")
print(json.dumps(verdict, ensure_ascii=False, indent=2))
print("<<<END>>>")
PY
