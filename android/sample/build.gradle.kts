plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.yudistirosaputro.dimock.sample"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yudistirosaputro.dimock.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // The per-variant switch an adopter copies. Defined in defaultConfig so EVERY variant has a value -
        // leave one variant without it and manifest merging fails on the unresolved ${dimockEnabled}.
        // A flavour that must never carry dimock overrides it, e.g.
        //   productFlavors { create("production") { manifestPlaceholders["dimockEnabled"] = "false" } }
        // which makes even productionDebug inert while still compiling against the same API.
        manifestPlaceholders["dimockEnabled"] = "true"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The integration every adopter copies: debug gets the real thing, release gets the no-op.
    debugImplementation(project(":dimock-ui"))
    releaseImplementation(project(":dimock-okhttp-no-op"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
}
