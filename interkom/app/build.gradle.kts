plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
}
android {
 namespace = "com.example.interkom"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.example.interkom"
  minSdk = 26
  targetSdk = 36
  versionCode = 4
  versionName = "0.3.1-test"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("io.livekit:livekit-android:2.30.0")
 implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
 implementation("io.github.jan-tennert.supabase:auth-kt")
 implementation("io.github.jan-tennert.supabase:postgrest-kt")
 implementation("io.github.jan-tennert.supabase:storage-kt")
 implementation("io.ktor:ktor-client-android:3.0.3")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
 implementation("io.coil-kt:coil-compose:2.7.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
