package app.atthedoor.domain

import app.atthedoor.domain.Fixtures.noon
import app.atthedoor.domain.Fixtures.soloGuardian
import app.atthedoor.domain.Fixtures.venue
import app.atthedoor.domain.Fixtures.withOtherGuardian
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.plan.ConditionParser
import app.atthedoor.domain.plan.Replanner
import app.atthedoor.domain.port.GearState
import app.atthedoor.domain.trip.HandoffWatcher
import app.atthedoor.domain.trip.HandoffCue
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase
import app.atthedoor.domain.trip.VehicleSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplanAndSignalsTest {
    private var seq = 0
    private fun Trip.on(e: TripEvent) = TripMachine.reduce(this, e, "e${seq++}")
    private val replanner = Replanner()

    private fun walking(conditions: TravelConditions = soloGuardian) = Trip.start("t", venue.id)
        .on(TripEvent.ConditionsConfirmed(conditions))
        .on(TripEvent.PlanSelected("B"))
        .on(TripEvent.NaviStarted)
        .on(TripEvent.ParkingConfirmed(drivingRestricted = false))
        .on(TripEvent.WalkStarted)

    private fun Trip.replan(): Trip = on(replanner.replan(venue, this, noon).event)

    @Test
    fun `수용 - 걷는 중 후문이 막히면 차를 옮기지 않고 같은 주차 구역에서 정문 경사로로 안내한다`() {
        val t = walking().on(TripEvent.EntranceBlocked("E-back")).replan()
        assertEquals(TripPhase.WALKING, t.phase)
        assertEquals("B2", t.selectedCandidateId)
        assertEquals(soloGuardian, t.conditions)
        assertEquals(TripMachine.NOTICE_ALTERNATIVE, t.notice)
    }

    @Test
    fun `걷는 중 같은 구역의 대안이 없으면 차를 옮겨야 한다고 알리고 후보 선택으로 간다`() {
        val t = walking(withOtherGuardian)
            .on(TripEvent.EntranceBlocked("E-back"))
            .on(TripEvent.EntranceBlocked("E-back")) // 중복 제보는 무시된다(이미 REPLANNING)
        val noB2 = venue.copy(candidates = venue.candidates.filter { it.id != "B2" && it.id != "E" })
        val next = t.on(replanner.replan(noB2, t, noon).event)
        assertEquals(TripPhase.CHOOSING, next.phase)
        assertEquals("A", next.selectedCandidateId)
        assertEquals(TripMachine.NOTICE_NEEDS_CAR_MOVE, next.notice)
    }

    @Test
    fun `수용 - 대안이 없으면 확인 필요로 멈춘다`() {
        val t = walking()
            .on(TripEvent.EntranceBlocked("E-back")).replan()
            .on(TripEvent.EntranceBlocked("E-front")).replan()
        // 남은 후보는 모두 막힌 입구로 가거나(E) 정보가 없다(C). 없는 길을 만들지 않는다.
        assertEquals(TripPhase.NEEDS_CHECK, t.phase)
    }

    @Test
    fun `차량 접근 중 만차를 알리면 같은 조건의 다른 구역으로 내비를 다시 요청한다`() {
        val approaching = Trip.start("t", venue.id)
            .on(TripEvent.ConditionsConfirmed(soloGuardian))
            .on(TripEvent.PlanSelected("B"))
            .on(TripEvent.NaviStarted)
        val t = approaching.on(TripEvent.CandidateUnavailable("B")).replan()
        // 만차는 후문 주차장 전체에 적용되므로 같은 주차장의 B2가 아니라 공영주차장 E로 간다.
        assertEquals(TripPhase.NAVI_REQUESTED, t.phase)
        assertEquals("E", t.selectedCandidateId)
    }

    @Test
    fun `앱을 다시 열면 끊긴 내비 요청은 후보 선택으로 돌아가고 선택은 유지한다`() {
        val t = Trip.start("t", venue.id)
            .on(TripEvent.ConditionsConfirmed(soloGuardian))
            .on(TripEvent.PlanSelected("B"))
            .on(TripEvent.Resumed)
        assertEquals(TripPhase.CHOOSING, t.phase)
        assertEquals("B", t.selectedCandidateId)
        assertEquals(TripMachine.NOTICE_RESUMED_NAVI, t.notice)
    }

    @Test
    fun `앱을 다시 열면 지난 실행의 알림은 지운다`() {
        val failed = Trip.start("t", venue.id)
            .on(TripEvent.ConditionsConfirmed(soloGuardian))
            .on(TripEvent.PlanSelected("B"))
            .on(TripEvent.NaviFailed("내비게이션 응답이 없어요"))
        val resumed = failed.on(TripEvent.Resumed)
        assertEquals(TripPhase.CHOOSING, resumed.phase)
        assertNull(resumed.notice)
        assertEquals("B", resumed.selectedCandidateId)
    }

    @Test
    fun `수용 - 차량 신호는 주행 뒤의 변화로만 주차 확인을 권하고 확정하지 않는다`() {
        val w = HandoffWatcher()
        val parkedUnbuckled = VehicleSignals(gear = GearState.PARK, passengerBuckled = false, passengerDoorOpen = false)
        // 출발 전부터 P·벨트 해제 상태: 주행한 적이 없으므로 권하지 않는다.
        assertNull(w.observe(TripPhase.APPROACHING, drivingRestricted = false, parkedUnbuckled))
        // 주행(제한 중)에는 권하지 않는다.
        val driving = VehicleSignals(gear = GearState.DRIVE, passengerBuckled = true, passengerDoorOpen = false)
        assertNull(w.observe(TripPhase.APPROACHING, drivingRestricted = true, driving))
        // 정차 후 P로 바꾸면 권한다.
        val parked = driving.copy(gear = GearState.PARK)
        assertEquals(HandoffCue.PARKED_GEAR, w.observe(TripPhase.APPROACHING, false, parked))
        // 벨트가 풀리고, 문이 열리면 다음 단계로 바뀐다.
        assertEquals(HandoffCue.BELT_RELEASED, w.observe(TripPhase.APPROACHING, false, parked.copy(passengerBuckled = false)))
        assertEquals(HandoffCue.DOOR_OPENED, w.observe(TripPhase.APPROACHING, false, parked.copy(passengerBuckled = false, passengerDoorOpen = true)))
        // 접근 단계가 아니면 권유를 지운다. 여행 단계는 사람이 누르기 전까지 바뀌지 않는다: 신호는 TripEvent가 아니다.
        assertNull(w.observe(TripPhase.PARKED, false, parked))
    }

    @Test
    fun `기어 P가 주행 제한 해제보다 먼저 와도 주차 확인을 권한다`() {
        val w = HandoffWatcher()
        val parked = VehicleSignals(gear = GearState.PARK, passengerBuckled = true, passengerDoorOpen = false)
        w.observe(TripPhase.APPROACHING, drivingRestricted = true, parked.copy(gear = GearState.DRIVE))
        assertNull(w.observe(TripPhase.APPROACHING, drivingRestricted = true, parked))
        assertEquals(HandoffCue.PARKED_GEAR, w.observe(TripPhase.APPROACHING, drivingRestricted = false, parked))
    }

    @Test
    fun `제외 이유가 바꿀 수 있는 조건 하나뿐이면 힌트를 준다`() {
        val r = app.atthedoor.domain.plan.PlanEvaluator().evaluate(venue, soloGuardian, noon)
        assertEquals(app.atthedoor.domain.plan.ConditionHint.ADD_OTHER_GUARDIAN, r.verdictOf("A")?.hint)
        assertNull(r.verdictOf("C")?.hint)
    }

    @Test
    fun `할머니 자리 발화에서 목적지와 조건을 뽑는다`() {
        val p = ConditionParser.parse(
            "오늘 새봄문화회관 소공연장 가. 엄마랑 둘이고, 계단 없이 가까이 걸으면 좋겠어.",
            TravelConditions(companionRequired = false, otherGuardian = true, stepFreeRequired = false),
            listOf(venue.copy(aliases = listOf("새봄문화회관"))),
        )
        assertEquals("saebom-hall", p.venueId)
        assertTrue(p.conditions.stepFreeRequired)
        assertTrue(p.conditions.preferShortWalk)
        assertTrue(p.conditions.companionRequired)
        assertFalse(p.conditions.otherGuardian)
    }

    @Test
    fun `알아듣지 못한 발화는 기존 조건을 바꾸지 않는다`() {
        val p = ConditionParser.parse("음 그러니까", soloGuardian)
        assertEquals(soloGuardian, p.conditions)
        assertFalse(p.understoodAnything)
    }

    @Test
    fun `사용자가 말한 보조기구만 반영하고 다른 보호자 언급을 알아듣는다`() {
        val p = ConditionParser.parse("누나도 같이 내려. 접이식 휠체어 가져가", soloGuardian)
        assertEquals(MobilityAid.FOLDING_WHEELCHAIR, p.conditions.mobilityAid)
        assertTrue(p.conditions.otherGuardian)
    }

    @Test
    fun `차량 화면의 조건 이름에는 보조기구가 보이지만 휴대폰 계약에는 조건 필드가 없다`() {
        val c = soloGuardian.copy(mobilityAid = MobilityAid.POWER_WHEELCHAIR)
        assertTrue(c.labels.any { "휠체어" in it })
        val fields = app.atthedoor.domain.port.TripSnapshot::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { "condition" in it.lowercase() || "aid" in it.lowercase() })
    }
}
