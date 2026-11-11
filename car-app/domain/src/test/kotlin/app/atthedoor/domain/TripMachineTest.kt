package app.atthedoor.domain

import app.atthedoor.domain.Fixtures.soloGuardian
import app.atthedoor.domain.Fixtures.withOtherGuardian
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class TripMachineTest {
    private var seq = 0
    private fun Trip.on(e: TripEvent, id: String = "e${seq++}") = TripMachine.reduce(this, e, id)

    private fun approaching(): Trip = Trip.start("t1", "saebom-hall")
        .on(TripEvent.ConditionsConfirmed(soloGuardian))
        .on(TripEvent.PlanSelected("B"))
        .on(TripEvent.NaviStarted)

    @Test
    fun `정상 흐름은 계획에서 완료까지 이어진다`() {
        val done = approaching()
            .on(TripEvent.ParkingConfirmed(drivingRestricted = false, note = "B2-14"))
            .on(TripEvent.WalkStarted)
            .on(TripEvent.ArrivedAtEntrance)
            .on(TripEvent.ReturnStarted)
            .on(TripEvent.BackInCar)
        assertEquals(TripPhase.COMPLETED, done.phase)
        assertEquals("B2-14", done.parkingNote)
    }

    @Test
    fun `수용 - 내비 요청 실패 시 안내 중으로 표시되지 않고 기존 선택을 유지한다`() {
        val failed = Trip.start("t1", "v")
            .on(TripEvent.ConditionsConfirmed(soloGuardian))
            .on(TripEvent.PlanSelected("B"))
            .on(TripEvent.NaviFailed("경로를 찾지 못했어요"))
        assertEquals(TripPhase.CHOOSING, failed.phase)
        assertEquals("B", failed.selectedCandidateId)
        assertEquals("경로를 찾지 못했어요", failed.notice)
    }

    @Test
    fun `차량 접근 중 운전자가 내비 안내를 취소하면 선택을 유지한 채 후보 선택으로 돌아간다`() {
        val t = approaching().on(TripEvent.NaviInterrupted("내비 안내가 취소됐어요"))
        assertEquals(TripPhase.CHOOSING, t.phase)
        assertEquals("B", t.selectedCandidateId)
    }

    @Test
    fun `계획과 다른 후보 지점에 세웠다고 알리면 그 지점의 동선으로 이어 간다`() {
        val t = approaching().on(TripEvent.ParkingConfirmed(drivingRestricted = false, candidateId = "C"))
        assertEquals(TripPhase.PARKED, t.phase)
        assertEquals("C", t.selectedCandidateId)
    }

    @Test
    fun `주행 제한 중에는 주차 확인을 받지 않는다`() {
        val t = approaching().on(TripEvent.ParkingConfirmed(drivingRestricted = true))
        assertEquals(TripPhase.APPROACHING, t.phase)
    }

    @Test
    fun `수용 - 사람이 확인하기 전에는 도착으로 진행하지 않는다`() {
        val parked = approaching().on(TripEvent.ParkingConfirmed(drivingRestricted = false))
        // 보행 시작 전 도착 확인은 무시된다. 도어·근접 신호는 TripEvent로 존재하지 않는다.
        assertEquals(TripPhase.PARKED, parked.on(TripEvent.ArrivedAtEntrance).phase)
    }

    @Test
    fun `수용 - 앱 재실행이나 중복 이벤트에도 상태가 잘못 진행되지 않는다`() {
        val walking = approaching()
            .on(TripEvent.ParkingConfirmed(drivingRestricted = false))
            .on(TripEvent.WalkStarted)
        val arrived = walking.on(TripEvent.ArrivedAtEntrance, id = "arrive-1")
        val again = arrived.on(TripEvent.ArrivedAtEntrance, id = "arrive-1")
        assertEquals(arrived, again)
        assertEquals(TripPhase.VISITING, again.phase)
    }

    @Test
    fun `수용 - 입구 폐쇄를 알리면 동행 조건을 유지한 채 재계획하고 보행 안내로 돌아간다`() {
        val walking = approaching()
            .on(TripEvent.ParkingConfirmed(drivingRestricted = false))
            .on(TripEvent.WalkStarted)
        val replanning = walking.on(TripEvent.EntranceBlocked("E-back"))
        assertEquals(TripPhase.REPLANNING, replanning.phase)
        assertEquals(soloGuardian, replanning.conditions)
        assertEquals(setOf("E-back"), replanning.blockedEntranceIds)

        val resumed = replanning.on(TripEvent.AlternativeChosen("B2", sameLot = true))
        assertEquals(TripPhase.WALKING, resumed.phase)
        assertEquals("B2", resumed.selectedCandidateId)
    }

    @Test
    fun `수용 - 적합한 대안이 없으면 확인 필요로 멈춘다`() {
        val t = approaching().on(TripEvent.CandidateUnavailable("B")).on(TripEvent.NoAlternative)
        assertEquals(TripPhase.NEEDS_CHECK, t.phase)
    }

    @Test
    fun `입구 도착을 잘못 눌렀으면 되돌려 보행 안내로 돌아간다`() {
        val visiting = approaching()
            .on(TripEvent.ParkingConfirmed(drivingRestricted = false))
            .on(TripEvent.WalkStarted).on(TripEvent.ArrivedAtEntrance)
        assertEquals(TripPhase.WALKING, visiting.on(TripEvent.ArrivalUndone).phase)
    }

    @Test
    fun `같은 조건으로 바꾸면 재계획하지 않는다`() {
        val t = approaching()
        assertEquals(t, t.on(TripEvent.ConditionsChanged(soloGuardian)))
    }

    @Test
    fun `여행을 마친 뒤 제보는 한 번만 받는다`() {
        val done = approaching()
            .on(TripEvent.ParkingConfirmed(drivingRestricted = false))
            .on(TripEvent.WalkStarted).on(TripEvent.ArrivedAtEntrance).on(TripEvent.ReturnStarted).on(TripEvent.BackInCar)
        val once = done.on(TripEvent.FeedbackGiven(setOf(app.atthedoor.domain.trip.FeedbackKind.ROUTE)))
        assertEquals(setOf(app.atthedoor.domain.trip.FeedbackKind.ROUTE), once.feedback)
        assertEquals(once, once.on(TripEvent.FeedbackGiven(emptySet())))
    }

    @Test
    fun `보호자 구성이 바뀌면 전체를 재평가하고 내비를 다시 요청한다`() {
        val t = approaching().on(TripEvent.ConditionsChanged(withOtherGuardian))
        assertEquals(TripPhase.REPLANNING, t.phase)
        assertEquals(TripPhase.NAVI_REQUESTED, t.on(TripEvent.AlternativeChosen("A", sameLot = false)).phase)
    }
}
