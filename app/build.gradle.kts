plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kash.app"
    compileSdk = 35

    // CI passes -PbuildNumber so every published build installs as an update over the last one.
    val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.kash.app"
        minSdk = 30 // Android 11+
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // The signing key never lives in the repository. CI decodes it from an encrypted GitHub secret
    // into a temp file and passes its path here. Without it, release builds fall back to the debug key.
    val keystorePath: String? = System.getenv("KASH_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("kash") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KASH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KASH_KEY_ALIAS") ?: "kash"
                keyPassword = System.getenv("KASH_KEYSTORE_PASSWORD")
            }
        }
    }

    // The privacy policy shown in the app is the same file published in the repository.
    sourceSets["main"].assets.srcDir("../legal")

    buildTypes {
        release {
            signingConfig = if (keystorePath != null) signingConfigs.getByName("kash") else signingConfigs.getByName("debug")
            // Obfuscation + shrinking: harder to reverse-engineer, smaller download.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core:parser"))
    implementation(project(":core:insights"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
