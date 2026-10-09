import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

// Optional build-time secrets (gitignored; template committed as
// secrets.properties.template). Every key is optional, the build always
// succeeds without the file.
val secretsProperties = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

// Server for the usage statistics, used only after the user agreed. Set
// TELEMETRY_URL in secrets.properties to report somewhere else; an empty value
// builds an app without statistics that never asks for them.
// Placeholder: no statistics server unless you set one in secrets.properties.
val telemetryUrl: String = (secretsProperties["TELEMETRY_URL"] as? String)?.trim() ?: ""

android {
    namespace = "com.renotify.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.renotify.oss"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.8.6"

        buildConfigField("String", "TELEMETRY_URL", "\"$telemetryUrl\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on: Play flags a bundle whose code is not shrunk and obfuscated,
            // and the mapping ships inside the AAB so its crash reports stay
            // readable. Our own crash pings need retrace with the mapping file.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only where a keystore is configured; without one the
            // release build is unsigned, which is what F-Droid expects.
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // Listener watchdog + daily telemetry ping.
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
}
