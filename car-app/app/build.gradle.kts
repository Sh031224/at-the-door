plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ./gradlew :app:assembleDebug -Patthedoor.useFakes=true  → SDK 대신 가짜 구현으로 실행(화면에 ‘모의 응답’ 표시)
// -Patthedoor.shareBaseUrl=http://10.0.2.2:8787       → 휴대폰 안내 서버 주소(에뮬레이터에서 호스트 PC는 10.0.2.2)
val useFakes = providers.gradleProperty("atthedoor.useFakes").orElse("false").get().toBoolean()
val shareBaseUrl = providers.gradleProperty("atthedoor.shareBaseUrl").orElse("http://10.0.2.2:8787").get()

// release 서명. ~/.gradle/gradle.properties 등에 아래 4개 값을 두면 그 키로 서명하고,
// 없으면 디버그 키로 서명한다(공모전 심사·시연용으로 설치 가능한 release APK를 만들기 위함).
//   atthedoor.storeFile, atthedoor.storePassword, atthedoor.keyAlias, atthedoor.keyPassword
val releaseStoreFile = providers.gradleProperty("atthedoor.storeFile").orNull

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

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("atthedoor.storePassword").get()
                keyAlias = providers.gradleProperty("atthedoor.keyAlias").get()
                keyPassword = providers.gradleProperty("atthedoor.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

// 공모전 제출 규칙: 저장소의 apk/ 폴더에 `팀명_앱이름_release.apk`
//   ./gradlew :app:exportReleaseApk -Patthedoor.teamName=팀명
val exportReleaseApk by tasks.registering(Copy::class) {
    group = "distribution"
    description = "release APK를 저장소 루트 apk/ 폴더에 제출 규칙 이름으로 복사한다."
    val teamName: String? = providers.gradleProperty("atthedoor.teamName").orNull
    doFirst {
        if (teamName.isNullOrBlank()) throw GradleException("-Patthedoor.teamName=팀명 을 지정하세요")
    }
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootDir.resolve("../apk"))
    rename { "${teamName}_문앞_release.apk" }
}
