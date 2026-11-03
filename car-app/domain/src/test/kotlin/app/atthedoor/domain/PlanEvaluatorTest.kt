package app.atthedoor.domain

import app.atthedoor.domain.Fixtures.noon
import app.atthedoor.domain.Fixtures.soloGuardian
import app.atthedoor.domain.Fixtures.venue
import app.atthedoor.domain.Fixtures.withOtherGuardian
import app.atthedoor.domain.model.Eligibility
import app.atthedoor.domain.model.Fact
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.plan.PlanEvaluator
import app.atthedoor.domain.plan.VerdictKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanEvaluatorTest {
    private val evaluator = PlanEvaluator()

    @Test
    fun `유일한 보호자가 운전자면 정문 하차는 제외하고 후문 주차를 추천하며 미확인 후보는 확인 필요로 남긴다`() {
        val result = evaluator.evaluate(venue, soloGuardian, noon)

        assertEquals(VerdictKind.EXCLUDED, result.verdictOf("A")?.kind)
        assertEquals("B", result.recommended?.candidate?.id)
        assertEquals(VerdictKind.NEEDS_CHECK, result.verdictOf("C")?.kind)
    }

    @Test
    fun `수용 - 혼자 동행과 다른 보호자 동행을 바꾸면 실행 가능한 후보 집합이 달라진다`() {
        fun feasible(r: app.atthedoor.domain.plan.Evaluation) =
            r.results.filter { it.verdict.kind in setOf(VerdictKind.RECOMMENDED, VerdictKind.ELIGIBLE) }
                .map { it.candidate.id }.toSet()

        val solo = feasible(evaluator.evaluate(venue, soloGuardian, noon))
        val withOther = feasible(evaluator.evaluate(venue, withOtherGuardian, noon))

        assertEquals(setOf("B"), solo)
        assertEquals(setOf("A", "B"), withOther)
        assertNotEquals(solo, withOther)
    }

    @Test
    fun `수용 - 계단 없음이 필수이면 계단 경로나 미확인 경로가 추천되지 않는다`() {
        val withStairs = venue.copy(
            candidates = venue.candidates.map {
                if (it.id == "B") it.copy(walk = it.walk.copy(stairs = Fact(true, it.walk.stairs.evidence))) else it
            },
        )
        val result = evaluator.evaluate(withStairs, soloGuardian, noon)

        assertEquals(VerdictKind.EXCLUDED, result.verdictOf("B")?.kind)
        assertEquals(VerdictKind.NEEDS_CHECK, result.verdictOf("C")?.kind)
        assertNull(result.recommended)
    }

    @Test
    fun `하차 후 운전자가 주차할 곳이 없으면 다른 보호자가 있어도 확인 필요다`() {
        val noDriverParking = venue.copy(
            candidates = venue.candidates.map { if (it.id == "A") it.copy(driverParkingId = null) else it },
        )
        val result = evaluator.evaluate(noDriverParking, withOtherGuardian, noon)
        assertEquals(VerdictKind.NEEDS_CHECK, result.verdictOf("A")?.kind)
    }

    @Test
    fun `이용 불가로 알린 후보는 제외하고 같은 조건의 대안을 찾는다`() {
        val result = evaluator.evaluate(venue, withOtherGuardian, noon, unavailableIds = setOf("B"))
        assertEquals(VerdictKind.EXCLUDED, result.verdictOf("B")?.kind)
        assertEquals("A", result.recommended?.candidate?.id)
    }

    @Test
    fun `장애인 전용 구역은 자격을 확인하지 않으면 제외한다`() {
        val disabledOnly = venue.copy(
            candidates = venue.candidates.map { if (it.id == "B") it.copy(eligibility = Eligibility.DISABLED_ONLY) else it },
        )
        assertEquals(VerdictKind.EXCLUDED, evaluator.evaluate(disabledOnly, soloGuardian, noon).verdictOf("B")?.kind)
        val permitted = soloGuardian.copy(hasDisabledParkingPermit = true)
        assertEquals(VerdictKind.RECOMMENDED, evaluator.evaluate(disabledOnly, permitted, noon).verdictOf("B")?.kind)
    }

    @Test
    fun `운영 시간 밖이면 제외한다`() {
        val result = evaluator.evaluate(venue, soloGuardian, java.time.LocalTime.of(23, 0))
        assertTrue(result.results.none { it.verdict.kind == VerdictKind.RECOMMENDED })
    }

    @Test
    fun `휠체어는 통행 폭 정보가 없으므로 추천 완료로 나오지 않는다`() {
        val wheelchair = soloGuardian.copy(mobilityAid = MobilityAid.FOLDING_WHEELCHAIR)
        val result = evaluator.evaluate(venue, wheelchair, noon)
        assertNull(result.recommended)
        assertEquals(VerdictKind.NEEDS_CHECK, result.verdictOf("B")?.kind)
    }

    @Test
    fun `같은 입력에는 같은 결과와 같은 이유가 나온다`() {
        assertEquals(evaluator.evaluate(venue, soloGuardian, noon), evaluator.evaluate(venue, soloGuardian, noon))
    }
}
