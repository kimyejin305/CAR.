plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.example.pleoscar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pleoscar"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
    implementation(libs.androidx.app.automotive)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Firebase 버전 관리
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))

    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore")

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // NAVER Maps SDK
    implementation("com.naver.maps:map-sdk:3.23.0")
    dependencies {
        // 미디어 브라우저 및 세션 호환성 라이브러리 추가
    }
}