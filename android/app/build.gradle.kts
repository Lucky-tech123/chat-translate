plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val translateUrl: String = (project.findProperty("translateUrl") as String?)
    ?: "https://project--3e310989-5d32-407f-8925-94df8ec83889.lovable.app/api/public/translate"

android {
    namespace = "com.lucky.floattranslate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lucky.floattranslate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TRANSLATE_URL", "\"$translateUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
}
