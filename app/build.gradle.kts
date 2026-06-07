import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.lanscreendupe"
    compileSdk = 35
    // NDK r27 or higher is required for proper 16KB alignment
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.example.lanscreendupe"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // run ./gradlew assembleRelease in terminal to build a release
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    // This tells Android to look for XML layouts and not expect Compose
    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs {
            // "false" might help making shared libraries be 16 KB aligned for compatibility with modern devices
            useLegacyPackaging = true
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Using LiveKit as the primary RTC solution (includes its own WebRTC)
    implementation(libs.livekit.android)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

}