plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val hoddVersionCode = providers.environmentVariable("HODD_VERSION_CODE").orElse("1").get().toInt()
val hoddSigningPath = providers.environmentVariable("HODD_KEYSTORE_PATH").orNull
val hoddSigningPassword = providers.environmentVariable("HODD_KEYSTORE_PASSWORD").orNull
val hoddSigningAlias = providers.environmentVariable("HODD_KEY_ALIAS").orNull
val hoddKeyPassword = providers.environmentVariable("HODD_KEY_PASSWORD").orNull
val hoddSigningReady = listOf(hoddSigningPath, hoddSigningPassword, hoddSigningAlias, hoddKeyPassword).all { !it.isNullOrBlank() }
check(listOf(hoddSigningPath, hoddSigningPassword, hoddSigningAlias, hoddKeyPassword).all { it.isNullOrBlank() } || hoddSigningReady) {
    "Provide all four Hodd signing variables together."
}
android {
    namespace = "no.vardir.hodd"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "no.vardir.hodd"
        minSdk = 26
        targetSdk = 35
        versionCode = hoddVersionCode
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hoddSigningReady) create("persistent") {
            storeFile = file(hoddSigningPath!!)
            storePassword = hoddSigningPassword
            keyAlias = hoddSigningAlias
            keyPassword = hoddKeyPassword
        }
    }
    buildTypes {
        if (hoddSigningReady) {
            getByName("debug").signingConfig = signingConfigs.getByName("persistent")
            getByName("release").signingConfig = signingConfigs.getByName("persistent")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
tasks.configureEach {
    if (name == "preReleaseBuild") doFirst {
        check(hoddSigningReady) { "Release APKs require the persistent Hodd signing key. See docs/SIGNING.md." }
    }
}
dependencies {
    implementation(project(":core"))
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
