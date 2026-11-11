package app.atthedoor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.trip.FeedbackKind
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripPhase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.tripDataStore by preferencesDataStore(name = "trip")

/**
 * 진행 중인 여행 하나를 저장한다. 앱을 다시 열면 마지막 계획·조건·주차 메모를 복원하고,
 * 화면은 복원된 상태를 그대로 믿지 않고 현재 상황을 다시 확인한다.
 */
class TripStore(private val context: Context) {
    private val key = stringPreferencesKey("current_trip")

    suspend fun save(trip: Trip) {
        context.tripDataStore.edit { it[key] = TripCodec.encode(trip) }
    }

    suspend fun load(): Trip? = context.tripDataStore.data.first()[key]?.let(TripCodec::decodeOrNull)

    suspend fun clear() {
        context.tripDataStore.edit { it.remove(key) }
    }
}

/** 저장 형식. 도메인 모델과 분리해 두고 버전을 붙인다. */
object TripCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TripRecord(
        val v: Int = 1,
        val id: String,
        val venueId: String,
        val phase: String,
        val selectedCandidateId: String? = null,
        val unavailableIds: Set<String> = emptySet(),
        val blockedEntranceIds: Set<String> = emptySet(),
        val parkingNote: String? = null,
        val parkedAt: List<Double>? = null,
        val resumePhase: String? = null,
        val notice: String? = null,
        val feedback: Set<String>? = null,
        val processedEventIds: Set<String> = emptySet(),
        val conditions: ConditionsRecord? = null,
    )

    @Serializable
    private data class ConditionsRecord(
        val companionRequired: Boolean,
        val otherGuardian: Boolean,
        val stepFreeRequired: Boolean,
        val preferShortWalk: Boolean,
        val mobilityAid: String,
        val hasDisabledParkingPermit: Boolean,
    )

    fun encode(t: Trip): String = json.encodeToString(
        TripRecord.serializer(),
        TripRecord(
            id = t.id,
            venueId = t.venueId,
            phase = t.phase.name,
            selectedCandidateId = t.selectedCandidateId,
            unavailableIds = t.unavailableIds,
            blockedEntranceIds = t.blockedEntranceIds,
            parkingNote = t.parkingNote,
            parkedAt = t.parkedAt?.let { listOf(it.lat, it.lng) },
            resumePhase = t.resumePhase?.name,
            notice = t.notice,
            feedback = t.feedback?.map { it.name }?.toSet(),
            processedEventIds = t.processedEventIds,
            conditions = t.conditions?.let {
                ConditionsRecord(it.companionRequired, it.otherGuardian, it.stepFreeRequired, it.preferShortWalk, it.mobilityAid.name, it.hasDisabledParkingPermit)
            },
        ),
    )

    fun decodeOrNull(text: String): Trip? = runCatching {
        val r = json.decodeFromString(TripRecord.serializer(), text)
        Trip(
            id = r.id,
            venueId = r.venueId,
            phase = TripPhase.valueOf(r.phase),
            selectedCandidateId = r.selectedCandidateId,
            unavailableIds = r.unavailableIds,
            blockedEntranceIds = r.blockedEntranceIds,
            parkingNote = r.parkingNote,
            parkedAt = r.parkedAt?.takeIf { it.size == 2 }?.let { GeoPoint(it[0], it[1]) },
            resumePhase = r.resumePhase?.let(TripPhase::valueOf),
            notice = r.notice,
            feedback = r.feedback?.map(FeedbackKind::valueOf)?.toSet(),
            processedEventIds = r.processedEventIds,
            conditions = r.conditions?.let {
                TravelConditions(it.companionRequired, it.otherGuardian, it.stepFreeRequired, it.preferShortWalk, MobilityAid.valueOf(it.mobilityAid), it.hasDisabledParkingPermit)
            },
        )
    }.getOrNull()
}
