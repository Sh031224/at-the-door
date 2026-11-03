# AGENTS.md

이 저장소에서 작업하는 사람과 에이전트가 공유하는 목표, 규칙, 참고 자료입니다. 응답과 문서는 한국어로 씁니다.

## 공동 목표

**현대자동차그룹 차량용 앱 개발 공모전에 ‘문앞’을 제출하고, 2차 PT(2026.12.03~04)에서 실제로 동작하는 앱을 시연한다.**

1. **제출 가능한 기획서**: `제출/문앞_접수양식.docx`의 02장(1~7번)이 심사 기준 네 가지를 모두 충족한다. 참가자 정보와 서명은 참가자가 직접 채운다.
2. **바로 개발할 수 있는 기획서**: `문앞_앱개발_시나리오_기능명세.md`만 읽고 다음 작업을 시작할 수 있다.
3. **동작하는 앱**: Pleos Connect Emulator에서 조건 입력 → 후보 비교 → 내비 연결 → 주차 확인 → 휴대폰 안내 → 입구 도착 → 복귀가 끝까지 이어진다.

제품 한 줄: 보행이 불편한 가족과 차로 외출할 때, 동행 조건에 맞는 승하차·주차 지점과 입구까지의 동선을 출발 전 차 안에서 정하고, 차량 내비게이션에서 휴대폰 보행 안내까지 이어 주는 도착 계획 앱.

## 일정 (KST)

| 날짜 | 일정 |
| --- | --- |
| ~2026.11.15(일) 23:59 | 접수 마감. 이메일 `appdevchallenge@hyundai-partners.com` |
| 2026.11.23(월) | 1차 서류심사 결과 |
| 2026.12.03(목)~12.04(금) | 2차 최종 PT. 현대자동차 강남대로사옥 1층 UX스튜디오 서울. 실제 동작하는 앱 필수(에뮬레이터 시연 허용) |
| 2026.12.18(금) | 최종 결과 |
| 2027.02 | 현대자동차그룹 행사 전시·시상. 수상팀은 최종 구동용 APK 제출 |

## 심사 기준

1차 서류심사 항목별 배점은 공개되지 않는다. 네 항목을 모두 필수로 다룬다.

| 항목 | 질문 | 양식 항목 |
| --- | --- | --- |
| 차량특화성 | 모바일보다 차량에서만 주는 가치가 있는가, 주행·주차 맥락과 차량 기능에 결합되었는가 | 3 |
| 창의성 | Pain point가 명확하고 해결 방식이 새로운가 | 2, 4 |
| 기술 활용성 | Pleos SDK·API를 핵심 기능에 썼는가 | 5 |
| 사업성 | 타깃, 예상 MAU, Retention, 확대 계획 | 6 |
| 가산점 | 시연 영상, GitHub 저장소, APK를 1차에 함께 제출 | 7 |

2차 PT는 PT 시점의 결과물로 순위를 정한다. PT 이후 추가 개발은 순위에 반영되지 않는다.

## 공모전 링크

- 공모전 홈페이지: https://appdevchallenge.auto-hmg.io
  - 참가 가이드: https://appdevchallenge.auto-hmg.io/guide
  - 일정: https://appdevchallenge.auto-hmg.io/guide/schedule
  - 지원·제출 방법: https://appdevchallenge.auto-hmg.io/guide/submission
  - 심사·시상: https://appdevchallenge.auto-hmg.io/guide/judging-awards
  - 개발 환경: https://appdevchallenge.auto-hmg.io/guide/development
  - 참가 규정: https://appdevchallenge.auto-hmg.io/guide/rules
  - FAQ: https://appdevchallenge.auto-hmg.io/faq
  - 공지: https://appdevchallenge.auto-hmg.io/notice
  - 이용약관(제7조 출품작 권리): https://appdevchallenge.auto-hmg.io/terms
- 보도자료: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-vehicle-app-development-contest
- Pleos Playground: https://pleos.ai/playground

## Pleos 개발 문서

- SDK 개요·권한 정책: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- NaviHelper: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- Vehicle: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- SpeechToText: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
- TextToSpeech: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- FusedLocation: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
- 주행 안전 설계: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 지역별 주행 규제 정책: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- 주행 규제 개발 가이드: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 앱 카테고리: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- 앱 심사 가이드: https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide
- Gleo Interface: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
- 에뮬레이터 설정: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

## 확인된 기술 사실

문서와 실제 SDK가 다를 때는 **Maven 저장소의 실제 AAR**을 기준으로 한다. 아래는 2026-11-03에 AAR을 `javap`로 확인한 내용이다.

- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`, 인증 없음
- 버전: NaviHelper 3.0.5, Vehicle 2.3.2, SpeechToText 3.3.1, TextToSpeech 3.3.1, FusedLocation 1.1.3
- 권한(`pleos.car.permission.*`): `NAVI_ROUTE_SEARCH`, `CAR_DRIVE`(Gear), `CAR_DOORS`, `CAR_SEATS`, `FUSED_LOCATION`, `STT_SERVICE`, `TTS_SERVICE`. 퍼미션은 Pleos App Market과 협의·승인된 범위에서만 쓴다. 제어 권한(`CONTROL_*`)은 쓰지 않는다.
- NaviHelper: `onRouteStarted`는 경로 **탐색 시작**, `onRoutingCompleted`가 **안내 시작**이다. `requestRoute` 호출 성공은 아무것도 보장하지 않는다. 문서에 없는 `onRouteEnded()`가 있다. `RouteInfo`의 `poiId`, `address`, `poiSubId`, `poiProvider`는 non-null 문자열이다.
- Vehicle: `Gear` API가 있으나 deprecated이며 Google `GEAR_SELECTION` 속성을 권장한다. 주차 브레이크 조회 API는 없다(Brake는 페달 상태만).
- STT·TTS: ON_DEVICE 모드는 앱 등록이 필요 없다. HYBRID·SERVER는 Pleos Playground My Project 자격 증명으로 `registerApp`이 필요하다. TTS `speak(text, isFinal)`.
- 주행 제한: `CarUxRestrictionsManager`만 기준으로 삼는다. QR은 `UX_RESTRICTIONS_NO_SETUP`도 함께 확인해 주차 상태에서만 보여 준다.
- 에뮬레이터: Pleos Connect Emulator v2.1.0, API 34 이미지, **Cold boot 필수**(quick boot에서는 VHAL 사용 불가).

아직 확인하지 못한 것: 에뮬레이터 Navigation 앱이 `requestRoute` 이벤트를 보내는지, 3rd party 앱의 DO 선언에 별도 승인이 필요한지, 심사용 GitHub 계정, 2차 PT 심사 기준.

## 제품 원칙

기획서와 코드가 함께 지켜야 하는 규칙이다.

- **모르는 정보를 충족으로 보지 않는다.** 시설 정보가 없거나 미확인이면 ‘확인 필요’로 둔다. 좌표, 거리, 출입 가능 여부를 지어내지 않는다. LLM이 장소 정보를 생성하지 않는다.
- **사람의 도착은 사람이 확인한다.** 도어 열림, 목적지 근접, 기어 P만으로 주차나 도착을 확정하지 않는다.
- **여행 상태와 주행 제한 상태를 분리한다.** 여행이 어느 단계든 주행 제한 중에는 최소 화면(현재 계획, 다음 지점, 내비 상태)만 보여 준다.
- **동행 조건이 바뀌면 계획 전체를 다시 평가한다.**
- **개인정보를 최소로 다룬다.** 서버에는 이름, 건강 정보, 위치를 저장하지 않는다. 공유 링크는 128비트 난수 토큰이고 24시간 후 만료된다. 보조기구·보행 제약은 기기 밖으로 보내지 않는다.
- **질병이나 장애를 추정하지 않는다.** 사용자가 지정한 조건만 쓴다.
- **시연 데이터와 운영 데이터를 섞지 않는다.** 시연 데이터는 `demo: true`로 표시한다.

## 저장소 구조와 명령

```
car-app/   domain(순수 Kotlin) · data · platform(SDK 어댑터·Fake) · app(Compose)
web/       휴대폰 안내 페이지 + 공유 링크 API (Hono, node:sqlite)
data/venues/  시설 데이터 JSON과 입구 사진 (차량 앱과 웹이 함께 사용)
apk/       제출용 release APK
제출/      공모전 제출 양식
```

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd car-app && ./gradlew :domain:test :data:testDebugUnitTest      # 단위 테스트
cd car-app && ./gradlew :app:assembleDebug [-Patthedoor.useFakes=true]
cd car-app && ./gradlew :app:exportReleaseApk -Patthedoor.teamName=팀명
cd web && npm test && npm run typecheck
```

- 의존 방향: `app → platform, data → domain`. `domain`에 Android나 SDK 의존성을 넣지 않는다.
- 모든 SDK 호출은 `car-app/domain/.../port/Ports.kt`의 인터페이스 뒤에 두고, 실제 구현(`Pleos*`)과 가짜 구현(`Fake*`)을 함께 만든다.
- 후보 판정은 `PlanEvaluator`, 상태 전이는 `TripMachine`만 거친다. 화면에서 추천 결과를 직접 바꾸지 않는다.
- 차량 앱 ↔ 웹 계약은 `web/src/contract.ts`와 `car-app/platform/.../share/HttpSharePort.kt`를 함께 바꾼다.
- 수용 테스트를 바꾸거나 추가하면 기획서의 ‘수용 테스트’ 목록도 맞춘다.

## 문서 규칙

- **기획서가 원본이다.** `문앞_앱개발_시나리오_기능명세.md`를 먼저 고치고, 그 내용을 제출 양식에 반영한다. 두 문서의 수치, 일정, SDK 설명이 서로 달라서는 안 된다.
- 기획서를 고치면 같은 이름의 PDF도 다시 만든다.
- 제출 양식(DOCX)은 원본 양식의 구조와 내용 컨트롤을 유지한 채 02장 박스만 채운다. 01장 참가자 정보, 04장 동의·서명은 비워 둔다.
- 사실과 가정을 구분해 쓴다. 공모전 유의사항에 따라 제출 서류의 내용이 사실과 다르면 선정이 취소될 수 있다. 통계와 수치에는 출처를 붙이고, 가정이면 가정이라고 쓴다.

## 제출·저장소 규칙

- 양식 02장과 공유 링크·첨부 자료에 개발자 실명, 연락처, 이메일, 소속, 계정정보를 넣지 않는다. README와 코드 주석에도 실명을 쓰지 않는다.
- 이 저장소의 커밋 이메일은 `sh031224.dev@gmail.com`이다(저장소 로컬 git 설정). 회사 이메일로 커밋하지 않는다.
- GitHub 제출 시 README에 필수 4항목을 둔다: 앱 이름 및 한 줄 소개, 실행 방법, 테스트 환경 및 필요한 설정, APK 파일 위치 또는 Release 링크.
- APK는 `apk/팀명_앱이름_release.apk`, 실행 가능한 release 빌드여야 한다. 용량이 넘으면 GitHub Releases 주소를 쓴다.
- 비공개 저장소로 제출하면 공지된 심사용 GitHub 계정을 Collaborator로 초대하고, 마감 전에 접근을 확인한다.
- 수정 제출은 마감 전, 처음 보낸 주소로, 제목에 `[수정제출]`을 붙여 전체 자료를 다시 보낸다.
- 출품작은 독립적으로 기획·개발한 것이어야 하고, 사용한 오픈소스는 라이선스를 지키고 README에 출처를 밝힌다.
- 커밋이나 푸시는 요청이 있을 때만 한다. 기록을 다시 쓰는 작업(강제 푸시, 리베이스)은 사람이 확인한 뒤 진행한다.
