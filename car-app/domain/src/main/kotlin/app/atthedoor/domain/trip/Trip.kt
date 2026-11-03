package app.atthedoor.domain.trip

import app.atthedoor.domain.model.TravelConditions

/** 여행의 진행 상태. 차량 UI의 조작 허용 상태(주행 제한)와는 별개로 관리한다. */
enum class TripPhase {
    PLANNING,        // 계획 작성: 목적지·조건 입력 중
    CHOOSING,        // 후보 선택
    NAVI_REQUESTED,  // 내비 요청 중(아직 안내 시작 아님)
    APPROACHING,     // 차량 접근(내비 안내 중)
    PARKED,          // 보행 준비(주차 확인됨)
    WALKING,         // 보행 안내
    VISITING,        // 방문 중(입구 도착을 사람이 확인함)
    RETURNING,       // 복귀
    COMPLETED,
    REPLANNING,      // 재계획: 이용 불가 제보나 조건 변경 후 대안 평가 중
    NEEDS_CHECK,     // 확인된 대안 없음
    CANCELLED,
}

data class Trip(
    val id: String,
    val venueId: String,
    val conditions: TravelConditions?,
    val phase: TripPhase,
    val selectedCandidateId: String? = null,
    /** 이번 여행에서 이용 불가로 알려진 후보. 전체 사용자의 확정 정보로 승격하지 않는다. */
    val unavailableIds: Set<String> = emptySet(),
    val parkingNote: String? = null,
    /** 재계획이 끝난 뒤 돌아갈 단계 */
    val resumePhase: TripPhase? = null,
    /** 마지막 사용자 알림 문구. 예: 내비 요청 실패 */
    val notice: String? = null,
    /** 중복 이벤트 방지용. 같은 eventId는 한 번만 처리한다. */
    val processedEventIds: Set<String> = emptySet(),
) {
    companion object {
        fun start(id: String, venueId: String) = Trip(id = id, venueId = venueId, conditions = null, phase = TripPhase.PLANNING)
    }
}
