import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val runtimeDir = rootProject.layout.projectDirectory.dir("runtime")
val maaendRepoDir = rootProject.layout.projectDirectory.dir("../MaaEnd")
val generatedAssetsDir = layout.buildDirectory.dir("generated/assets")
val generatedJniLibsDir = layout.buildDirectory.dir("generated/jniLibs")

val prepareBundledRuntimeAssets by tasks.registering(Sync::class) {
    from(runtimeDir)
    from(maaendRepoDir.dir("assets/resource/pipeline/Common/__Private")) {
        into("private_pipeline/resource/CommonPrivate")
    }
    from(maaendRepoDir.dir("assets/resource_adb/pipeline/Common/__Private")) {
        into("private_pipeline/resource_adb/CommonPrivate")
    }
    into(generatedAssetsDir.map { it.dir("bundled_runtime") })
    includeEmptyDirs = true
}

val prepareBundledRuntimeJniLibs by tasks.registering(Sync::class) {
    from(runtimeDir.dir("maafw"))
    into(generatedJniLibsDir.map { it.dir("arm64-v8a") })
    include("*.so")
    includeEmptyDirs = false
}

android {
    namespace = "com.maaend.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maaend.android"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.srcDirs(
        "src/main/assets",
        maaendRepoDir.dir("assets"),
        generatedAssetsDir,
    )
    sourceSets.getByName("main").jniLibs.srcDirs(generatedJniLibsDir)

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(prepareBundledRuntimeAssets)
    dependsOn(prepareBundledRuntimeJniLibs)
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("net.java.dev.jna:jna:5.18.1") { artifact { type = "aar" } }

    testImplementation("junit:junit:4.13.2")
}
