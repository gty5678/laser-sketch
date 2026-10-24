import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
}

val commonSources = fileTree("src/commonMain/kotlin") { include("**/*.kt") }
val checkSharedBoundaries by tasks.registering {
    group = "verification"
    description = "Reject platform and UI dependencies in the shared core."
    inputs.files(commonSources)
    val sources = commonSources
    val moduleDirectory = projectDir
    doLast {
        val forbidden = Regex("""(?m)^\h*import\h+(android\.|androidx\.|java\.|javax\.|org\.json\.|.*\.compose\.|com\.example\.boschlaser\.(?!shared\.))""")
        val violations = sources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(moduleDirectory)}:${index + 1}: $line" else null
            }
        }
        check(violations.isEmpty()) { "Platform/UI imports in shared core:\n${violations.joinToString("\n")}" }
    }
}
tasks.named("check") { dependsOn(checkSharedBoundaries) }

kotlin {
    android {
        namespace = "com.example.boschlaser.shared"
        compileSdk = 37
        minSdk = 31
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
