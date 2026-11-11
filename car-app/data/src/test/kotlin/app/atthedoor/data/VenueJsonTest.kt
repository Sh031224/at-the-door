package app.atthedoor.data

import app.atthedoor.domain.model.PhotoKind
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.plan.PlanEvaluator
import app.atthedoor.domain.plan.VerdictKind
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripMachine
import java.io.File
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VenueJsonTest {
    /** 단위 테스트의 작업 디렉터리는 모듈 폴더(car-app/data)다. */
    private val venuesDir = File("../../data/venues")

    @Test
    fun `저장소의 모든 시설 파일을 읽을 수 있다`() {
        val files = venuesDir.listFiles { f -> f.extension == "json" }!!.toList()
        assertFalse(files.isEmpty())
        files.forEach { VenueJson.parse(it.readText()) }
    }

    @Test
    fun `시연 데이터로 할머니 자리 시나리오 판정이 재현된다`() {
        val venue = VenueJson.parse(File(venuesDir, "saebom-hall.json").readText())
        val solo = TravelConditions(companionRequired = true, otherGuardian = false, stepFreeRequired = true, preferShortWalk = true)

        val result = PlanEvaluator().evaluate(venue, solo, LocalTime.of(18, 30))

        assertEquals(VerdictKind.EXCLUDED, result.verdictOf("A")?.kind)
        assertEquals("B", result.recommended?.candidate?.id)
        assertEquals(VerdictKind.NEEDS_CHECK, result.verdictOf("C")?.kind)
    }

    @Test
    fun `병원 시연 데이터는 계단 있는 외래동을 빼고 장애인 구역은 자격을 확인해야 후보가 된다`() {
        val venue = VenueJson.parse(File(venuesDir, "hanbit-hospital.json").readText())
        val solo = TravelConditions(companionRequired = true, otherGuardian = false, stepFreeRequired = true, preferShortWalk = true)
        val r = PlanEvaluator().evaluate(venue, solo, LocalTime.of(10, 0))
        assertEquals("B", r.recommended?.candidate?.id)
        assertEquals(VerdictKind.EXCLUDED, r.verdictOf("C")?.kind)
        assertEquals(VerdictKind.EXCLUDED, r.verdictOf("D")?.kind)
        val permitted = PlanEvaluator().evaluate(venue, solo.copy(hasDisabledParkingPermit = true), LocalTime.of(10, 0))
        assertEquals("D", permitted.recommended?.candidate?.id)
    }

    @Test
    fun `공연장 데이터는 같은 후문 주차장 후보끼리 lotId를 공유하고 돌아가는 순서를 갖는다`() {
        val venue = VenueJson.parse(File(venuesDir, "saebom-hall.json").readText())
        assertEquals(venue.candidate("B")?.lotId, venue.candidate("B2")?.lotId)
        assertFalse(venue.candidate("B")?.walk?.returnSteps.isNullOrEmpty())
        assertEquals(PhotoKind.ILLUSTRATION, venue.entrance("E-back")?.photoKind)
    }

    @Test
    fun `여행 상태를 저장 형식으로 바꿨다가 그대로 복원한다`() {
        val trip = TripMachine.reduce(
            Trip.start("t1", "saebom-hall"),
            TripEvent.ConditionsConfirmed(TravelConditions(true, false, true)),
            "e1",
        )
        assertEquals(trip, TripCodec.decodeOrNull(TripCodec.encode(trip)))
    }

    @Test
    fun `재계획·주차 위치·제보까지 저장했다가 그대로 복원한다`() {
        val trip = Trip.start("t2", "saebom-hall").copy(
            phase = app.atthedoor.domain.trip.TripPhase.COMPLETED,
            conditions = TravelConditions(true, false, true),
            selectedCandidateId = "B2",
            unavailableIds = setOf("B"),
            blockedEntranceIds = setOf("E-back"),
            parkingNote = "B2 C구역",
            parkedAt = app.atthedoor.domain.model.GeoPoint(37.5, 127.0),
            feedback = setOf(app.atthedoor.domain.trip.FeedbackKind.ROUTE),
            processedEventIds = setOf("e1"),
        )
        assertEquals(trip, TripCodec.decodeOrNull(TripCodec.encode(trip)))
    }
}
