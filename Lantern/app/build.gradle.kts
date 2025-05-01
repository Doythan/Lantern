plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("androidx.room")

}

android {
    namespace = "com.ssafy.lantern"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ssafy.lantern"
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
    kotlinOptions {
        jvmTarget = "11"
    }

    room {
        // schemas 폴더를 미리 생성해 두세요: app/schemas/
        schemaDirectory("$projectDir/schemas")
    }

    buildFeatures {
        viewBinding = true
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

    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")      // core
    ksp         ("androidx.room:room-compiler:$roomVersion")     // annotation processor
    implementation("androidx.room:room-ktx:$roomVersion")         // Kotlin Coroutines 확장
    // 필요시
    // implementation("androidx.room:room-rxjava2:$roomVersion")
    // implementation("androidx.room:room-guava:$roomVersion")

    // 테스트/페이징 등 옵션
    testImplementation("androidx.room:room-testing:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")


}