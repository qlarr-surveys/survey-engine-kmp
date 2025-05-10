plugins {
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("com.gradle.plugin-publish") version "1.3.0" apply false
}

allprojects {
    group = "qlarr"
    repositories {
        mavenCentral()
    }
}