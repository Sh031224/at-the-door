package app.atthedoor.domain.plan

import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Eligibility
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.model.VerificationStatus
import java.time.LocalTime

enum class VerdictKind { RECOMMENDED, ELIGIBLE, NEEDS_CHECK, EXCLUDED }

data class Verdict(val kind: VerdictKind, val reasons: List<String>)

data class CandidateResult(val candidate: Candidate, val verdict: Verdict)

data class Evaluation(val results: List<CandidateResult>) {
    val recommended: CandidateResult? get() = results.firstOrNull { it.verdict.kind == VerdictKind.RECOMMENDED }
    fun of(kind: VerdictKind): List<CandidateResult> = results.filter { it.verdict.kind == kind }
    fun verdictOf(candidateId: String): Verdict? = results.firstOrNull { it.candidate.id == candidateId }?.verdict
}

/**
 * 도착 계획 후보를 평가하는 명시적 규칙 엔진.
 * 같은 입력에는 항상 같은 결과와 같은 이유 문구를 돌려준다. 학습 모델을 쓰지 않는다.
 */
class PlanEvaluator {

    fun evaluate(
        venue: Venue,
        conditions: TravelConditions,
        at: LocalTime,
        unavailableIds: Set<String> = emptySet(),
    ): Evaluation {
        val checked = venue.candidates.map { candidate ->
            candidate to checkRequired(venue, candidate, conditions, at, unavailableIds)
        }

        val passing = checked
            .filter { (_, verdict) -> verdict == null }
            .map { it.first }
            .sortedWith(rankComparator(conditions))

        val results = buildList {
            passing.forEachIndexed { index, candidate ->
                val kind = if (index == 0) VerdictKind.RECOMMENDED else VerdictKind.ELIGIBLE
                add(CandidateResult(candidate, Verdict(kind, positiveReasons(venue, candidate))))
            }
            checked.filter { it.second?.kind == VerdictKind.NEEDS_CHECK }
                .forEach { (c, v) -> add(CandidateResult(c, v!!)) }
            checked.filter { it.second?.kind == VerdictKind.EXCLUDED }
                .forEach { (c, v) -> add(CandidateResult(c, v!!)) }
        }
        return Evaluation(results)
    }

    /** 필수 조건 검사. 통과하면 null, 아니면 EXCLUDED 또는 NEEDS_CHECK. 제외가 확인 필요보다 우선한다. */
    private fun checkRequired(
        venue: Venue,
        c: Candidate,
        cond: TravelConditions,
        at: LocalTime,
        unavailableIds: Set<String>,
    ): Verdict? {
        val excluded = mutableListOf<String>()
        val needsCheck = mutableListOf<String>()

        if (c.id in unavailableIds) excluded += "이번 여행에서 이용할 수 없다고 알려 주셨어요"

        if (c.type == CandidateType.DROPOFF && cond.companionRequired) {
            if (!cond.otherGuardian && c.leavesPassengerAlone) {
                excluded += "운전자가 주차하는 동안 함께할 다른 보호자가 없어요"
            } else if (c.driverParkingId == null || venue.candidate(c.driverParkingId) == null) {
                needsCheck += "하차 후 운전자가 주차할 곳이 확인되지 않았어요"
            }
        }

        if (c.eligibility == Eligibility.DISABLED_ONLY && !cond.hasDisabledParkingPermit) {
            excluded += "장애인 전용 구역은 이용 자격을 확인한 경우에만 안내해요"
        }

        c.hours?.let { if (!it.isOpenAt(at)) excluded += "지금은 운영 시간이 아니에요" }

        val entrance = venue.entrance(c.walk.toEntranceId)
        if (entrance == null) {
            needsCheck += "연결된 건물 입구 정보가 없어요"
        } else {
            entrance.hours?.let { if (!it.isOpenAt(at)) excluded += "${entrance.name}은 지금 출입 시간이 아니에요" }
        }

        if (cond.effectiveStepFreeRequired) {
            when {
                c.walk.stairs.known && c.walk.stairs.value == true -> excluded += "보행 동선에 계단이 있어요"
                !c.walk.stairs.known -> needsCheck += "보행 동선의 계단 여부가 확인되지 않았어요"
            }
            if (entrance != null) {
                when {
                    entrance.stepFree.known && entrance.stepFree.value == false -> excluded += "${entrance.name}에 계단이나 턱이 있어요"
                    !entrance.stepFree.known -> needsCheck += "${entrance.name}의 계단·턱 여부가 확인되지 않았어요"
                }
            }
        }

        if (cond.mobilityAid == MobilityAid.FOLDING_WHEELCHAIR || cond.mobilityAid == MobilityAid.POWER_WHEELCHAIR) {
            // 통행 폭·경사 데이터가 아직 스키마에 없으므로 휠체어는 항상 추가 확인 대상으로 둔다.
            needsCheck += "휠체어 통행 폭과 경사 정보는 아직 확인되지 않았어요"
        }

        return when {
            excluded.isNotEmpty() -> Verdict(VerdictKind.EXCLUDED, excluded)
            needsCheck.isNotEmpty() -> Verdict(VerdictKind.NEEDS_CHECK, needsCheck)
            else -> null
        }
    }

    /** 근거 있는 보행 거리 → 경로 단계 수 → 근거 신뢰도 → ID 순. 거리를 모르는 후보는 뒤로 보낸다. */
    private fun rankComparator(cond: TravelConditions): Comparator<Candidate> {
        val distance = compareBy<Candidate> { c ->
            if (c.walk.distanceM.known) c.walk.distanceM.value!! else Int.MAX_VALUE
        }
        val steps = compareBy<Candidate> { it.walk.steps.size }
        val trust = compareBy<Candidate> { c ->
            when (c.walk.stairs.evidence?.status) {
                VerificationStatus.VERIFIED -> 0
                VerificationStatus.DEMO -> 1
                else -> 2
            }
        }
        val base = if (cond.preferShortWalk) distance.then(steps) else steps.then(distance)
        return base.then(trust).thenBy { it.id }
    }

    private fun positiveReasons(venue: Venue, c: Candidate): List<String> = buildList {
        if (c.type == CandidateType.PARKING) add("함께 주차한 뒤 입구까지 같이 걸어갈 수 있어요")
        if (c.walk.stairs.known && c.walk.stairs.value == false) add("확인된 보행 동선에 계단이 없어요")
        if (c.walk.distanceM.known) add("입구까지 약 ${c.walk.distanceM.value}m")
        if (c.liveAvailabilitySource == null && c.type == CandidateType.PARKING) {
            add("주차 여유 공간은 현장에서 확인해야 해요")
        }
        venue.entrance(c.walk.toEntranceId)?.let { add("${it.name}으로 들어가요") }
    }
}
