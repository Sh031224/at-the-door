package app.atthedoor.platform.fake

import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.port.DoorEvent
import app.atthedoor.domain.port.DrivingStatePort
import app.atthedoor.domain.port.GearState
import app.atthedoor.domain.port.LocationPort
import app.atthedoor.domain.port.NaviPort
import app.atthedoor.domain.port.NaviResult
import app.atthedoor.domain.port.SpeechPort
import app.atthedoor.domain.port.SpeechText
import app.atthedoor.domain.port.VehicleSignalPort
import app.atthedoor.domain.port.VoicePort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/*
 * 가짜 구현. 단위 테스트, 에뮬레이터에서 SDK가 응답하지 않을 때의 시연, 디버그 메뉴에서 쓴다.
 * 시연에 쓰면 화면에 ‘모의 응답’ 표시를 반드시 띄운다(앱의 isFake 플래그).
 */

enum class FakeNaviScenario { SUCCESS, FAIL, TIMEOUT }

class FakeNaviPort(
    var scenario: FakeNaviScenario = FakeNaviScenario.SUCCESS,
    private val stepDelayMs: Long = 800,
) : NaviPort {
    val requested = mutableListOf<AccessPoint>()

    override fun requestRoute(target: AccessPoint): Flow<NaviResult> = flow {
        requested += target
        delay(stepDelayMs)
        when (scenario) {
            FakeNaviScenario.SUCCESS -> {
                emit(NaviResult.Searching)
                delay(stepDelayMs)
                emit(NaviResult.Guiding)
            }
            FakeNaviScenario.FAIL -> emit(NaviResult.Failed("FAKE_SEARCH_ROUTE"))
            FakeNaviScenario.TIMEOUT -> emit(NaviResult.TimedOut)
        }
    }

    override fun cancel() = Unit
}

class FakeDrivingStatePort(restricted: Boolean = false) : DrivingStatePort {
    override val restricted = MutableStateFlow(restricted)
    override val setupBlocked = MutableStateFlow(restricted)

    fun set(driving: Boolean) {
        restricted.value = driving
        setupBlocked.value = driving
    }
}

class FakeVehicleSignalPort : VehicleSignalPort {
    override val gear = MutableStateFlow<GearState?>(GearState.PARK)
    override val doorEvents = MutableSharedFlow<DoorEvent>(extraBufferCapacity = 8)
    override val frontPassengerOccupied = MutableStateFlow<Boolean?>(true)
    override val frontPassengerBuckled = MutableStateFlow<Boolean?>(true)
}

class FakeLocationPort(var point: GeoPoint? = GeoPoint(37.49940, 127.03660)) : LocationPort {
    override suspend fun current(): GeoPoint? = point
}

/** 정해 둔 발화를 글자 단위로 흘려보낸다. */
class FakeSpeechPort(var script: String = "오늘 새봄문화회관 소공연장 가. 엄마랑 둘이고, 계단 없이 가까이 걸으면 좋겠어.") : SpeechPort {
    override fun listen(): Flow<SpeechText> = flow {
        val words = script.split(" ")
        words.forEachIndexed { i, _ ->
            delay(120)
            emit(SpeechText(words.take(i + 1).joinToString(" "), completed = i == words.lastIndex))
        }
    }

    override fun stop() = Unit
}

class FakeVoicePort : VoicePort {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) {
        spoken += text
    }
}
