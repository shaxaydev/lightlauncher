plugins {
    id("com.android.application")
}

val releaseKey = rootProject.file(".signing/doma.jks")
val releasePassword = rootProject.file(".signing/password")

android {
    namespace = "uz.shaxay.tvhome"
    compileSdk = 35
    defaultConfig {
        applicationId = "uz.shaxay.tvhome"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "1.3.3"
        testInstrumentationRunner = "uz.shaxay.tvhome.TvSmokeInstrumentation"
    }
    testBuildType = "release"
    if (releaseKey.isFile && releasePassword.isFile) {
        signingConfigs.create("localRelease") {
            storeFile = releaseKey
            storePassword = releasePassword.readText().trim()
            keyAlias = "doma"
            keyPassword = storePassword
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("localRelease")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
