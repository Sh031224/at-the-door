package app.atthedoor.domain.model

/**
 * 사용자가 지정한 보조기구. 질병이나 장애 여부를 추정하지 않고 사용자가 고른 값만 쓴다.
 * 접이식 휠체어와 탑승형(전동) 휠체어는 필요한 조건이 달라 따로 둔다.
 */
enum class MobilityAid { NONE, WALKER, STROLLER, FOLDING_WHEELCHAIR, POWER_WHEELCHAIR }

data class TravelConditions(
    /** 운전자가 동승자와 입구까지 함께 이동해야 하는지 */
    val companionRequired: Boolean,
    /** 운전자 외에 동승자와 함께할 다른 보호자가 있는지 */
    val otherGuardian: Boolean,
    /** 계단 없는 동선이 필수인지 */
    val stepFreeRequired: Boolean,
    /** 보행 거리를 짧게 하는 것을 선호하는지(필수 아님) */
    val preferShortWalk: Boolean = false,
    val mobilityAid: MobilityAid = MobilityAid.NONE,
    /** 장애인 전용 주차 자격을 사용자가 확인했는지 */
    val hasDisabledParkingPermit: Boolean = false,
) {
    /** 휠체어는 계단 없는 동선을 항상 필수로 본다. */
    val effectiveStepFreeRequired: Boolean
        get() = stepFreeRequired || mobilityAid == MobilityAid.FOLDING_WHEELCHAIR ||
            mobilityAid == MobilityAid.POWER_WHEELCHAIR
}
