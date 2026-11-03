package app.atthedoor.data

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
    fun `여행 상태를 저장 형식으로 바꿨다가 그대로 복원한다`() {
        val trip = TripMachine.reduce(
            Trip.start("t1", "saebom-hall"),
            TripEvent.ConditionsConfirmed(TravelConditions(true, false, true)),
            "e1",
        )
        assertEquals(trip, TripCodec.decodeOrNull(TripCodec.encode(trip)))
    }
}
