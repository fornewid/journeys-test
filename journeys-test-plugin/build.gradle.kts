plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
    id("com.vanniktech.maven.publish") version "0.34.0"
}

val GROUP: String by project
val VERSION_NAME: String by project
group = GROUP
version = VERSION_NAME

dependencies {
    // On the plugin (buildscript) classpath; the journeysTest task runs the JUnit Platform
    // launcher in-process, so these resolve from Maven Central for consumers of the published plugin.
    implementation(platform("org.junit:junit-bom:5.11.4"))
    implementation("org.junit.platform:junit-platform-launcher")
    implementation("org.junit.platform:junit-platform-engine")
    implementation("org.junit.platform:junit-platform-reporting")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website = "https://github.com/fornewid/journeys-test"
    vcsUrl = "https://github.com/fornewid/journeys-test"
    plugins {
        create("journeys") {
            id = "io.github.fornewid.journeys-test"
            implementationClass = "io.github.fornewid.gradle.plugins.journeystest.JourneysPlugin"
            displayName = "Journeys Test"
            description = "Run natural-language journey XML on a device via your CLI agent, reported as standard JUnit/Gradle tests. Follows Android Studio's journeysTest layout."
            tags = listOf("android", "testing", "ui-testing", "journeys", "agent")
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
