package app.atthedoor.platform.fake

import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.model.GeoPoint
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
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

    /** 시연 제어에서 ‘내비 안내 취소’를 누르면 진행 중인 요청에 Cancelled를 흘려보낸다. */
    private val external = kotlinx.coroutines.flow.MutableSharedFlow<NaviResult>(extraBufferCapacity = 4)

    override fun requestRoute(target: AccessPoint): Flow<NaviResult> = kotlinx.coroutines.flow.channelFlow {
        requested += target
        launch { external.collect { send(it) } }
        delay(stepDelayMs)
        when (scenario) {
            FakeNaviScenario.SUCCESS -> {
                send(NaviResult.Searching)
                delay(stepDelayMs)
                send(NaviResult.Guiding)
            }
            FakeNaviScenario.FAIL -> send(NaviResult.Failed("FAKE_SEARCH_ROUTE"))
            FakeNaviScenario.TIMEOUT -> {
                send(NaviResult.Searching)
                delay(stepDelayMs * 3)
                send(NaviResult.TimedOut)
            }
        }
        kotlinx.coroutines.awaitCancellation()
    }

    fun simulate(result: NaviResult) {
        external.tryEmit(result)
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

/** 시연 제어 패널에서 기어·벨트·도어를 바꾼다. 처음에는 주행 준비 상태(D, 벨트 체결, 문 닫힘)로 둔다. */
class FakeVehicleSignalPort : VehicleSignalPort {
    override val gear = MutableStateFlow<GearState?>(GearState.PARK)
    override val frontPassengerDoorOpen = MutableStateFlow<Boolean?>(false)
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
