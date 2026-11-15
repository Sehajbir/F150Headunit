plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.f150headunit.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.f150headunit.app"
        // The production target is an Android 14 (API 34) 1280x720 head unit.
        // minSdk 29 keeps the APK installable on bench tablets for development.
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Intentionally no third-party dependencies:
// everything in this app is built directly on the Android framework.
dependencies {
}
