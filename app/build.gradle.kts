plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "app.kholo.reader"
    compileSdk = 37

    defaultConfig {
        // Placeholder: pehle Play upload se pehle final hoga, uske baad badal nahi sakta.
        applicationId = "app.kholo.reader"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        generateLocaleConfig = false
        localeFilters += listOf("en", "hi")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sirf local testing ke liye. Asli release key M4 me user ke saath tay hogi.
            signingConfig = signingConfigs.getByName("debug")
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

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.pdf.viewer.fragment)
    implementation(libs.androidx.tracing)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.core)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    testImplementation(libs.coroutines.test)
}
