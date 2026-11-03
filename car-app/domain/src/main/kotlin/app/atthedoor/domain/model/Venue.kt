package app.atthedoor.domain.model

import java.time.LocalTime

data class GeoPoint(val lat: Double, val lng: Double)

enum class VerificationStatus {
    /** 현장에서 실제로 확인한 정보 */
    VERIFIED,
    /** 시연용으로 만든 정보. 운영 데이터와 섞지 않는다. */
    DEMO,
    /** 출처는 있으나 확인되지 않은 정보. 판정에서는 ‘모름’으로 취급한다. */
    UNVERIFIED,
}

data class Evidence(
    val id: String,
    val source: String,
    val checkedAt: String,
    val status: VerificationStatus,
)

/**
 * 근거가 붙은 사실 하나. 값이 없거나 근거가 없거나 미확인이면 [known]이 false다.
 * 규칙 엔진은 known이 아닌 사실을 충족으로 간주하지 않는다.
 */
data class Fact<out T>(val value: T?, val evidence: Evidence?) {
    val known: Boolean
        get() = value != null && evidence != null && evidence.status != VerificationStatus.UNVERIFIED

    companion object {
        fun <T> unknown(): Fact<T> = Fact(null, null)
    }
}

data class OpeningHours(val open: LocalTime, val close: LocalTime) {
    fun isOpenAt(time: LocalTime): Boolean = !time.isBefore(open) && time.isBefore(close)
}

data class Entrance(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val photos: List<String>,
    val hours: OpeningHours?,
    val stepFree: Fact<Boolean>,
)

data class AccessPoint(
    val point: GeoPoint,
    /** 차량 내비게이션에 넘길 표시 이름. 예: "후문 주차장 입구" */
    val approach: String,
)

enum class CandidateType { DROPOFF, PARKING }

enum class Eligibility { GENERAL, DISABLED_ONLY }

data class WalkRoute(
    val toEntranceId: String,
    val stairs: Fact<Boolean>,
    /** 확인 가능한 경로 데이터가 있을 때만 값이 있다. 직선거리를 넣지 않는다. */
    val distanceM: Fact<Int>,
    val steps: List<String>,
)

data class Candidate(
    val id: String,
    val type: CandidateType,
    val name: String,
    val accessPoint: AccessPoint,
    val walk: WalkRoute,
    /** 하차 계획에서 동승자가 운전자 없이 혼자 남는지 */
    val leavesPassengerAlone: Boolean = false,
    /** 하차 계획일 때 운전자가 이어서 주차할 후보 ID. 없으면 하차 계획을 끝까지 실행할 수 없다. */
    val driverParkingId: String? = null,
    val eligibility: Eligibility = Eligibility.GENERAL,
    val hours: OpeningHours? = null,
    /** 실시간 잔여 정보 출처. 없으면 현장 확인 사항으로 남긴다. */
    val liveAvailabilitySource: String? = null,
)

data class Venue(
    val id: String,
    val name: String,
    val demo: Boolean,
    val contact: String?,
    val entrances: List<Entrance>,
    val candidates: List<Candidate>,
) {
    fun entrance(id: String): Entrance? = entrances.firstOrNull { it.id == id }
    fun candidate(id: String): Candidate? = candidates.firstOrNull { it.id == id }
}
