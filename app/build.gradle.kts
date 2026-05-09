plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cherin.edupsych"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cherin.edupsych"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Force the Kotlin compiler toolchain to JDK 17 (matches our compileOptions
// and the installed Microsoft OpenJDK 17). Without this, Kotlin 2.2 tries
// to provision JetBrains Runtime 21, which is unavailable.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ML Kit on-device translation (English -> Korean, ~30MB model)
    implementation(libs.mlkit.translate)
    implementation(libs.play.services.tasks.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    // Home-screen widget (Glance) + daily refresh worker
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}
