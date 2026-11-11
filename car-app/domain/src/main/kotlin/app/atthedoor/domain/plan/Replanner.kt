package app.atthedoor.domain.plan

import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripPhase
import java.time.LocalTime

/**
 * 재계획 단계(REPLANNING)의 여행을 같은 조건으로 다시 평가하고 다음 이벤트를 정한다.
 *
 * - 차 밖에서 걷는 중이면, 차를 옮기지 않고 갈 수 있는 대안(같은 lotId)을 먼저 고른다.
 * - 그런 대안이 없으면 추천 1위를 고르되, 상태 머신이 ‘차를 옮겨야 한다’로 처리한다.
 * - 대안이 없으면 NoAlternative. 없는 정보를 만들어 내지 않는다.
 */
class Replanner(private val evaluator: PlanEvaluator = PlanEvaluator()) {

    data class Outcome(val evaluation: Evaluation, val event: TripEvent)

    fun replan(venue: Venue, trip: Trip, at: LocalTime): Outcome {
        require(trip.phase == TripPhase.REPLANNING) { "replan only in REPLANNING, was ${trip.phase}" }
        val conditions = requireNotNull(trip.conditions) { "conditions required" }
        val evaluation = evaluator.evaluate(venue, conditions, at, trip.unavailable)

        val currentLot = trip.selectedCandidateId?.let(venue::candidate)?.lotId
        val onFoot = trip.resumePhase?.onFoot == true
        val walkable = if (onFoot && currentLot != null) evaluation.selectable.firstOrNull { it.candidate.lotId == currentLot } else null
        val pick = walkable ?: evaluation.recommended

        val event = pick?.let { TripEvent.AlternativeChosen(it.candidate.id, sameLot = it.candidate.lotId == currentLot) }
            ?: TripEvent.NoAlternative
        return Outcome(evaluation, event)
    }
}
