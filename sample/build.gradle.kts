plugins {
    id("com.android.application") version "9.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    id("io.github.fornewid.journeys-test")
}

android {
    namespace = "io.github.fornewid.journeys.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.fornewid.journeys.sample"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

journeys {
    agentCommand.set(
        providers.gradleProperty("journeyAgentCommand").orElse(
            "claude --no-session-persistence --allowedTools 'Bash(android *)' 'Bash(adb *)' -p",
        ),
    )
    timeoutSeconds.set(300)
}

tasks.named("journeysTest") {
    dependsOn("installDebug")
}
