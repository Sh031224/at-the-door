package app.atthedoor.domain.trip

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
    data class ConditionsConfirmed(val conditions: TravelConditions) : TripEvent
    data class PlanSelected(val candidateId: String) : TripEvent
    /** NaviHelper onRoutingCompleted 수신(경로 탐색 완료 = 안내 시작). onRouteStarted나 requestRoute 호출 성공으로는 보내지 않는다. */
    data object NaviStarted : TripEvent
    /** NaviHelper onError 또는 응답 시간 초과 */
    data class NaviFailed(val reason: String) : TripEvent
    /** 차량 접근 중 운전자가 내비 안내를 취소하거나 안내가 끝남(onRouteCancelled / onRouteEnded, 도착 전) */
    data class NaviInterrupted(val reason: String) : TripEvent
    /**
     * 사용자가 ‘주차했어요’를 누름. [drivingRestricted]는 그 순간의 CarUxRestrictions 상태다.
     * 주행 제한 중이면 무시한다.
     */
    data class ParkingConfirmed(val drivingRestricted: Boolean, val note: String? = null) : TripEvent
    data object WalkStarted : TripEvent
    /** 사람이 직접 누른 ‘입구에 도착했어요’. 도어 열림·목적지 근접 신호로 만들지 않는다. */
    data object ArrivedAtEntrance : TripEvent
    data object ReturnStarted : TripEvent
    data object BackInCar : TripEvent
    data class ParkingNoteSaved(val note: String) : TripEvent
    /** 주차장 만차, 입구 폐쇄 등 사용자 제보 */
    data class CandidateUnavailable(val candidateId: String) : TripEvent
    data class ConditionsChanged(val conditions: TravelConditions) : TripEvent
    /** 재계획 결과: 같은 조건을 만족하는 대안 */
    data class AlternativeChosen(val candidateId: String) : TripEvent
    /** 재계획 결과: 확인된 대안 없음 */
    data object NoAlternative : TripEvent
    data object Cancel : TripEvent
}

/**
 * 여행 상태 전이. 순수 함수이며, 허용되지 않은 전이는 상태를 바꾸지 않는다.
 * 같은 eventId가 다시 들어오면 무시해 중복 SDK 이벤트로 도착·종료가 두 번 처리되지 않게 한다.
 */
object TripMachine {

    fun reduce(trip: Trip, event: TripEvent, eventId: String): Trip {
        if (eventId in trip.processedEventIds) return trip
        val next = transition(trip, event) ?: return trip
        return next.copy(processedEventIds = trip.processedEventIds + eventId)
    }

    private fun transition(t: Trip, e: TripEvent): Trip? = when (e) {
        is TripEvent.ConditionsConfirmed ->
            if (t.phase == PLANNING) t.copy(conditions = e.conditions, phase = CHOOSING) else null

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
                t.copy(phase = PARKED, parkingNote = e.note ?: t.parkingNote)
            } else null

        TripEvent.WalkStarted -> if (t.phase == PARKED) t.copy(phase = WALKING) else null

        TripEvent.ArrivedAtEntrance -> if (t.phase == WALKING) t.copy(phase = VISITING) else null

        TripEvent.ReturnStarted -> if (t.phase == VISITING) t.copy(phase = RETURNING) else null

        TripEvent.BackInCar -> if (t.phase == RETURNING) t.copy(phase = COMPLETED) else null

        is TripEvent.ParkingNoteSaved ->
            if (t.phase in setOf(PARKED, WALKING, VISITING, RETURNING)) t.copy(parkingNote = e.note) else null

        is TripEvent.CandidateUnavailable ->
            if (t.phase in setOf(APPROACHING, PARKED, WALKING, RETURNING, NAVI_REQUESTED)) {
                t.copy(
                    unavailableIds = t.unavailableIds + e.candidateId,
                    resumePhase = resumeAfterReplan(t.phase),
                    phase = REPLANNING,
                )
            } else null

        is TripEvent.ConditionsChanged ->
            // 동행 조건이 바뀌면 이전 계획을 그대로 잇지 않고 전체를 다시 평가한다.
            if (t.phase in setOf(CHOOSING, NAVI_REQUESTED, APPROACHING, PARKED, WALKING, RETURNING, NEEDS_CHECK)) {
                t.copy(
                    conditions = e.conditions,
                    resumePhase = if (t.phase == NEEDS_CHECK) CHOOSING else resumeAfterReplan(t.phase),
                    phase = REPLANNING,
                )
            } else null

        is TripEvent.AlternativeChosen ->
            if (t.phase == REPLANNING) {
                t.copy(selectedCandidateId = e.candidateId, phase = t.resumePhase ?: CHOOSING, resumePhase = null)
            } else null

        TripEvent.NoAlternative ->
            if (t.phase == REPLANNING) t.copy(phase = NEEDS_CHECK) else null

        TripEvent.Cancel ->
            if (t.phase != COMPLETED && t.phase != CANCELLED) t.copy(phase = CANCELLED) else null
    }

    /** 차량 이동 전 단계에서 바뀌면 새 목적지로 내비를 다시 요청해야 하므로 NAVI_REQUESTED로 돌아간다. */
    private fun resumeAfterReplan(phase: TripPhase): TripPhase = when (phase) {
        NAVI_REQUESTED, APPROACHING -> NAVI_REQUESTED
        PARKED, WALKING -> WALKING
        RETURNING -> RETURNING
        else -> CHOOSING
    }
}
