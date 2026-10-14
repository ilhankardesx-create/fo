plugins {
    id("com.android.application")
}

android {
    namespace = "com.folddesk.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.folddesk.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-prototype"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.window:window-java:1.5.1")
}
