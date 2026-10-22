import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ultracore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ultracore.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 20
        versionName = "2.3.0"
    }

    signingConfigs {
        // Fixed debug key committed to the repo: every CI build is signed with the same key,
        // so a new APK installs over the old one without uninstalling.
        getByName("debug") {
            storeFile = file("ultracore-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Real release key: comes only from GitHub Actions secrets (never committed).
        // Without them (e.g. a fork) release falls back to the debug key so the build still works.
        val releaseStore = System.getenv("RELEASE_KEYSTORE_PATH")
        if (!releaseStore.isNullOrBlank() && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // R8 shrinks and optimises the code: Compose runs noticeably smoother than in debug.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        // Release lint must not break the CI build; warnings are reviewed separately.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.graphics.shapes)
    // Applies the baseline profiles shipped inside Compose libraries also for sideloaded APKs.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
