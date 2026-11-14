import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = rootProject.file("app/release.p12")

android {
    namespace = "app.henachti"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.henachti"
        minSdk = 26
        targetSdk = 36
        versionCode = 27
        versionName = "2.8.7"
    }

    // direct = the sideloaded APK (keeps the original id so it updates in place, full uninstall protection).
    // play   = the Google Play bundle (its own permanent id, Play-policy-safe permission set).
    flavorDimensions += "channel"
    productFlavors {
        create("direct") {
            dimension = "channel"
        }
        create("play") {
            dimension = "channel"
            applicationId = "app.ukshartem"
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "henachti"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                // All three schemes so every installer and Android version accepts the file.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            // Release builds are never debuggable. R8 shrinks and optimizes (Play asks for optimized
            // DEX from 2027), but names are kept: the code is public anyway and crash traces stay clear.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
        // Debug hooks (for the emulator screenshot run) with the same R8 processing as release,
        // so the run also proves the optimized code does not break any screen.
        create("qa") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Lint runs in CI and publishes a report; it never blocks the build.
        abortOnError = false
        checkReleaseBuilds = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    // Pure calendar arithmetic, no I/O. 3.x renamed its classes; kept on 2.5.0 (no security surface).
    implementation("com.kosherjava:zmanim:2.5.0")
}
