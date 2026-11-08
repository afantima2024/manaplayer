import com.sun.source.tree.MemberSelectTree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreeScanner
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import org.gradle.api.tasks.compile.JavaCompile

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
        vectorDrawables.useSupportLibrary = true
        versionCode = 86
        versionName = "1.55.3"
        resourceConfigurations += listOf("en", "zh")
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

tasks.withType<JavaCompile>().configureEach {
    if (name.endsWith("AndroidTestJavaWithJavac")) {
        val compileTask = this
        doFirst {
            val smokeSource = compileTask.source.files.single { it.name == "OptimizedSmokeInstrumentation.java" }
            val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "A JDK is required to check optimized smoke bindings" }
            val diagnostics = DiagnosticCollector<JavaFileObject>()
            compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8).use { manager ->
                val parser = compiler.getTask(null, manager, diagnostics, listOf("-proc:none"), null,
                    manager.getJavaFileObjects(smokeSource)) as JavacTask
                val trees = parser.parse().toList()
                check(diagnostics.diagnostics.none { it.kind == Diagnostic.Kind.ERROR }) {
                    "Cannot parse optimized smoke bindings: ${diagnostics.diagnostics}"
                }
                val forbidden = sortedSetOf<String>()
                object : TreeScanner<Void?, Void?>() {
                    override fun visitMemberSelect(node: MemberSelectTree, unused: Void?): Void? {
                        val reference = node.toString()
                        if (listOf("androidx.", "kotlin.", "kotlinx.", "com.google.android.material.").any(reference::startsWith)) {
                            forbidden += reference
                        }
                        return super.visitMemberSelect(node, unused)
                    }
                }.scan(trees, null)
                check(forbidden.isEmpty()) {
                    "Optimized smoke must stay framework-only; R8-sensitive library references: ${forbidden.joinToString()}"
                }
            }
        }
    }
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
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
