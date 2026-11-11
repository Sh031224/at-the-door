package app.atthedoor.app

import android.content.Context
import app.atthedoor.data.TripStore
import app.atthedoor.data.VenueRepository
import app.atthedoor.domain.plan.PlanEvaluator
import app.atthedoor.domain.plan.Replanner
import app.atthedoor.domain.port.DrivingStatePort
import app.atthedoor.domain.port.LocationPort
import app.atthedoor.domain.port.NaviPort
import app.atthedoor.domain.port.SharePort
import app.atthedoor.domain.port.SpeechPort
import app.atthedoor.domain.port.VehicleSignalPort
import app.atthedoor.domain.port.VoicePort
import app.atthedoor.platform.car.CarUxDrivingStatePort
import app.atthedoor.platform.fake.FakeDrivingStatePort
import app.atthedoor.platform.fake.FakeLocationPort
import app.atthedoor.platform.fake.FakeNaviPort
import app.atthedoor.platform.fake.FakeSpeechPort
import app.atthedoor.platform.fake.FakeVehicleSignalPort
import app.atthedoor.platform.fake.FakeVoicePort
import app.atthedoor.platform.pleos.PleosLocationPort
import app.atthedoor.platform.pleos.PleosNaviPort
import app.atthedoor.platform.pleos.PleosSpeechPort
import app.atthedoor.platform.pleos.PleosVehicleSignalPort
import app.atthedoor.platform.pleos.PleosVoicePort
import app.atthedoor.platform.share.HttpSharePort

/**
 * 수동 의존성 조립. 모의 응답 모드면 모든 SDK 포트를 가짜로 바꾸고 화면에 ‘모의 응답’을 표시한다.
 * 모드는 빌드 기본값(USE_FAKES)이나 앱 설정에서 고르며, 바꾸면 화면을 다시 만든다.
 */
class AppContainer(context: Context, val isFake: Boolean) {
    private val app = context.applicationContext

    val venues = VenueRepository(app)
    val tripStore = TripStore(app)
    val evaluator = PlanEvaluator()
    val replanner = Replanner(evaluator)
    val share: SharePort = HttpSharePort(BuildConfig.SHARE_BASE_URL)

    private val carUx = if (isFake) null else CarUxDrivingStatePort(app)
    private val vehicleSignals = if (isFake) null else PleosVehicleSignalPort(app)

    /** 모의 응답 모드에서만 있는 시연 제어 대상. 실제 SDK 모드에서는 Virtual Console로 차량 상태를 바꾼다. */
    val demo: DemoControls? = if (isFake) DemoControls(FakeDrivingStatePort(), FakeVehicleSignalPort(), FakeNaviPort()) else null

    val drivingState: DrivingStatePort = carUx ?: demo!!.driving
    val vehicle: VehicleSignalPort = vehicleSignals ?: demo!!.vehicle
    val navi: NaviPort by lazy { demo?.navi ?: PleosNaviPort(app) }
    val location: LocationPort by lazy { if (isFake) FakeLocationPort() else PleosLocationPort(app) }
    val speech: SpeechPort by lazy { if (isFake) FakeSpeechPort() else PleosSpeechPort(app) }
    val voice: VoicePort by lazy { if (isFake) FakeVoicePort() else PleosVoicePort(app) }

    fun start() {
        carUx?.start()
        vehicleSignals?.start()
    }

    fun stop() {
        carUx?.stop()
        vehicleSignals?.stop()
    }
}

class DemoControls(
    val driving: FakeDrivingStatePort,
    val vehicle: FakeVehicleSignalPort,
    val navi: FakeNaviPort,
)
