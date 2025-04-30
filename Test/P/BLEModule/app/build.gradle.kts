plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.example.blemodule"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.blemodule"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true // ViewBinding 사용 추천
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // --- 코어 & UI (libs.versions.toml 별칭 사용) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx) // 추가된 별칭 사용
    implementation(libs.androidx.constraintlayout)

    // --- 비동기 처리 (libs.versions.toml 별칭 사용) ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core) // 추가된 별칭 사용

    // --- 아키텍처 컴포넌트 (Lifecycle) (libs.versions.toml 별칭 사용) ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    // Lifecycle Annotation Processor (libs.versions.toml 별칭 사용)
    kapt(libs.androidx.lifecycle.compiler) // kapt 사용 시

    // --- 테스트 (libs.versions.toml 별칭 사용) ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}