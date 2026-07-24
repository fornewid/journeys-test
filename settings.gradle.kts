pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("gradle-plugin")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

includeBuild("gradle-plugin") // also substitutes the engine dependency (the pluginManagement one only resolves the plugin)

rootProject.name = "journeys-test"
include(":sample")
