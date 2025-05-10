plugins {
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
}

allprojects {
    group = "qlarr"
    repositories {
        mavenCentral()
    }
}