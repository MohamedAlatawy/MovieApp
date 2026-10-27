import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// Do not log this value. The ignored local file is optional for builds and tests.
val tmdbToken = localProperties.getProperty("tmdb.readAccessToken", "").trim()
require(tmdbToken.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "._-" }) {
    "tmdb.readAccessToken must contain only bearer-token characters."
}

android {
    namespace = "com.example.movieapp.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"$tmdbToken\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}

ksp {
    arg("room.schemaLocation", file("schemas").path)
}
