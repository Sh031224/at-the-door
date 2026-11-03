package app.atthedoor.platform.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.port.NaviPort
import app.atthedoor.domain.port.NaviResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NaviHelper SDK 3.0.5 어댑터. 권한: pleos.car.permission.NAVI_ROUTE_SEARCH
 *
 * 문서: "requestRoute() 호출이 정상적으로 수행되더라도 경로 탐색 완료를 의미하지 않습니다.
 * 최종 결과는 이벤트 콜백으로 확인합니다." 그래서 onRoutingCompleted를 받아야 안내 중으로 본다.
 */
class PleosNaviPort(
    context: Context,
    private val connectTimeoutMs: Long = 5_000,
    private val guidingTimeoutMs: Long = 15_000,
) : NaviPort {

    private val helper = NaviHelper(context.applicationContext)
    private val connected = CompletableDeferred<Boolean>()

    init {
        helper.initialize(
            onConnected = { connected.complete(true) },
            onFailed = { connected.complete(false) },
        )
    }

    override fun requestRoute(target: AccessPoint): Flow<NaviResult> = callbackFlow {
        var settled = false
        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                trySend(NaviResult.Searching)
            }

            override fun onRoutingCompleted() {
                settled = true
                trySend(NaviResult.Guiding)
            }

            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                trySend(NaviResult.Arrived)
            }

            override fun onRouteCancelled() {
                trySend(NaviResult.Cancelled)
            }

            override fun onRouteEnded() {
                trySend(NaviResult.Ended)
            }

            override fun onDestinationChanged() {
                trySend(NaviResult.DestinationChanged)
            }

            override fun onError(errorCode: NaviErrorCode) {
                settled = true
                trySend(NaviResult.Failed(errorCode.toString()))
            }
        }

        val ok = withTimeoutOrNull(connectTimeoutMs) { connected.await() } ?: false
        if (!ok) {
            send(NaviResult.Failed("NAVI_SERVICE_NOT_CONNECTED"))
            close()
            return@callbackFlow
        }

        helper.addListener(listener)
        helper.requestRoute(
            RouteInfo(
                latitude = target.point.lat,
                longitude = target.point.lng,
                displayLatitude = null,
                displayLongitude = null,
                poiName = target.approach,
                poiId = "",
                address = "",
                poiSubId = "0",
                routeOption = RouteOption.RECOMMENDED,
                poiProvider = "",
                isMobileRequest = false,
            ),
        )
        launch {
            delay(guidingTimeoutMs)
            if (!settled) trySend(NaviResult.TimedOut)
        }
        awaitClose { helper.removeListener(listener) }
    }

    override fun cancel() {
        helper.cancelRoute()
    }

    fun release() {
        helper.release()
    }
}
