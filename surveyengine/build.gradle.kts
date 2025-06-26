import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id ("org.jetbrains.kotlin.multiplatform")
    id ("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(17)
    jvm()
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            resources.srcDirs("src/commonMain/resources")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
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

        val jsMain by getting {
            dependencies {
                implementation(devNpm("survey-engine-script", file("src/commonMain/resources/survey-engine-script")))
            }
        }




        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }


        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                // Any testing libraries specific to JS
            }
        }
    }

    tasks.matching { it.name.contains("ProcessResources") }.configureEach {
        if (this is Copy) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }



    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        if (konanTarget.family == org.jetbrains.kotlin.konan.target.Family.IOS) {
            val copyTask = tasks.register<Copy>("copyTestResourcesFor${targetName}") {
                from("src/commonTest/resources")
                into("build/bin/${targetName}/debugTest/test-resources")
                mustRunAfter(tasks.withType<KotlinCompile>())
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
            val copyTask1 = tasks.register<Copy>("copyTestResourcesFor${targetName}1") {
                from("src/commonMain/resources")
                into("build/bin/${targetName}/debugTest")
                mustRunAfter(tasks.withType<KotlinCompile>())
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }

            if (konanTarget.family == org.jetbrains.kotlin.konan.target.Family.IOS &&
                tasks.findByName("${targetName}Test") != null) {
                tasks.named("${targetName}Test") {
                    dependsOn(copyTask)
                }
                tasks.named("${targetName}Test") {
                    dependsOn(copyTask1)
                }
            }
        }
    }

    cocoapods {
        // Required properties
        // Specify the required Pod version here
        // Otherwise, the Gradle project version is used
        version = "0.1.6"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"

        // Optional properties
        // Configure the Pod name here instead of changing the Gradle project name
        name = "SurveyEngineCocoaPod"

        framework {
            baseName = "SurveyEngine"
            isStatic = false
        }

        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

}
group = "com.qlarr.survey-engine"
version = "0.1.6"
publishing {
    publications {
        // This creates a publication for each target
        withType<MavenPublication> {
            // Set the artifactId for all publications
            artifactId = "surveyengine"
        }
    }
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