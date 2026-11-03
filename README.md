# 문앞

보행이 불편한 가족과 차로 외출할 때, 동행 조건에 맞는 승하차·주차 지점과 입구까지의 동선을 출발 전 차 안에서 정하고, 차량 내비게이션에서 휴대폰 보행 안내까지 이어 주는 도착 계획 앱입니다. 현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작입니다.

- 기획·기능 명세: [`문앞_앱개발_시나리오_기능명세.md`](문앞_앱개발_시나리오_기능명세.md)
- 제출 양식: [`제출/문앞_접수양식.docx`](제출/문앞_접수양식.docx)

## 저장소 구조

```
.
├── car-app/                 차량 앱 (Android Automotive / Pleos Connect, Kotlin)
│   ├── domain/              순수 Kotlin: 이동 조건, 규칙 엔진(PlanEvaluator), 여행 상태 머신(TripMachine), 포트 인터페이스
│   ├── data/                시설 데이터 로더(VenueJson), 여행 상태 저장(TripStore, DataStore)
│   ├── platform/            Pleos SDK·Car API 어댑터(Pleos*), 가짜 구현(Fake*), 공유 링크 API 클라이언트
│   └── app/                 Compose 화면, ViewModel, 의존성 조립(AppContainer)
├── web/                     휴대폰 안내 페이지와 공유 링크 API (TypeScript, Hono, node:sqlite)
├── data/venues/             시연 시설 데이터 JSON과 입구 사진 (차량 앱 assets와 웹이 함께 사용)
└── 제출/                    공모전 제출 양식
```

의존 방향은 `app → platform, data → domain` 입니다. `domain`은 Android와 SDK에 의존하지 않으므로 수용 테스트 대부분을 JVM 단위 테스트로 검증합니다. 모든 SDK 호출은 `domain/port/Ports.kt`의 인터페이스 뒤에 있습니다.

## 차량 앱

### 준비

- Android Studio(내장 JBR 사용), Android SDK Platform 34 이상
- Pleos Connect Emulator v2.1.0: SDK Manager에 업데이트 사이트 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`(Apple Silicon)을 추가하고, API 34 "Pleos Connect v2.1.0 APIs" 이미지로 AVD를 만듭니다. **Cold boot로 설정**해야 VHAL(차량 신호)을 쓸 수 있습니다.
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 기어·도어·시트 상태를 바꿉니다.
- Pleos SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 인증 없이 받습니다(`car-app/settings.gradle.kts`).

### 빌드와 테스트

```bash
cd car-app
./gradlew :domain:test :data:testDebugUnitTest   # 규칙 엔진·상태 머신·데이터 테스트
./gradlew :app:assembleDebug                     # 실제 SDK 연결
./gradlew :app:assembleDebug -Patthedoor.useFakes=true   # SDK 대신 가짜 구현, 화면에 ‘모의 응답’ 표시
./gradlew :app:installDebug -Patthedoor.shareBaseUrl=http://10.0.2.2:8787
```

`JAVA_HOME`이 없으면 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

### 사용 SDK와 권한

| SDK | 버전 | 권한 | 사용처 |
| --- | --- | --- | --- |
| NaviHelper | 3.0.5 | `NAVI_ROUTE_SEARCH` | `PleosNaviPort`: 접근 지점 좌표로 경로 요청, 이벤트 수신 |
| Vehicle | 2.3.2 | `CAR_DRIVE`, `CAR_DOORS`, `CAR_SEATS` | `PleosVehicleSignalPort`: 기어, 동승석 도어·착석·안전벨트(보조 신호) |
| FusedLocation | 1.1.3 | `FUSED_LOCATION` | `PleosLocationPort`: 주차 위치 저장 |
| SpeechToText | 3.3.1 | `STT_SERVICE` | `PleosSpeechPort`: 조건 발화(ON_DEVICE 모드) |
| TextToSpeech | 3.3.1 | `TTS_SERVICE` | `PleosVoicePort`: 정차 후 음성 안내(ON_DEVICE 모드) |
| android.car | 플랫폼 | — | `CarUxDrivingStatePort`: 주행 제한(CarUxRestrictions) |

권한 이름은 `pleos.car.permission.*` 입니다. 퍼미션은 Pleos App Market과 협의·승인된 범위에서만 쓸 수 있습니다.

## 휴대폰 안내 웹

Node.js 22.13 이상(내장 `node:sqlite` 사용).

```bash
cd web
npm install
npm test          # API·만료·이벤트 테스트
npm run dev       # http://localhost:8787
```

환경 변수: `PORT`(기본 8787), `PUBLIC_BASE_URL`(QR에 들어갈 공개 주소), `DB_PATH`(기본 `trips.sqlite`), `PHOTOS_DIR`(기본 `../data/venues/photos`).

차량 앱과의 계약은 `web/src/contract.ts`와 `car-app/platform/.../share/HttpSharePort.kt`가 같아야 합니다.

## 시설 데이터

[`data/README.md`](data/README.md)를 보세요. 확인되지 않은 정보는 `null`로 두고, 규칙 엔진은 이를 ‘확인 필요’로 처리합니다.

## 오픈소스 고지

| 구성 요소 | 라이선스 |
| --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX (Compose, Activity, Lifecycle, DataStore) | Apache-2.0 |
| ZXing core | Apache-2.0 |
| Hono, @hono/node-server | MIT |
| tsx | MIT |
| TypeScript | Apache-2.0 |

Pleos Connect SDK는 현대자동차그룹 Pleos Playground 이용 조건을 따릅니다.
