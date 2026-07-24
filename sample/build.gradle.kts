plugins {
    id("io.github.fornewid.journeys-test")
}

journeys {
    // device-free demo agent (reports each step PASSED); swap for a real agent + device for actual UI tests
    agentCommand.set("bash ${rootDir}/tools/echo-agent.sh")
}
