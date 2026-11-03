package app.atthedoor.app

import android.content.Context
import app.atthedoor.data.TripStore
import app.atthedoor.data.VenueRepository
import app.atthedoor.domain.plan.PlanEvaluator
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

/** 수동 의존성 조립. USE_FAKES면 모든 SDK 포트를 가짜로 바꾸고 화면에 ‘모의 응답’을 표시한다. */
class AppContainer(context: Context, val isFake: Boolean = BuildConfig.USE_FAKES) {
    private val app = context.applicationContext

    val venues = VenueRepository(app)
    val tripStore = TripStore(app)
    val evaluator = PlanEvaluator()
    val share: SharePort = HttpSharePort(BuildConfig.SHARE_BASE_URL)

    private val carUx = if (isFake) null else CarUxDrivingStatePort(app)
    private val vehicleSignals = if (isFake) null else PleosVehicleSignalPort(app)

    val drivingState: DrivingStatePort = carUx ?: FakeDrivingStatePort()
    val vehicle: VehicleSignalPort = vehicleSignals ?: FakeVehicleSignalPort()
    val navi: NaviPort by lazy { if (isFake) FakeNaviPort() else PleosNaviPort(app) }
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
