package app.atthedoor.domain.model

/**
 * 사용자가 지정한 보조기구. 질병이나 장애 여부를 추정하지 않고 사용자가 고른 값만 쓴다.
 * 접이식 휠체어와 탑승형(전동) 휠체어는 필요한 조건이 달라 따로 둔다.
 */
enum class MobilityAid(val label: String) {
    NONE("없음"),
    WALKER("보행 보조기"),
    STROLLER("유아차"),
    FOLDING_WHEELCHAIR("접이식 휠체어"),
    POWER_WHEELCHAIR("전동 휠체어"),
}

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
        get() = stepFreeRequired || mobilityAid.isWheelchair

    /**
     * 차량 화면에만 보여 줄 조건 이름. 보행 제약·보조기구·자격은 건강 관련으로 볼 여지가 있어 기기 밖으로 보내지 않는다.
     * 휴대폰 안내에는 이미 이 조건으로 고른 계획(입구·순서)만 넘긴다.
     */
    val labels: List<String>
        get() = buildList {
            add(companionLabel)
            if (effectiveStepFreeRequired) add(Labels.STEP_FREE)
            if (preferShortWalk) add(Labels.SHORT_WALK)
        } + listOfNotNull(
            mobilityAid.takeIf { it != MobilityAid.NONE }?.label,
            Labels.PERMIT.takeIf { hasDisabledParkingPermit },
        )

    val companionLabel: String
        get() = when {
            !companionRequired -> Labels.ALONE_OK
            otherGuardian -> Labels.OTHER_GUARDIAN
            else -> Labels.DRIVER_WITH
        }

    companion object {
        /** 할머니 자리 시나리오의 기본값: 운전자가 유일한 보호자, 계단 없이, 가까이 */
        val DEFAULT = TravelConditions(companionRequired = true, otherGuardian = false, stepFreeRequired = true, preferShortWalk = true)
    }
}

/** 조건 이름은 모든 화면에서 이 하나만 쓴다. */
object Labels {
    const val DRIVER_WITH = "제가 같이 내려요"
    const val OTHER_GUARDIAN = "다른 보호자도 있어요"
    const val ALONE_OK = "함께 가지 않아도 돼요"
    const val STEP_FREE = "계단 없이"
    const val SHORT_WALK = "짧게 걷기"
    const val PERMIT = "장애인 주차 표지 있음"
}

val MobilityAid.isWheelchair: Boolean
    get() = this == MobilityAid.FOLDING_WHEELCHAIR || this == MobilityAid.POWER_WHEELCHAIR
