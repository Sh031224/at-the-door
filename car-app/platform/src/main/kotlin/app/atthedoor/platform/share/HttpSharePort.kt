package app.atthedoor.platform.share

import app.atthedoor.domain.port.PhoneEvent
import app.atthedoor.domain.port.ReceivedPhoneEvent
import app.atthedoor.domain.port.ShareLink
import app.atthedoor.domain.port.SharePort
import app.atthedoor.domain.port.TripSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * web/ 서버의 공유 링크 API 클라이언트. 계약은 web/src/contract.ts 와 같아야 한다.
 * 네트워크가 실패하면 null 또는 빈 목록을 돌려주고, 앱은 차량 화면 안내로 대체한다.
 */
class HttpSharePort(private val baseUrl: String, private val timeoutMs: Int = 5_000) : SharePort {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }

    @Serializable
    private data class CreateTripRequest(
        val venueName: String,
        val planName: String,
        val entranceName: String,
        val entrancePhotos: List<String>,
        val photosAreIllustrations: Boolean,
        val steps: List<String>,
        val returnSteps: List<String>,
        val parkingNote: String?,
        val evidenceLabel: String,
        val demo: Boolean,
        val contact: String?,
        val walkSummary: String,
        val noAlternative: Boolean,
        val changeNote: String?,
    )

    private fun TripSnapshot.dto() = CreateTripRequest(
        venueName, planName, entranceName, entrancePhotos, photosAreIllustrations, steps, returnSteps, parkingNote, evidenceLabel, demo, contact, walkSummary, noAlternative, changeNote,
    )

    @Serializable
    private data class CreateTripResponse(val token: String, val url: String, val expiresAt: Long)

    @Serializable
    private data class EventDto(val id: String, val type: String, val note: String? = null)

    @Serializable
    private data class TripStatusResponse(val events: List<EventDto>)

    override suspend fun createLink(snapshot: TripSnapshot): ShareLink? = runCatching {
        val body = json.encodeToString(CreateTripRequest.serializer(), snapshot.dto())
        val res = json.decodeFromString(CreateTripResponse.serializer(), request("POST", "/api/trips", body))
        ShareLink(res.token, res.url, res.expiresAt)
    }.getOrNull()

    override suspend fun update(token: String, snapshot: TripSnapshot): Boolean = runCatching {
        request("PUT", "/api/trips/$token", json.encodeToString(CreateTripRequest.serializer(), snapshot.dto()))
        true
    }.getOrDefault(false)

    override suspend fun pollEvents(token: String): List<ReceivedPhoneEvent> = runCatching {
        json.decodeFromString(TripStatusResponse.serializer(), request("GET", "/api/trips/$token", null))
            .events.mapNotNull { e ->
                val event = when (e.type) {
                    "arrived" -> PhoneEvent.Arrived
                    "arrival_undone" -> PhoneEvent.ArrivalUndone
                    "entrance_blocked" -> PhoneEvent.EntranceBlocked
                    "returning" -> PhoneEvent.Returning
                    "parking_note" -> e.note?.let { PhoneEvent.ParkingNote(it) }
                    else -> null
                }
                event?.let { ReceivedPhoneEvent(e.id, it) }
            }
    }.getOrDefault(emptyList())

    override suspend fun delete(token: String) {
        runCatching { request("DELETE", "/api/trips/$token", null) }
    }

    private suspend fun request(method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
