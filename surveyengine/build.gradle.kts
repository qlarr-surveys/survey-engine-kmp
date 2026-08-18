import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Base64

plugins {
    id ("org.jetbrains.kotlin.multiplatform")
    id ("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
    id("org.jetbrains.kotlin.native.cocoapods")
}

// Embeds the navigation engine scripts (common_script.js + initial_script.js) into a generated
// Kotlin source for the JS target. Kotlin/JS can't read commonMain resources at runtime the way the
// JVM reads them off the classpath, so we Base64-encode them (avoids all string-escaping issues) and
// decode at runtime. commonMain/resources/scripts is the single source of truth.
// Single source of truth for the survey-engine-script version. Used both for the JS target's npm
// dependency and for the build-time fetch that embeds the compiled script into JVM/iOS resources.
val surveyEngineScriptVersion = "0.2.1"

// JVM (GraalVM classpath) and iOS (app bundle) can't npm-install at runtime, so they load the
// compiled `survey-engine-script.min.js` as an embedded resource. This task pulls that exact file
// from the published npm package (registry tarball) and drops it into a generated resources dir,
// keeping npm the single source of truth instead of a committed copy.
val fetchSurveyEngineScript by tasks.registering {
    val version = surveyEngineScriptVersion
    val outDir = layout.buildDirectory.dir("generated/surveyEngineScript")
    val tgzFile = layout.buildDirectory.file("tmp/survey-engine-script-$version.tgz")
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val url = "https://registry.npmjs.org/@qlarr/survey-engine-script/-/survey-engine-script-$version.tgz"
        val tgz = tgzFile.get().asFile
        tgz.parentFile.mkdirs()
        uri(url).toURL().openStream().use { input -> tgz.outputStream().use { input.copyTo(it) } }

        val dest = outDir.get().dir("survey-engine-script").asFile
        dest.deleteRecursively()
        dest.mkdirs()
        copy {
            from(tarTree(resources.gzip(tgz)))
            include("package/dist/survey-engine-script.min.js")
            includeEmptyDirs = false
            eachFile { path = name } // flatten package/dist/ into the resource root
            into(dest)
        }
        logger.lifecycle("fetched survey-engine-script@$version into ${dest.absolutePath}")
    }
}

val generateJsScriptResources by tasks.registering {
    val scriptsDir = layout.projectDirectory.dir("src/commonMain/resources/scripts")
    val outputDir = layout.buildDirectory.dir("generated/scriptResources/jsMain/kotlin")
    inputs.dir(scriptsDir)
    outputs.dir(outputDir)
    doLast {
        val encoder = Base64.getEncoder()
        fun b64(name: String): String =
            encoder.encodeToString(scriptsDir.file(name).asFile.readText(Charsets.UTF_8).toByteArray(Charsets.UTF_8))
        val common = b64("common_script.js")
        val initial = b64("initial_script.js")
        val pkgDir = outputDir.get().dir("com/qlarr/surveyengine/scriptengine").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("ScriptResourcesGenerated.kt").writeText(
            "package com.qlarr.surveyengine.scriptengine\n\n" +
                "internal object ScriptResourcesGenerated {\n" +
                "    const val COMMON_SCRIPT_B64: String = \"" + common + "\"\n" +
                "    const val INITIAL_SCRIPT_B64: String = \"" + initial + "\"\n" +
                "}\n"
        )
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(17)
    jvm()
    js(IR) {
        moduleName = "qlarr-survey-engine"
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            resources.srcDirs("src/commonMain/resources", fetchSurveyEngineScript)
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
            kotlin.srcDir(generateJsScriptResources)
            dependencies {
                implementation(npm("@qlarr/survey-engine-script", surveyEngineScriptVersion))
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
        dependsOn(fetchSurveyEngineScript)
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
                from(fetchSurveyEngineScript)
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
        version = "0.2.1"
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
// Assembles a publish-ready npm package from the Kotlin/JS production library.
// The compiled library does `require('@qlarr/survey-engine-script')`, which Kotlin already declares
// as a regular dependency in the generated package.json, so npm pulls it from the registry at
// install time. This task only fixes up the package name and strips dead resources.
val assembleNpmPackage by tasks.registering {
    dependsOn("jsNodeProductionLibraryDistribution")
    val distDir = layout.buildDirectory.dir("dist/js/productionLibrary")
    val outDir = layout.buildDirectory.dir("npmPackage")
    inputs.dir(distDir)
    outputs.dir(outDir)
    doLast {
        val src = distDir.get().asFile
        val out = outDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        src.copyRecursively(out, overwrite = true)

        // The embedded navigation scripts are Base64-inlined into the compiled JS, so the copied
        // `scripts/` resource folder is dead weight in the package. Likewise, the JS target resolves
        // survey-engine-script via its npm dependency, so the resource copy (embedded for JVM/iOS) is
        // dead weight here too.
        out.resolve("scripts").deleteRecursively()
        out.resolve("survey-engine-script").deleteRecursively()

        // Publish under the scoped name.
        val pkg = out.resolve("package.json")
        pkg.writeText(
            pkg.readText()
                .replace("\"name\": \"qlarr-survey-engine\"", "\"name\": \"@qlarr/survey-engine\"")
        )
        logger.lifecycle("npm package assembled at: ${out.absolutePath}")
    }
}

group = "com.qlarr.survey-engine"
version = "0.2.1"
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