# 문앞

## 앱 이름 및 한 줄 소개

**문앞**: 보행이 불편한 가족과 차로 외출할 때, 동행 조건에 맞는 승하차·주차 지점과 입구까지의 동선을 출발 전 차 안에서 정하고, 차량 내비게이션에서 휴대폰 보행 안내까지 이어 주는 도착 계획 앱입니다. (Pleos Connect / Android Automotive OS)

- 기획·기능 명세: [`문앞_앱개발_시나리오_기능명세.md`](문앞_앱개발_시나리오_기능명세.md)
- 구동 시연: [`docs/문앞_시연영상.mp4`](docs/문앞_시연영상.mp4) (Pleos Connect Emulator v2.1.0, 실제 Pleos SDK, 시연 데이터, 약 2분 43초, 2026-11-14 변경 UI)
- 가족 시나리오 영상: [러프 v2 MP4](docs/video-draft-2026-11-14/문앞_스토리영상_러프_v2.mp4) · [장면별 플레이어](docs/video-draft-2026-11-14/v2.html) (3분, 가족 일러스트와 Pleos 전체 구도 합성·UI 시안·실제 웹 캡처를 연결한 영상)
- 변경한 UI: [`docs/ui-applied-2026-11-14/`](docs/ui-applied-2026-11-14/) (차량 Compose 렌더링·실제 휴대폰 웹 캡처). [Pleos 전체 구도](docs/ui-applied-2026-11-14/pleos-full-preview.jpg)는 기존 OS 프레임과 변경한 UI를 합성한 예시입니다. 변경한 APK는 2026-11-14 Pleos Connect Emulator에서 실제 SDK로 처음부터 복귀까지 실행해 구동 시연 영상으로 녹화했습니다.
- 기존 에뮬레이터 화면: [`docs/screens/`](docs/screens/)

## 실행 방법

### 1) APK로 바로 실행

1. 아래 ‘테스트 환경 및 필요한 설정’대로 Pleos Connect Emulator를 띄웁니다.
2. APK를 설치하고 실행합니다.
   ```bash
   adb install -r apk/<팀명>_문앞_release.apk
   adb shell am start -n app.atthedoor/app.atthedoor.app.MainActivity
   ```
3. (선택) 휴대폰 안내 페이지까지 보려면 같은 PC에서 웹 서버를 띄웁니다. 에뮬레이터는 `http://10.0.2.2:8787`로 이 서버에 접속합니다.
   ```bash
   cd web && npm install && npm run dev
   ```
   서버가 없어도 앱은 동작하며, 휴대폰 연결 대신 차량 화면 안내로 대체합니다.

### 2) 소스에서 빌드

```bash
cd car-app
./gradlew :app:installDebug                                # 실제 Pleos SDK 연결
./gradlew :app:installDebug -Patthedoor.useFakes=true      # SDK 대신 가짜 구현(화면에 ‘모의 응답’ 표시)
./gradlew :app:exportReleaseApk -Patthedoor.teamName=팀명  # apk/팀명_문앞_release.apk 생성
```

`JAVA_HOME`이 없으면 Android Studio 내장 JDK를 씁니다: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

### 3) 시연 흐름

1. Virtual Console에서 기어를 **P**로 둡니다.
2. 목적지·이동 조건·추천 계획을 한 화면에서 확인합니다. 처음에는 ‘이동 조건 확인’에서 동행자와 이동 조건을 직접 고르고 ‘이 조건 저장’을 누릅니다. ‘말로 정하기’도 사용할 수 있습니다.
3. 같은 화면에서 추천 계획의 ‘후문 주차장 입구로 안내’를 누릅니다. ‘다른 계획’ 시트에서 비교할 수 있으며, 정문 승하차 구역은 ‘추천하지 않음’, 지하 주차장은 ‘확인 필요’입니다.
4. 차량 Navigation 앱이 ‘새봄문화회관 후문 주차장 입구’로 안내를 시작하면(약 10초) 문앞이 ‘내비게이션 안내 중’으로 바뀝니다.
5. 기어를 **D**로 바꾸고 속도를 올리면 주행 중 최소 화면으로 바뀝니다. 여기서 ‘만차예요 · 다른 곳 찾기’를 누르면 같은 조건의 인근 공영주차장으로 내비가 다시 안내되고 음성으로 알립니다.
6. 다시 **P**로 두고 ‘주차했어요’를 누르면 음성 안내와 휴대폰 안내 QR이 나타납니다(웹 서버 실행 시).
7. 휴대폰 페이지(또는 차량 화면)에서 ‘입구에 도착했어요’ → ‘차로 돌아가기’를 누르면 차량 화면이 따라 바뀌고, ‘차에 탔어요’로 마치면 공유 링크가 삭제됩니다.
8. 걷는 중 ‘입구가 막혀 있어요 · 다른 입구 찾기’를 누르면 차를 옮기지 않고 같은 주차장에서 정문 경사로로 가는 길을 안내합니다.
9. ‘새 여행 계획’에서 ‘다른 보호자도 함께해요’를 켜면 정문 승하차 구역이 후보가 됩니다. ‘한빛종합병원 본관’에서는 장애인 전용 구역과 계단 있는 외래동 동선이 조건에 따라 어떻게 판정되는지 볼 수 있습니다.

네트워크가 없거나 Navigation 앱이 응답하지 않으면 왼쪽 위 ‘문앞’ 로고를 길게 눌러 ‘모의 응답 모드’로 바꿉니다(실제 SDK 시연 중 잘못 누르지 않도록 숨겨 두었습니다). 화면 위에 모의 응답 표시가 나오고, 왼쪽 ‘시연 제어’에서 주행·기어·벨트·도어·내비 응답을 바꿀 수 있습니다.

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, AVD 시스템 이미지 API 34 “Pleos Connect v2.1.0 APIs”, RAM 4GB |
| 앱 | minSdk 28, targetSdk 34, 패키지 `app.atthedoor` |
| 빌드 | JDK 17 이상(Android Studio 내장 JBR 사용 가능), Gradle 9.5(wrapper 포함), Android SDK Platform 37 |
| 웹(선택) | Node.js 22.13 이상 |

**에뮬레이터 설정**

1. Android Studio SDK Manager → SDK Update Sites에 다음 주소를 추가하고 시스템 이미지를 받습니다.
   - Apple Silicon: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. AVD의 Boot option을 **Cold boot**로 설정합니다. Quick boot에서는 차량 신호(VHAL)를 쓸 수 없습니다. 명령줄로 띄울 때는 다음을 권장합니다(2026-11-11 확인).
   ```bash
   emulator -avd <AVD 이름> -no-snapshot -no-audio -dns-server 8.8.8.8
   ```
   - `-no-audio`: 오디오를 켠 채 부팅하면 멈추는 경우가 있었습니다. TTS 동작 확인에는 지장이 없습니다.
   - `-dns-server`: 에뮬레이터 DNS가 풀리지 않으면 Navigation 앱이 경로를 만들지 못해 내비 연결이 30초 뒤 실패로 표시됩니다.
3. 음성 입력을 쓰려면 에뮬레이터 설정에서 마이크 입력을 켭니다.
4. 차량 상태는 Virtual Console로 바꿉니다: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`. 브라우저 창이 열려 있어야 기어·속도 변경이 적용됩니다.

**사용 SDK와 권한**

| SDK | 버전 | 권한(`pleos.car.permission.*`) | 용도 |
| --- | --- | --- | --- |
| NaviHelper | 3.0.5 | `NAVI_ROUTE_SEARCH` | 계획한 접근 지점으로 경로 요청, 탐색 완료·실패·취소 이벤트 |
| Vehicle | 2.3.2 | `CAR_DRIVE`, `CAR_DOORS`, `CAR_SEATS` | 기어, 동승석 도어·착석·안전벨트(하차 준비 보조 신호) |
| FusedLocation | 1.1.3 | `FUSED_LOCATION` | 주차 위치 저장 |
| SpeechToText | 3.3.1 | `STT_SERVICE` | 조건 발화 인식(ON_DEVICE, 앱 등록 불필요) |
| TextToSpeech | 3.3.1 | `TTS_SERVICE` | 주차 후 음성 안내(ON_DEVICE) |
| android.car | 플랫폼 | — | 주행 제한(CarUxRestrictions) |

SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 인증 없이 받습니다.

**알려진 제약**

- 시설 데이터는 가상 공연장·종합병원 2곳(`data/venues/*.json`)의 시연용 데이터이고, 입구 이미지는 시연용 일러스트입니다(`data/tools/gen_illustrations.py`). 실제 시설 검증 결과가 아니며 화면에도 그렇게 표시합니다.
- Navigation 앱은 경로 탐색 후 안내를 시작할 때 `onRouteStarted`를 보내고 `onRoutingCompleted`는 보내지 않았습니다. 문앞은 우리가 보낸 목적지 이름과 거리가 담긴 `onRouteStarted`를 안내 시작으로 봅니다. 30초 안에 시작되지 않으면 ‘내비게이션 응답이 없어요’를 표시하고 계획을 유지합니다.

## APK 파일 위치

- 저장소의 [`apk/`](apk/) 폴더: `apk/<팀명>_문앞_release.apk`
- 디버그 키로 서명한 release 빌드이며, 실제 Pleos SDK를 연결합니다.
- 휴대폰 안내 서버 주소는 빌드 때 정합니다(`-Patthedoor.shareBaseUrl`, 기본 `http://10.0.2.2:8787`). 서버가 없으면 휴대폰 연결 대신 차량 화면 안내로 계속됩니다.
- 용량이 GitHub 제한을 넘으면 GitHub Releases에 올리고 링크를 여기에 적습니다.

---

## 개발자 참고

### 저장소 구조

```
.
├── car-app/                 차량 앱 (Android Automotive / Pleos Connect, Kotlin)
│   ├── domain/              순수 Kotlin: 이동 조건, 규칙 엔진(PlanEvaluator), 여행 상태 머신(TripMachine), 포트 인터페이스
│   ├── data/                시설 데이터 로더(VenueJson), 여행 상태 저장(TripStore, DataStore)
│   ├── platform/            Pleos SDK·Car API 어댑터(Pleos*), 가짜 구현(Fake*), 공유 링크 API 클라이언트
│   └── app/                 Compose 화면, ViewModel, 의존성 조립(AppContainer)
├── web/                     휴대폰 안내 페이지와 공유 링크 API (TypeScript, Hono, node:sqlite)
├── data/venues/             시연 시설 데이터 JSON과 입구 사진 (차량 앱 assets와 웹이 함께 사용)
├── apk/                     제출용 release APK
└── 제출/                    공모전 제출 양식
```

의존 방향은 `app → platform, data → domain` 입니다. `domain`은 Android와 SDK에 의존하지 않으므로 수용 테스트 대부분을 JVM 단위 테스트로 검증합니다. 모든 SDK 호출은 `domain/port/Ports.kt`의 인터페이스 뒤에 있습니다.

### 테스트

```bash
cd car-app && ./gradlew :domain:test :data:testDebugUnitTest   # 규칙 엔진·상태 머신·데이터
cd car-app && ./gradlew :app:verifyPaparazziDebug             # Compose 화면 비교
cd web && npm test && npm run typecheck                      # 공유 링크 API
```

### release 서명

`~/.gradle/gradle.properties`에 `atthedoor.storeFile`, `atthedoor.storePassword`, `atthedoor.keyAlias`, `atthedoor.keyPassword`를 두면 그 키로 서명합니다. 없으면 디버그 키로 서명합니다.

### 휴대폰 안내 웹

환경 변수: `PORT`(기본 8787), `PUBLIC_BASE_URL`(QR에 들어갈 공개 주소), `DB_PATH`(기본 `trips.sqlite`), `PHOTOS_DIR`(기본 `../data/venues/photos`). 차량 앱과의 계약은 `web/src/contract.ts`와 `car-app/platform/.../share/HttpSharePort.kt`가 같아야 합니다.

### 시설 데이터

[`data/README.md`](data/README.md)를 보세요. 확인되지 않은 정보는 `null`로 두고, 규칙 엔진은 이를 ‘확인 필요’로 처리합니다.

### 오픈소스 고지

| 구성 요소 | 라이선스 |
| --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| AndroidX (Compose, Activity, Lifecycle, DataStore) | Apache-2.0 |
| ZXing core | Apache-2.0 |
| [Paparazzi](https://github.com/cashapp/paparazzi) (Compose 화면 캡처) | Apache-2.0 |
| Asta Sans (시안·기획서 PDF) | SIL OFL-1.1, [라이선스](docs/ux-concept-2026-11-14/assets/fonts/OFL.txt) |
| Hono, @hono/node-server | MIT |
| tsx | MIT |
| TypeScript | Apache-2.0 |

Pleos Connect SDK는 현대자동차그룹 Pleos Playground 이용 조건을 따릅니다.
