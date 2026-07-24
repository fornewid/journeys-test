plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.github.fornewid.journeys-test"
version = "0.1.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation(platform("org.junit:junit-bom:5.11.4"))
    implementation("org.junit.platform:junit-platform-engine")
    implementation("org.junit.platform:junit-platform-launcher")
    implementation("org.junit.platform:junit-platform-reporting")
}
