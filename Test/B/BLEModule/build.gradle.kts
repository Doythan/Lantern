// ────────────────────────────────────
// BLEModule/build.gradle.kts
// ────────────────────────────────────

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Android Gradle Plugin
        classpath("com.android.tools.build:gradle:7.4.2")
        // Hilt Gradle Plugin
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.44")
    }
}

plugins {
    // Kotlin DSL 지원용 (공통)
    kotlin("android") version "1.8.0" apply false
    kotlin("kapt")    version "1.8.0" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
