plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    // Commenting out native targets that are causing build issues
    // linuxX64()
    // macosX64()
    // macosArm64()
    // mingwX64()
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":scriptengine"))
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                // JVM-specific dependencies if needed
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val jsMain by getting {
            dependencies {
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                // Any testing libraries specific to JS
            }
        }
    }
}
group = "com.qlarr.survey-engine"
version = "0.1.6"
publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/qlarr-surveys/survey-engine")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}