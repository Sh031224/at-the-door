pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK. 인증 없이 받을 수 있다.
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "at-the-door"

include(":app")      // 차량 앱 화면(Compose)과 의존성 조립
include(":domain")   // 순수 Kotlin: 이동 조건, 규칙 엔진, 여행 상태 머신, 포트 인터페이스
include(":data")     // 시설 데이터 로더, 여행 상태 저장
include(":platform") // Pleos SDK·Car API 어댑터와 가짜 구현, 공유 링크 API 클라이언트
