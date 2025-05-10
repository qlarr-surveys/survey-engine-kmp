plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    js(IR) {
        moduleName = "survey-engine-mvp"
        browser()
        nodejs()
        binaries.executable()
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
                implementation(project(path = ":surveyengine"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                // JVM-specific dependencies if needed
                // Added GraalVM JavaScript dependencies
                implementation("org.graalvm.js:js:22.3.1")
                implementation("org.graalvm.js:js-scriptengine:22.3.1")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(devNpm("survey-engine-script", file("src/commonMain/resources/survey-engine-script")))
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