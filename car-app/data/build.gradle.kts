plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.atthedoor.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 저장소 루트의 data/venues 를 그대로 assets 로 넣어 웹과 같은 데이터를 쓴다.
    sourceSets {
        getByName("main").assets.srcDir("../../data/venues")
    }
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}
