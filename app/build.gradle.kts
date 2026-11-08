plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    buildFeatures { compose = true }
    // Native Android adapters and Compose iOS use the same vector source files.
    sourceSets.getByName("main").res.srcDir("../shared-ui/src/commonMain/composeResources")
    namespace = "com.example.boschlaser"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.boschlaser"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Temporary installable release build. Replace with a private release
            // keystore before publishing or distributing production updates.
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-ui"))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.test.manifest)
    androidTestImplementation(libs.androidx.compose.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Connected tests share the app sandbox. Require an explicitly selected emulator so
// test fixtures and runner cleanup cannot touch a developer's daily phone data.
abstract class VerifyInstrumentationDevice : DefaultTask() {
    @get:Input
    abstract val adbPath: Property<String>

    @get:Input
    abstract val selectedSerial: Property<String>

    @TaskAction
    fun verify() {
        val serial = selectedSerial.get()
        check(serial.startsWith("emulator-")) {
            "Instrumented tests require a disposable emulator. Set ANDROID_SERIAL=emulator-<port>; " +
                "use the Android App Run/Debug configuration for daily testing with retained data."
        }
        val process = ProcessBuilder(adbPath.get(), "-s", serial, "shell", "getprop", "ro.kernel.qemu")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor() == 0 && output == "1") {
            "The selected instrumentation target must be a running Android emulator."
        }
    }
}

val verifyInstrumentationDevice = tasks.register<VerifyInstrumentationDevice>("verifyInstrumentationDevice") {
    adbPath.set(androidComponents.sdkComponents.adb.map { it.asFile.absolutePath })
    selectedSerial.set(providers.environmentVariable("ANDROID_SERIAL").orElse(""))
}

tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        dependsOn(verifyInstrumentationDevice)
    }
}
