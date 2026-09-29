import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load secrets from secrets.properties (gitignored). Falls back to empty strings
// so the project still builds without the file (e.g. in CI without credentials).
val secretsFile = rootProject.file("examples/example-deferred-only/secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.postedapp.posted.example.deferred"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.postedapp.posted.example.deferred"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "POSTED_API_KEY", "\"${secrets["POSTED_API_KEY"] ?: ""}\"")
        buildConfigField("String", "POSTED_APP_ID", "\"${secrets["POSTED_APP_ID"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // posted-sdk
    implementation(project(":posted-sdk"))

    // Splash screen API (backport to API 23+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
