package app.atthedoor.platform.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.port.LocationPort
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** FusedLocation SDK 1.1.3 어댑터. 권한: pleos.car.permission.FUSED_LOCATION(dangerous, 런타임 요청 필요) */
class PleosLocationPort(context: Context, private val timeoutMs: Long = 3_000) : LocationPort {

    private val fused = FusedLocation(context.applicationContext).also { it.initialize() }

    override suspend fun current(): GeoPoint? {
        if (!fused.isSupported()) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                fused.getLastKnownLocation(object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) {
                        if (cont.isActive) cont.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                    }

                    override fun onError(exception: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        }
    }

    fun release() = fused.release()
}
