package app.atthedoor.platform.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
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
 * 최종 결과는 이벤트 콜백으로 확인합니다." 그래서 호출 성공만으로는 안내 중으로 보지 않는다.
 * 안내 시작 판정: onRoutingCompleted(문서), 또는 우리가 보낸 목적지 이름과 계산된 거리가 담긴 onRouteStarted(에뮬레이터 실측).
 */
class PleosNaviPort(
    context: Context,
    private val connectTimeoutMs: Long = 5_000,
    private val guidingTimeoutMs: Long = 30_000,
) : NaviPort {

    private val helper = NaviHelper(context.applicationContext)
    private val connected = CompletableDeferred<Boolean>()

    init {
        helper.initialize(
            onConnected = { Log.d(TAG, "navi service connected"); connected.complete(true) },
            onFailed = { Log.w(TAG, "navi service connect failed"); connected.complete(false) },
        )
    }

    override fun requestRoute(target: AccessPoint): Flow<NaviResult> = callbackFlow {
        var settled = false
        var guidingAt = 0L
        fun guiding(reason: String, info: RouteStartInfo? = null) {
            if (settled) return
            settled = true
            guidingAt = SystemClock.elapsedRealtime()
            Log.d(TAG, "guiding by $reason")
            trySend(NaviResult.Guiding(info?.duration?.takeIf { it > 0 }, info?.distance?.takeIf { it > 0 }))
        }
        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                Log.d(TAG, "onRouteStarted $routeStartInfo")
                // Pleos Connect Emulator v2.1.0 실측(2026-11-11): Navigation 앱은 경로 탐색을 마치고 안내를 시작할 때
                // 목적지 이름·거리·시간을 담아 onRouteStarted를 보내고, onRoutingCompleted는 보내지 않았다.
                // 그래서 ‘우리가 보낸 목적지 이름 + 계산된 거리’가 있으면 안내 시작으로 본다. 그렇지 않으면 탐색 중이다.
                if (routeStartInfo.name == target.approach && routeStartInfo.distance > 0) guiding("onRouteStarted", routeStartInfo)
                else trySend(NaviResult.Searching)
            }

            override fun onRoutingCompleted() {
                Log.d(TAG, "onRoutingCompleted")
                guiding("onRoutingCompleted")
            }

            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                Log.d(TAG, "onDestinationArrived")
                trySend(NaviResult.Arrived)
            }

            override fun onRouteCancelled() {
                Log.d(TAG, "onRouteCancelled")
                trySend(NaviResult.Cancelled)
            }

            override fun onRouteEnded() {
                // 실측: 안내 시작 직후에도 onRouteEnded가 한 번 붙어 온다. 그 직후의 것은 무시한다.
                val sinceStart = SystemClock.elapsedRealtime() - guidingAt
                Log.d(TAG, "onRouteEnded (settled=$settled, ${sinceStart}ms after guiding)")
                if (settled && sinceStart > SPURIOUS_END_WINDOW_MS) trySend(NaviResult.Ended)
            }

            override fun onNavigationStatus(navigationStatus: NavigationStatus) {
                Log.d(TAG, "onNavigationStatus $navigationStatus")
                if (settled && navigationStatus != NavigationStatus.ROUTE_DRIVE &&
                    SystemClock.elapsedRealtime() - guidingAt > SPURIOUS_END_WINDOW_MS
                ) trySend(NaviResult.Ended)
            }

            override fun onDestinationChanged() {
                Log.d(TAG, "onDestinationChanged")
                trySend(NaviResult.DestinationChanged)
            }

            override fun onError(errorCode: NaviErrorCode) {
                Log.w(TAG, "onError $errorCode")
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
        Log.d(TAG, "requestRoute ${target.approach} ${target.point}")
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

    private companion object {
        const val TAG = "AtTheDoor.Navi"
        const val SPURIOUS_END_WINDOW_MS = 3_000L
    }

    override fun cancel() {
        helper.cancelRoute()
    }

    fun release() {
        helper.release()
    }
}
