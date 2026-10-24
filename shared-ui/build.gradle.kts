import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val commonUiSources = fileTree("src/commonMain/kotlin") { include("**/*.kt") }
val checkSharedUiBoundaries by tasks.registering {
    group = "verification"
    description = "Reject Android, JVM and host imports in shared UI."
    inputs.files(commonUiSources)
    val sources = commonUiSources
    val moduleDirectory = projectDir
    doLast {
        val forbidden = Regex("""(?m)^\h*import\h+(android\.|java\.|javax\.|org\.json\.|com\.example\.boschlaser\.(?!shared\.|ui\.))""")
        val violations = sources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "${file.relativeTo(moduleDirectory)}:${index + 1}: $line" else null
            }
        }
        check(violations.isEmpty()) { "Platform/host imports in shared UI:\n${violations.joinToString("\n")}" }
    }
}

tasks.named("check") { dependsOn(checkSharedUiBoundaries) }

kotlin {
    android {
        namespace = "com.example.boschlaser.ui"
        compileSdk = 37
        minSdk = 31
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
