package app.atthedoor.data

import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Eligibility
import app.atthedoor.domain.model.Entrance
import app.atthedoor.domain.model.Evidence
import app.atthedoor.domain.model.Fact
import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.model.OpeningHours
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.model.VerificationStatus
import app.atthedoor.domain.model.WalkRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalTime

/** 저장소 루트 data/venues 폴더의 JSON 형식. 도메인 모델과 분리해 파일 형식이 바뀌어도 규칙 엔진이 흔들리지 않게 한다. */
object VenueJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Venue = json.decodeFromString(VenueDto.serializer(), text).toDomain()

    @Serializable
    private data class VenueDto(
        val venueId: String,
        val name: String,
        val demo: Boolean,
        val contact: String? = null,
        val entrances: List<EntranceDto>,
        val candidates: List<CandidateDto>,
        val evidence: List<EvidenceDto>,
    )

    @Serializable
    private data class EntranceDto(
        val id: String,
        val name: String,
        val point: List<Double>,
        val photos: List<String> = emptyList(),
        val hours: String? = null,
        val stepFree: FactDto<Boolean>? = null,
    )

    @Serializable
    private data class CandidateDto(
        val id: String,
        val type: String,
        val name: String,
        val accessPoint: AccessPointDto,
        val walk: WalkDto,
        val leavesPassengerAlone: Boolean = false,
        val driverParkingId: String? = null,
        val eligibility: String = "general",
        val hours: String? = null,
        val liveAvailability: String? = null,
    )

    @Serializable
    private data class AccessPointDto(val point: List<Double>, val approach: String)

    @Serializable
    private data class WalkDto(
        val toEntrance: String,
        val stairs: FactDto<Boolean>? = null,
        val distanceM: FactDto<Int>? = null,
        val steps: List<String> = emptyList(),
    )

    @Serializable
    private data class FactDto<T>(val value: T? = null, val evidence: String? = null)

    @Serializable
    private data class EvidenceDto(val id: String, val source: String, val checkedAt: String, val status: String)

    private fun VenueDto.toDomain(): Venue {
        val ev = evidence.associate { it.id to Evidence(it.id, it.source, it.checkedAt, status(it.status)) }
        fun <T> FactDto<T>?.fact(): Fact<T> = if (this == null) Fact.unknown() else Fact(value, evidence?.let { requireNotNull(ev[it]) { "unknown evidence id: $it" } })
        return Venue(
            id = venueId,
            name = name,
            demo = demo,
            contact = contact,
            entrances = entrances.map { e ->
                Entrance(e.id, e.name, e.point.geo(), e.photos, e.hours?.hours(), e.stepFree.fact())
            },
            candidates = candidates.map { c ->
                Candidate(
                    id = c.id,
                    type = when (c.type) {
                        "dropoff" -> CandidateType.DROPOFF
                        "parking" -> CandidateType.PARKING
                        else -> error("unknown candidate type: ${c.type}")
                    },
                    name = c.name,
                    accessPoint = AccessPoint(c.accessPoint.point.geo(), c.accessPoint.approach),
                    walk = WalkRoute(c.walk.toEntrance, c.walk.stairs.fact(), c.walk.distanceM.fact(), c.walk.steps),
                    leavesPassengerAlone = c.leavesPassengerAlone,
                    driverParkingId = c.driverParkingId,
                    eligibility = if (c.eligibility == "disabled_only") Eligibility.DISABLED_ONLY else Eligibility.GENERAL,
                    hours = c.hours?.hours(),
                    liveAvailabilitySource = c.liveAvailability,
                )
            },
        )
    }

    private fun status(s: String) = when (s) {
        "verified" -> VerificationStatus.VERIFIED
        "demo" -> VerificationStatus.DEMO
        else -> VerificationStatus.UNVERIFIED
    }

    private fun List<Double>.geo(): GeoPoint {
        require(size == 2) { "point must be [lat, lng]" }
        return GeoPoint(this[0], this[1])
    }

    private fun String.hours(): OpeningHours {
        val (open, close) = split("-").map { LocalTime.parse(it.trim()) }
        return OpeningHours(open, close)
    }
}
