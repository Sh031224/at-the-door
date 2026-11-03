package app.atthedoor.platform.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import app.atthedoor.domain.port.DrivingStatePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 제한 상태. 플랫폼이 지역별 규제에 맞는 ActiveRestrictions를 내려주므로 앱은 받은 값만 반영한다.
 * Car 서비스에 연결되기 전이나 연결이 끊기면 제한 중(true)으로 본다.
 */
class CarUxDrivingStatePort(private val context: Context) : DrivingStatePort {

    private val _restricted = MutableStateFlow(true)
    override val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private val _setupBlocked = MutableStateFlow(true)
    override val setupBlocked: StateFlow<Boolean> = _setupBlocked.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        car = Car.createCar(context.applicationContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (!ready) {
                apply(null)
                return@createCar
            }
            val mgr = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = mgr
            apply(mgr.currentCarUxRestrictions)
            mgr.registerListener { apply(it) }
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        car?.disconnect()
    }

    private fun apply(r: CarUxRestrictions?) {
        if (r == null) {
            _restricted.value = true
            _setupBlocked.value = true
            return
        }
        _restricted.value = r.isRequiresDistractionOptimization
        _setupBlocked.value = r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) != 0
    }
}
