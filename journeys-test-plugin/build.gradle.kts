import org.gradle.plugin.compatibility.compatibility

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.spotless)
    `java-gradle-plugin`
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

dependencies {
    // On the plugin (buildscript) classpath; the journeysTest task runs the JUnit Platform
    // launcher in-process, so these resolve from Maven Central for consumers of the published plugin.
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.platform.launcher)
    implementation(libs.junit.platform.engine)
    implementation(libs.junit.platform.reporting)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
}

tasks.test {
    // This project's own TestEngine is on the test runtime classpath; keep it from
    // taking over the plugin's unit tests.
    useJUnitPlatform { includeEngines("junit-jupiter") }
    testLogging { events("failed") }
    // Tests claim the device the same way a real run does. Keep that claim in the build directory
    // so running them never queues behind, or blocks, a journey running on this machine.
    systemProperty(
        "journeys.deviceLockDir",
        layout.buildDirectory
            .dir("device-locks")
            .get()
            .asFile.path,
    )
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
}

gradlePlugin {
    website = "https://github.com/fornewid/journeys-test"
    vcsUrl = "https://github.com/fornewid/journeys-test"
    plugins {
        create("journeys") {
            id = "io.github.fornewid.journeys-test"
            implementationClass = "io.github.fornewid.gradle.plugins.journeystest.JourneysPlugin"
            displayName = "Journeys Test"
            description = "Run natural-language journey XML on a device via your CLI agent, reported as " +
                "standard JUnit/Gradle tests. Follows Android Studio's journeysTest layout."
            tags = listOf("android", "testing", "ui-testing", "journeys", "agent")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
