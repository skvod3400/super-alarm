plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skvod3400.superalarm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skvod3400.superalarm"
        minSdk = 31
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Fixed debug key, so every test build can be installed over the previous one
    // without losing saved alarms.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The release key is never stored in the repository: CI restores it from GitHub secrets.
        create("release") {
            System.getenv("RELEASE_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A separate app id, so test builds and the RuStore version can live side by side.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Супербудильник (тест)"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["appLabel"] = "@string/app_name"
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
