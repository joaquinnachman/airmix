plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ar.joaquin.airmix"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.joaquin.airmix"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.4.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Solo para las capturas automáticas en CI (no van dentro del APK).
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
// La app no tiene dependencias externas: solo el SDK de Android + Kotlin stdlib (las de test no se empaquetan).
