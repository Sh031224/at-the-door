package app.atthedoor.domain.port

import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * 플랫폼 포트. 모든 SDK 호출은 이 인터페이스 뒤에 둔다.
 * 실제 구현은 :platform 의 Pleos*, 시연·테스트용은 Fake* 이다.
 */

/**
 * NaviHelper 이벤트를 문서 정의대로 옮긴 결과.
 * - onRouteStarted: "Navigation 앱이 경로 탐색을 시작하면 호출" → [Searching] (아직 안내 중 아님)
 * - onRoutingCompleted: "경로 탐색이 완료되면 호출" → [Guiding] (이때부터 안내 중으로 표시)
 */
sealed interface NaviResult {
    data object Searching : NaviResult
    /** 안내 시작. 내비가 알려 준 예상 시간(초)·거리(m)가 있으면 함께 담는다(RouteStartInfo). */
    data class Guiding(val durationSec: Int? = null, val distanceM: Int? = null) : NaviResult
    /** onDestinationArrived. 차량 접근 지점 도착 참고 신호일 뿐, 사람의 도착이 아니다. */
    data object Arrived : NaviResult
    /** onRouteCancelled. 운전자가 내비에서 안내를 취소함 */
    data object Cancelled : NaviResult
    /** onRouteEnded */
    data object Ended : NaviResult
    /** onDestinationChanged. 우리 요청으로도 발생하므로 단독으로 이탈 판단에 쓰지 않는다. */
    data object DestinationChanged : NaviResult
    data class Failed(val code: String) : NaviResult
    /** 정해진 시간 안에 Guiding 또는 Failed가 오지 않음 */
    data object TimedOut : NaviResult
}

interface NaviPort {
    /** 경로를 요청하고 결과 이벤트를 흘려보낸다. Guiding 전에 Failed 또는 TimedOut이 올 수 있다. */
    fun requestRoute(target: AccessPoint): Flow<NaviResult>
    fun cancel()
}

interface DrivingStatePort {
    /** CarUxRestrictions.isRequiresDistractionOptimization. 판단할 수 없으면 true(보수적)로 둔다. */
    val restricted: StateFlow<Boolean>
    /** ActiveRestrictions에 UX_RESTRICTIONS_NO_SETUP이 있는지. QR·조건 수정 화면은 이 값도 함께 본다. */
    val setupBlocked: StateFlow<Boolean>
}

enum class GearState { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 차량 보조 신호. 기능 판단의 근거가 아니라 제안 시점을 고르는 맥락으로만 쓴다. */
interface VehicleSignalPort {
    /** Vehicle Gear(CAR_DRIVE). 지원하지 않거나 실패하면 null */
    val gear: StateFlow<GearState?>
    /** Vehicle Door 위치(CAR_DOORS), 동승석. 열림이면 true. 지원하지 않거나 실패하면 null */
    val frontPassengerDoorOpen: StateFlow<Boolean?>
    /** Vehicle Seat 점유(CAR_SEATS). 지원하지 않으면 null */
    val frontPassengerOccupied: StateFlow<Boolean?>
    /** Vehicle Seat 안전벨트 체결(CAR_SEATS). 해제는 하차 준비의 가장 이른 신호다. 지원하지 않으면 null */
    val frontPassengerBuckled: StateFlow<Boolean?>
}

interface LocationPort {
    suspend fun current(): GeoPoint?
}

data class SpeechText(val text: String, val completed: Boolean)

interface SpeechPort {
    /** SpeechToText onUpdated(stt, completed). 오류 시 Flow가 예외로 끝난다. */
    fun listen(): Flow<SpeechText>
    fun stop()
}

interface VoicePort {
    fun speak(text: String)
}

/**
 * 휴대폰 안내 페이지로 넘길 정보. 이름·건강 정보·실시간 위치·이동 조건(보행 제약, 보조기구)을 넣지 않는다.
 * 계약은 web/src/contract.ts 의 CreateTripRequest 와 같다.
 */
data class TripSnapshot(
    val venueName: String,
    val planName: String,
    val entranceName: String,
    val entrancePhotos: List<String>,
    /** 입구 이미지가 시연용 일러스트이면 true. 휴대폰에서도 ‘실제 사진 아님’을 표시한다. */
    val photosAreIllustrations: Boolean,
    val steps: List<String>,
    /** 차로 돌아가는 순서. 비어 있으면 휴대폰은 올 때 순서를 거꾸로 보여 준다. */
    val returnSteps: List<String>,
    val parkingNote: String?,
    val evidenceLabel: String,
    /** 시연 데이터 여부. 운영 데이터와 섞지 않는다. */
    val demo: Boolean,
    /** 대안이 없을 때 보여 줄 시설 연락처 */
    val contact: String?,
    /** 예: "주차 후 입구까지 약 80m · 계단 없음" */
    val walkSummary: String,
    /** 같은 조건의 대안을 찾지 못함. 휴대폰은 시설 연락 화면으로 바뀐다. */
    val noAlternative: Boolean,
    /** 재계획으로 길이 바뀐 이유. 휴대폰 위에 한 줄로 보인다. */
    val changeNote: String?,
    /** 지금 계획의 입구가 막혔다고 알려진 상태. 들어간 입구가 없으므로 입구 이름·사진·입구 기준 복귀 순서를 보여 주지 않는다. */
    val entranceBlocked: Boolean = false,
)

data class ShareLink(val token: String, val url: String, val expiresAtEpochMs: Long)

sealed interface PhoneEvent {
    data object Arrived : PhoneEvent
    data object EntranceBlocked : PhoneEvent
    data object Returning : PhoneEvent
    /** 휴대폰에서 ‘아직 도착하지 않았어요’ */
    data object ArrivalUndone : PhoneEvent
    data class ParkingNote(val note: String) : PhoneEvent
}

data class ReceivedPhoneEvent(val id: String, val event: PhoneEvent)

interface SharePort {
    /** 실패하면 null. 차량 화면 안내로 대체한다. */
    suspend fun createLink(snapshot: TripSnapshot): ShareLink?
    /** 재계획으로 안내 내용이 바뀌면 같은 링크의 내용을 바꾼다. 휴대폰 페이지가 새 내용을 다시 읽는다. */
    suspend fun update(token: String, snapshot: TripSnapshot): Boolean
    suspend fun pollEvents(token: String): List<ReceivedPhoneEvent>
    suspend fun delete(token: String)
}
