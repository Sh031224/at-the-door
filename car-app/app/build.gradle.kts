plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ./gradlew :app:assembleDebug -Patthedoor.useFakes=true  → SDK 대신 가짜 구현으로 실행(화면에 ‘모의 응답’ 표시)
// -Patthedoor.shareBaseUrl=http://10.0.2.2:8787       → 휴대폰 안내 서버 주소(에뮬레이터에서 호스트 PC는 10.0.2.2)
val useFakes = providers.gradleProperty("atthedoor.useFakes").orElse("false").get().toBoolean()
val shareBaseUrl = providers.gradleProperty("atthedoor.shareBaseUrl").orElse("http://10.0.2.2:8787").get()

android {
    namespace = "app.atthedoor.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.atthedoor"
        // 차량 사이버 보안 요건(사이닝 키 로테이션·리니지)에 따라 28 이상
        minSdk = 28
        // Pleos Connect Emulator v2.1.0 시스템 이미지가 API 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "USE_FAKES", useFakes.toString())
        buildConfigField("String", "SHARE_BASE_URL", "\"$shareBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    useLibrary("android.car")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":platform"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
}
