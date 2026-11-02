plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

version = "1.0.0"
val sdkVersion = project.version.toString()

android {
    namespace = "com.postedapp.posted"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
        buildConfigField("String", "SDK_HEADER_VALUE", "\"android/$sdkVersion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

detekt {
    config.from(rootProject.file("detekt-correctness.yml"))
    buildUponDefaultConfig = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.installreferrer)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates("com.postedapp", "posted-sdk")
    publishToMavenCentral()
    signAllPublications()
    pom {
        name = "posted-sdk Android"
        description = "Reports app installs to Posted for ad attribution. One call, reportInstall, sent once per install."
        url = "https://github.com/thepostedapp/posted-sdk-android"
        licenses {
            license {
                name = "The MIT License"
                url = "http://www.opensource.org/licenses/mit-license.php"
            }
        }
        scm {
            connection = "scm:git:git://github.com/thepostedapp/posted-sdk-android.git"
            developerConnection = "scm:git:ssh://github.com/thepostedapp/posted-sdk-android.git"
            url = "https://github.com/thepostedapp/posted-sdk-android"
        }
        developers {
            developer {
                id = "postedapp"
                name = "Posted"
                email = "yuriy@postedapp.com"
            }
        }
    }
}
