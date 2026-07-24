// declare the Kotlin plugins once here so subprojects apply them without a version
plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
}
