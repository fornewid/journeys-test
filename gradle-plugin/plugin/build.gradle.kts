plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

group = "io.github.fornewid.journeys-test"
version = "0.1.0"

dependencies {
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("journeys") {
            id = "io.github.fornewid.journeys-test"
            implementationClass = "io.github.fornewid.gradle.plugins.journeystest.JourneysPlugin"
            displayName = "Journeys Test"
            description = "Runs natural-language journey XML on a device via your CLI agent, reported as JUnit/Gradle tests. Follows Android Studio's journeysTest layout."
        }
    }
}
