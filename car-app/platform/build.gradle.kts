plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.atthedoor.platform"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // CarUxRestrictionsManager(android.car) 사용
    useLibrary("android.car")
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.speechtotext)
    implementation(libs.pleos.texttospeech)
    implementation(libs.pleos.fusedlocation)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
