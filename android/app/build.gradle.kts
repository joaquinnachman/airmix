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
        versionCode = 13
        versionName = "0.4.8"
    }

    // Firma fija guardada en el repo: si cada compilación usara una clave nueva, Android rechaza la
    // actualización ("conflicto con un paquete") y hay que desinstalar. No cambiar este archivo.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("airmix-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
