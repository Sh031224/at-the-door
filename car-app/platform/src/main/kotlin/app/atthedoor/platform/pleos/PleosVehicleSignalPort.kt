package app.atthedoor.platform.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import app.atthedoor.domain.port.GearState
import app.atthedoor.domain.port.VehicleSignalPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vehicle SDK 2.3.2 어댑터. 조회 권한만 쓴다: CAR_DRIVE(Gear), CAR_DOORS(Door), CAR_SEATS(Seat).
 * 한국 좌측 운전석 기준으로 동승석은 ROW_1_RIGHT 다.
 * 신호 실패는 null 로 두고, 기능 판단은 DrivingStatePort(CarUxRestrictions)가 맡는다.
 */
class PleosVehicleSignalPort(context: Context) : VehicleSignalPort {

    private val vehicle = Vehicle(context.applicationContext)

    private val _gear = MutableStateFlow<GearState?>(null)
    override val gear: StateFlow<GearState?> = _gear.asStateFlow()

    private val _doorOpen = MutableStateFlow<Boolean?>(null)
    override val frontPassengerDoorOpen: StateFlow<Boolean?> = _doorOpen.asStateFlow()

    private val _occupied = MutableStateFlow<Boolean?>(null)
    override val frontPassengerOccupied: StateFlow<Boolean?> = _occupied.asStateFlow()

    private val _buckled = MutableStateFlow<Boolean?>(null)
    override val frontPassengerBuckled: StateFlow<Boolean?> = _buckled.asStateFlow()

    private val passengerDoor = DoorArea(DoorValue.ROW_1_RIGHT)
    private val passengerSeat = SeatArea(SeatValue.ROW_1_RIGHT)

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.d(TAG, "gear=$gear")
            _gear.value = gear?.toState()
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "gear failed", exception)
            _gear.value = null
        }
    }

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            Log.d(TAG, "door position=$position")
            _doorOpen.value = position?.let { it > 0 }
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "door failed", exception)
            _doorOpen.value = null
        }
    }

    private val occupancyListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) {
            _occupied.value = occupied
        }

        override fun onFailed(exception: Exception) {
            _occupied.value = null
        }
    }

    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
            Log.d(TAG, "passenger belt buckled=$buckled")
            _buckled.value = buckled
        }

        override fun onFailed(exception: Exception) {
            _buckled.value = null
        }
    }

    fun start() = runCatching {
        vehicle.initialize()
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getDoor().registerDoorPosition(passengerDoor, doorListener)
        // 좌석 신호는 차량마다 지원 여부가 달라 Capability를 먼저 확인한다.
        vehicle.getSeat().checkSeatOccupancyCapabilities({ areas ->
            if (areas.any { passengerSeat.values.first() in it.values }) {
                vehicle.getSeat().registerSeatOccupancy(passengerSeat, occupancyListener)
            }
        }, { _occupied.value = null })
        vehicle.getSeat().checkSeatBeltBuckledCapabilities({ areas ->
            if (areas.any { passengerSeat.values.first() in it.values }) {
                vehicle.getSeat().registerSeatBeltBuckledState(passengerSeat, beltListener)
            }
        }, { _buckled.value = null })
    }.onFailure { Log.w(TAG, "vehicle signals unavailable", it) }

    fun stop() {
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getDoor().unregisterDoorPosition(passengerDoor, doorListener) }
        runCatching { vehicle.getSeat().unregisterSeatOccupancy(passengerSeat, occupancyListener) }
        runCatching { vehicle.getSeat().unregisterSeatBeltBuckledState(passengerSeat, beltListener) }
        vehicle.release()
    }

    private companion object {
        const val TAG = "AtTheDoor.Vehicle"
    }

    private fun VehicleGear.toState(): GearState = when (this) {
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_REVERSE -> GearState.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
        else -> GearState.DRIVE
    }
}
