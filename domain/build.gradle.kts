plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.paging:paging-common:${libs.versions.paging.get()}")
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
