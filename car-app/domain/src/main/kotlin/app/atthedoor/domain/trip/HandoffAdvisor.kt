package app.atthedoor.domain.trip

import app.atthedoor.domain.port.GearState

/** 하차 준비를 판단할 때 쓰는 차량 보조 신호의 한 시점. 값이 없으면(null) 그 신호를 쓰지 않는다. */
data class VehicleSignals(
    val gear: GearState? = null,
    val passengerBuckled: Boolean? = null,
    val passengerDoorOpen: Boolean? = null,
)

/** 차량 신호가 알려 주는 하차 준비 단계. 순서대로 진행된다: 기어 P → 동승석 벨트 해제 → 동승석 도어 열림 */
enum class HandoffCue(val message: String) {
    PARKED_GEAR("기어가 P로 바뀌었어요. 주차했으면 아래 ‘주차했어요’를 눌러 주세요."),
    BELT_RELEASED("동승석 안전벨트가 풀렸어요. 주차를 확인하면 입구 안내를 이어 드릴게요."),
    DOOR_OPENED("동승석 문이 열렸어요. 주차를 확인하고 휴대폰으로 입구 안내를 이어 보세요."),
}

/**
 * 차량 신호로 ‘주차 확인’을 권할 시점을 고른다. 신호만으로 주차나 도착을 확정하지 않는다.
 *
 * 현재 상태가 아니라 ‘변화’로 판단한다. 출발 전부터 벨트가 풀려 있거나 P에 있던 차에서 안내가 뜨면 틀린 말이 되므로,
 * 차량 접근 단계에 들어와 한 번이라도 주행(주행 제한)을 한 뒤에, 그리고 주행 제한이 풀린 상태에서만 권한다.
 * 상태를 가지므로 화면 하나(ViewModel)당 하나를 쓴다.
 */
class HandoffWatcher {
    private var drove = false
    private var wasRestricted = false
    private var last: VehicleSignals? = null
    private var cue: HandoffCue? = null

    fun observe(phase: TripPhase, drivingRestricted: Boolean, s: VehicleSignals): HandoffCue? {
        if (phase != TripPhase.APPROACHING) {
            reset()
            return null
        }
        val prev = last
        val justStopped = wasRestricted && !drivingRestricted
        last = s
        wasRestricted = drivingRestricted
        if (drivingRestricted) {
            drove = true
            cue = null
            return null
        }
        if (!drove || prev == null) return cue
        val next = when {
            prev.passengerDoorOpen == false && s.passengerDoorOpen == true -> HandoffCue.DOOR_OPENED
            prev.passengerBuckled == true && s.passengerBuckled == false -> HandoffCue.BELT_RELEASED
            // 기어 P 신호가 주행 제한 해제보다 먼저 올 수 있어, 제한이 풀리는 순간 이미 P면 그것도 변화로 본다.
            s.gear == GearState.PARK && (prev.gear != GearState.PARK || justStopped) -> HandoffCue.PARKED_GEAR
            else -> null
        }
        // 더 늦은 단계의 신호가 오면 바꾸고, 그렇지 않으면 이미 보여 준 권유를 유지한다.
        if (next != null && (cue == null || next.ordinal > cue!!.ordinal)) cue = next
        return cue
    }

    private fun reset() {
        drove = false
        wasRestricted = false
        last = null
        cue = null
    }
}
