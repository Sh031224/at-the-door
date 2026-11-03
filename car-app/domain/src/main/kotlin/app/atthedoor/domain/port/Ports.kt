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
    data object Guiding : NaviResult
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

sealed interface DoorEvent {
    data class Opened(val seat: SeatPosition) : DoorEvent
    data class Closed(val seat: SeatPosition) : DoorEvent
}

enum class SeatPosition { DRIVER, FRONT_PASSENGER, REAR_LEFT, REAR_RIGHT }

/** 차량 보조 신호. 기능 판단의 근거가 아니라 제안 시점을 고르는 맥락으로만 쓴다. */
interface VehicleSignalPort {
    /** Vehicle Gear(CAR_DRIVE). 지원하지 않거나 실패하면 null */
    val gear: StateFlow<GearState?>
    /** Vehicle Door(CAR_DOORS) */
    val doorEvents: Flow<DoorEvent>
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

/** 휴대폰 안내 페이지로 넘길 정보. 이름·건강 정보·실시간 위치를 넣지 않는다. */
data class TripSnapshot(
    val venueName: String,
    val planName: String,
    val entranceName: String,
    val entrancePhotos: List<String>,
    val steps: List<String>,
    val conditionLabels: List<String>,
    val parkingNote: String?,
    val evidenceLabel: String,
)

data class ShareLink(val token: String, val url: String, val expiresAtEpochMs: Long)

sealed interface PhoneEvent {
    data object Arrived : PhoneEvent
    data object EntranceBlocked : PhoneEvent
    data object Returning : PhoneEvent
    data class ParkingNote(val note: String) : PhoneEvent
}

data class ReceivedPhoneEvent(val id: String, val event: PhoneEvent)

interface SharePort {
    /** 실패하면 null. 차량 화면 안내로 대체한다. */
    suspend fun createLink(snapshot: TripSnapshot): ShareLink?
    suspend fun pollEvents(token: String): List<ReceivedPhoneEvent>
    suspend fun delete(token: String)
}
