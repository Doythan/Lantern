plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

android {
    namespace = "com.ssafy.lantern"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ssafy.lantern"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Room 스키마 위치 설정
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // KSP-generated sources
    applicationVariants.all {
        val variantName = name
        kotlin.sourceSets.getByName(variantName) {
            kotlin.srcDir("build/generated/ksp/$variantName/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room
    val roomVersion = "2.6.0"
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)

    // Jetpack Compose
    implementation(libs.androidx.ui.v180)
    implementation(libs.androidx.material)
    implementation(libs.androidx.ui.tooling.preview.v180)
    implementation(libs.androidx.activity.compose.v170)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Google Sign-In (기존 play-services-auth 버전이 있다면 최신 버전으로 업데이트 고려)
    implementation("com.google.android.gms:play-services-auth:21.1.0") // 최신 안정 버전 확인
    implementation("com.google.firebase:firebase-auth:22.2.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Credential Manager (이제 직접 사용하지 않지만, 다른 곳에서 필요할 수 있으므로 일단 유지)
    // implementation("androidx.credentials:credentials:1.3.0-alpha01")
    // implementation("androidx.credentials:credentials-play-services-auth:1.3.0-alpha01")

    // Hilt (버전은 프로젝트 상황에 맞게 조정)
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    // ViewModel 주입 (@HiltViewModel)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Accompanist Permissions (권한 요청 UI)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0") // 최신 버전 확인
}
