plugins {
    id("com.android.application")
}

android {
    namespace = "com.zoryvo.hub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zoryvo.hub"
        minSdk = 21
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"https://raw.githubusercontent.com/aseel90/zoryvo-app-hub/main/catalog/apps.json\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 1.13.1 keeps support for Android 5.x while providing FileProvider.
    implementation("androidx.core:core:1.13.1")
}
