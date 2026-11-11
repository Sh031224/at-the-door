package app.atthedoor.domain.trip

import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.trip.TripPhase.APPROACHING
import app.atthedoor.domain.trip.TripPhase.CANCELLED
import app.atthedoor.domain.trip.TripPhase.CHOOSING
import app.atthedoor.domain.trip.TripPhase.COMPLETED
import app.atthedoor.domain.trip.TripPhase.NAVI_REQUESTED
import app.atthedoor.domain.trip.TripPhase.NEEDS_CHECK
import app.atthedoor.domain.trip.TripPhase.PARKED
import app.atthedoor.domain.trip.TripPhase.PLANNING
import app.atthedoor.domain.trip.TripPhase.REPLANNING
import app.atthedoor.domain.trip.TripPhase.RETURNING
import app.atthedoor.domain.trip.TripPhase.VISITING
import app.atthedoor.domain.trip.TripPhase.WALKING

sealed interface TripEvent {
    /** 목적지(시설) 선택. 계획 작성 중에만 바꿀 수 있다. */
    data class VenueSelected(val venueId: String) : TripEvent
    data class ConditionsConfirmed(val conditions: TravelConditions) : TripEvent
    /** 후보 비교에서 조건을 고치러 돌아감 */
    data object EditConditions : TripEvent
    data class PlanSelected(val candidateId: String) : TripEvent
    /** NaviHelper onRoutingCompleted 수신(경로 탐색 완료 = 안내 시작). onRouteStarted나 requestRoute 호출 성공으로는 보내지 않는다. */
    data object NaviStarted : TripEvent
    /** NaviHelper onError 또는 응답 시간 초과 */
    data class NaviFailed(val reason: String) : TripEvent
    /** 차량 접근 중 운전자가 내비 안내를 취소하거나 안내가 끝남(onRouteCancelled / onRouteEnded, 도착 전) */
    data class NaviInterrupted(val reason: String) : TripEvent
    /**
     * 사용자가 ‘주차했어요’를 누름. [drivingRestricted]는 그 순간의 CarUxRestrictions 상태다.
     * 주행 제한 중이면 무시한다. [location]은 차량 기기 안에만 저장한다.
     */
    data class ParkingConfirmed(val drivingRestricted: Boolean, val note: String? = null, val location: GeoPoint? = null) : TripEvent
    data object WalkStarted : TripEvent
    /** 사람이 직접 누른 ‘입구에 도착했어요’. 도어 열림·목적지 근접 신호로 만들지 않는다. */
    data object ArrivedAtEntrance : TripEvent
    /** 입구 도착을 잘못 눌렀을 때 되돌림. 방문 중에서 다시 보행 안내로 간다. */
    data object ArrivalUndone : TripEvent
    data object ReturnStarted : TripEvent
    data object BackInCar : TripEvent
    data class ParkingNoteSaved(val note: String) : TripEvent
    /** 주차장 만차 등 후보 자체를 쓸 수 없다는 사용자 제보 */
    data class CandidateUnavailable(val candidateId: String) : TripEvent
    /** 건물 입구 폐쇄 제보. 같은 입구로 가는 모든 후보가 영향을 받는다. */
    data class EntranceBlocked(val entranceId: String) : TripEvent
    data class ConditionsChanged(val conditions: TravelConditions) : TripEvent
    /**
     * 재계획 결과: 같은 조건을 만족하는 대안.
     * [sameLot]은 지금 차가 있는 구역에서 걸어서 갈 수 있는 대안인지다. 걷는 중인데 아니면 차를 옮겨야 한다.
     */
    data class AlternativeChosen(val candidateId: String, val sameLot: Boolean) : TripEvent
    /** 재계획 결과: 확인된 대안 없음 */
    data object NoAlternative : TripEvent
    /** 앱을 다시 열었을 때. 끊긴 내비 연결 같은 휘발 상태를 믿지 않고 안전한 단계로 되돌린다. */
    data object Resumed : TripEvent
    data class FeedbackGiven(val kinds: Set<FeedbackKind>) : TripEvent
    data object Cancel : TripEvent
}

/**
 * 여행 상태 전이. 순수 함수이며, 허용되지 않은 전이는 상태를 바꾸지 않는다.
 * 같은 eventId가 다시 들어오면 무시해 중복 SDK 이벤트로 도착·종료가 두 번 처리되지 않게 한다.
 */
object TripMachine {

    const val NOTICE_RESUMED_NAVI = "앱이 다시 시작돼 내비 연결을 확인하지 못했어요. 다시 안내를 눌러 주세요."
    const val NOTICE_NEEDS_CAR_MOVE = "지금 주차한 곳에서 걸어서 갈 수 있는 대안이 없어요. 차로 돌아가 다른 구역으로 옮겨야 해요."
    const val NOTICE_ALTERNATIVE = "같은 조건으로 다른 계획을 찾았어요."

    fun reduce(trip: Trip, event: TripEvent, eventId: String): Trip {
        if (eventId in trip.processedEventIds) return trip
        val next = transition(trip, event) ?: return trip
        return next.copy(processedEventIds = trip.processedEventIds + eventId)
    }

    private val replannable = setOf(CHOOSING, NAVI_REQUESTED, APPROACHING, PARKED, WALKING, RETURNING, NEEDS_CHECK)

    private fun transition(t: Trip, e: TripEvent): Trip? = when (e) {
        is TripEvent.VenueSelected ->
            if (t.phase == PLANNING && t.venueId != e.venueId) t.copy(venueId = e.venueId, selectedCandidateId = null) else null

        is TripEvent.ConditionsConfirmed ->
            if (t.phase == PLANNING) t.copy(conditions = e.conditions, phase = CHOOSING, notice = null) else null

        TripEvent.EditConditions ->
            if (t.phase == CHOOSING) t.copy(phase = PLANNING, notice = null) else null

        is TripEvent.PlanSelected ->
            if (t.phase == CHOOSING) t.copy(selectedCandidateId = e.candidateId, phase = NAVI_REQUESTED, notice = null) else null

        TripEvent.NaviStarted ->
            if (t.phase == NAVI_REQUESTED) t.copy(phase = APPROACHING, notice = null) else null

        is TripEvent.NaviFailed ->
            // 선택한 계획은 유지하고, 안내 중으로 표시하지 않는다.
            if (t.phase == NAVI_REQUESTED) t.copy(phase = CHOOSING, notice = e.reason) else null

        is TripEvent.NaviInterrupted ->
            // 선택은 유지하고 다시 안내를 요청할 수 있게 후보 선택으로 돌아간다.
            if (t.phase == APPROACHING) t.copy(phase = CHOOSING, notice = e.reason) else null

        is TripEvent.ParkingConfirmed ->
            if (t.phase == APPROACHING && !e.drivingRestricted) {
                t.copy(phase = PARKED, parkingNote = e.note ?: t.parkingNote, parkedAt = e.location ?: t.parkedAt, notice = null)
            } else null

        TripEvent.WalkStarted -> if (t.phase == PARKED) t.copy(phase = WALKING, notice = null) else null

        TripEvent.ArrivedAtEntrance -> if (t.phase == WALKING) t.copy(phase = VISITING, notice = null) else null

        TripEvent.ArrivalUndone -> if (t.phase == VISITING) t.copy(phase = WALKING, notice = null) else null

        TripEvent.ReturnStarted -> if (t.phase == VISITING) t.copy(phase = RETURNING, notice = null) else null

        TripEvent.BackInCar -> if (t.phase == RETURNING) t.copy(phase = COMPLETED, notice = null) else null

        is TripEvent.ParkingNoteSaved ->
            if (t.phase in setOf(PARKED, WALKING, VISITING, RETURNING)) t.copy(parkingNote = e.note.take(40)) else null

        is TripEvent.CandidateUnavailable ->
            if (t.phase in setOf(NAVI_REQUESTED, APPROACHING, PARKED, WALKING, RETURNING)) {
                replanning(t, t.copy(unavailableIds = t.unavailableIds + e.candidateId))
            } else null

        is TripEvent.EntranceBlocked ->
            if (t.phase in setOf(NAVI_REQUESTED, APPROACHING, PARKED, WALKING, RETURNING)) {
                replanning(t, t.copy(blockedEntranceIds = t.blockedEntranceIds + e.entranceId))
            } else null

        is TripEvent.ConditionsChanged ->
            // 동행 조건이 바뀌면 이전 계획을 그대로 잇지 않고 전체를 다시 평가한다.
            if (t.phase in replannable && e.conditions != t.conditions) replanning(t, t.copy(conditions = e.conditions)) else null

        is TripEvent.AlternativeChosen ->
            if (t.phase == REPLANNING) {
                val resume = t.resumePhase ?: CHOOSING
                if (resume.onFoot && !e.sameLot) {
                    t.copy(selectedCandidateId = e.candidateId, phase = CHOOSING, resumePhase = null, notice = NOTICE_NEEDS_CAR_MOVE)
                } else {
                    t.copy(
                        selectedCandidateId = e.candidateId,
                        phase = resume,
                        resumePhase = null,
                        notice = NOTICE_ALTERNATIVE.takeIf { e.candidateId != t.selectedCandidateId },
                    )
                }
            } else null

        TripEvent.NoAlternative ->
            if (t.phase == REPLANNING) t.copy(phase = NEEDS_CHECK, resumePhase = null) else null

        TripEvent.Resumed -> when (t.phase) {
            // 내비 요청 결과를 받던 리스너는 앱과 함께 사라졌다. 선택은 유지하고 다시 요청하게 한다.
            NAVI_REQUESTED -> t.copy(phase = CHOOSING, notice = NOTICE_RESUMED_NAVI)
            // 재계획 도중 꺼졌으면 다시 평가하도록 후보 선택으로 돌아간다.
            REPLANNING -> t.copy(phase = CHOOSING, resumePhase = null, notice = null)
            // 지난 실행의 알림(예: 내비 응답 없음)은 지금 상황이 아니므로 지운다.
            else -> if (t.notice != null) t.copy(notice = null) else null
        }

        is TripEvent.FeedbackGiven ->
            if (t.phase == COMPLETED && t.feedback == null) t.copy(feedback = e.kinds) else null

        TripEvent.Cancel ->
            if (!t.phase.finished) t.copy(phase = CANCELLED, notice = null) else null
    }

    private fun replanning(before: Trip, changed: Trip): Trip = changed.copy(
        resumePhase = resumeAfterReplan(before.phase),
        phase = REPLANNING,
        notice = null,
    )

    /** 차량 이동 전 단계에서 바뀌면 새 목적지로 내비를 다시 요청해야 하므로 NAVI_REQUESTED로 돌아간다. */
    private fun resumeAfterReplan(phase: TripPhase): TripPhase = when (phase) {
        NAVI_REQUESTED, APPROACHING -> NAVI_REQUESTED
        PARKED, WALKING -> WALKING
        RETURNING -> RETURNING
        else -> CHOOSING
    }
}
