plugins {
    id ("org.jetbrains.kotlin.native.cocoapods") version "2.1.20" apply false
    id ("org.jetbrains.kotlin.multiplatform") version "2.1.20" apply false
    id ("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id ("com.gradle.plugin-publish") version "1.3.1" apply false
}

allprojects {
    group = "qlarr"
    repositories {
        mavenCentral()
    }
}