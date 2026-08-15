plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nova.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nova.assistant"
        minSdk = 26          // Android 8.0+ — needed for reliable background/foreground service behavior
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-mvp"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Force every Kotlin compile task onto JVM target 17 (belt-and-braces —
// KSP does not have kapt's separate stub-generation tasks, but this keeps
// the whole build consistent regardless).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device AI brain — runs Gemma 4 locally, fully offline, zero cost.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    // Local storage for Nova's memory and learned routines (Room database).
    // Using KSP (not kapt) for annotation processing — modern, faster, and
    // avoids the JVM-target/stub-task mismatches that kapt is prone to.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
