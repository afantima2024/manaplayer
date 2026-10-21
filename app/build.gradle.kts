plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.macarmusic.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.macarmusic.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 38
        versionName = "1.24.0"
        testInstrumentationRunner = if (providers.gradleProperty("optimizedSmoke").orNull == "true") {
            "com.macarmusic.app.OptimizedSmokeInstrumentation"
        } else {
            "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        dex {
            // Preserve direct DEX mapping; lowering minSdk otherwise enables ZIP compression.
            useLegacyPackaging = false
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

val exportDebugApk by tasks.registering(Copy::class) {
    group = "build"
    description = "Export the unoptimized developer APK separately from the formal release."
    dependsOn("packageDebug")
    from(layout.buildDirectory.dir("outputs").map { it.dir("apk").dir("debug").file("app-debug.apk") })
    into(rootProject.layout.projectDirectory.dir("dist").dir("debug"))
    rename("app-debug.apk", "manaplayer.apk")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    dependsOn(exportDebugApk)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
