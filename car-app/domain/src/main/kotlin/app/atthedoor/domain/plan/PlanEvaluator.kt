package app.atthedoor.domain.plan

import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Eligibility
import app.atthedoor.domain.model.Evidence
import app.atthedoor.domain.model.Josa
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.model.VerificationStatus
import app.atthedoor.domain.model.isWheelchair
import java.time.LocalTime

enum class VerdictKind { RECOMMENDED, ELIGIBLE, NEEDS_CHECK, EXCLUDED }

/**
 * [reasons]는 판정의 이유(추천이면 장점, 제외·확인 필요면 걸린 조건),
 * [cautions]는 판정과 별개로 현장에서 확인해야 할 사항이다.
 */
data class Verdict(
    val kind: VerdictKind,
    val reasons: List<String>,
    val cautions: List<String> = emptyList(),
    /** 이 후보를 막은 이유가 사용자가 바꿀 수 있는 조건 하나뿐일 때, 그 조건. 화면은 ‘이렇게 바꾸면 후보가 돼요’로 보여 준다. */
    val hint: ConditionHint? = null,
)

enum class ConditionHint(val label: String) {
    ADD_OTHER_GUARDIAN("‘다른 보호자도 있어요’면 후보가 돼요"),
    CONFIRM_PERMIT("‘장애인 주차 표지 있음’이면 후보가 돼요"),
}

data class CandidateResult(
    val candidate: Candidate,
    val verdict: Verdict,
    /** 이 후보의 핵심 사실(보행 동선 계단 여부)을 뒷받침하는 근거. 화면에 출처와 확인 날짜를 보여 준다. */
    val evidence: Evidence?,
) {
    val selectable: Boolean get() = verdict.kind == VerdictKind.RECOMMENDED || verdict.kind == VerdictKind.ELIGIBLE
}

data class Evaluation(val results: List<CandidateResult>) {
    val recommended: CandidateResult? get() = results.firstOrNull { it.verdict.kind == VerdictKind.RECOMMENDED }
    val selectable: List<CandidateResult> get() = results.filter { it.selectable }
    fun of(kind: VerdictKind): List<CandidateResult> = results.filter { it.verdict.kind == kind }
    fun verdictOf(candidateId: String): Verdict? = results.firstOrNull { it.candidate.id == candidateId }?.verdict
}

/** 이번 여행에서 사용자가 이용할 수 없다고 알린 후보와 입구. 전체 사용자의 확정 정보로 승격하지 않는다. */
data class Unavailable(
    val candidateIds: Set<String> = emptySet(),
    val entranceIds: Set<String> = emptySet(),
)

/**
 * 도착 계획 후보를 평가하는 명시적 규칙 엔진.
 * 같은 입력에는 항상 같은 결과와 같은 이유 문구를 돌려준다. 학습 모델을 쓰지 않는다.
 */
class PlanEvaluator {

    fun evaluate(
        venue: Venue,
        conditions: TravelConditions,
        at: LocalTime,
        unavailable: Unavailable = Unavailable(),
    ): Evaluation {
        // 만차 같은 이용 불가 제보는 차를 세우는 구역(lotId) 전체에 적용한다. 같은 주차장에서 다른 입구로 가는 후보도 함께 빠진다.
        val blockedLots = venue.candidates.filter { it.id in unavailable.candidateIds }.map { it.lotId }.toSet()
        val checked = venue.candidates.map { candidate ->
            candidate to checkRequired(venue, candidate, conditions, at, unavailable, blockedLots)
        }

        val passing = checked
            .filter { (_, verdict) -> verdict == null }
            .map { it.first }
            .sortedWith(rankComparator(conditions))

        val results = buildList {
            passing.forEachIndexed { index, candidate ->
                val kind = if (index == 0) VerdictKind.RECOMMENDED else VerdictKind.ELIGIBLE
                add(result(candidate, Verdict(kind, positiveReasons(venue, candidate, conditions), cautions(candidate))))
            }
            checked.filter { it.second?.kind == VerdictKind.NEEDS_CHECK }
                .forEach { (c, v) -> add(result(c, v!!)) }
            checked.filter { it.second?.kind == VerdictKind.EXCLUDED }
                .forEach { (c, v) -> add(result(c, v!!)) }
        }
        return Evaluation(results)
    }

    private fun result(c: Candidate, v: Verdict) = CandidateResult(c, v, c.walk.stairs.evidence ?: c.walk.distanceM.evidence)

    /** 필수 조건 검사. 통과하면 null, 아니면 EXCLUDED 또는 NEEDS_CHECK. 제외가 확인 필요보다 우선한다. */
    private fun checkRequired(
        venue: Venue,
        c: Candidate,
        cond: TravelConditions,
        at: LocalTime,
        unavailable: Unavailable,
        blockedLots: Set<String>,
    ): Verdict? {
        val excluded = mutableListOf<String>()
        val needsCheck = mutableListOf<String>()
        val hints = mutableListOf<ConditionHint>()

        if (c.lotId in blockedLots) excluded += "이번 여행에서 이 구역을 이용할 수 없다고 알려 주셨어요"

        if (c.type == CandidateType.DROPOFF && cond.companionRequired) {
            if (!cond.otherGuardian && c.leavesPassengerAlone) {
                excluded += "운전자가 주차하는 동안 함께할 다른 보호자가 없어요"
                hints += ConditionHint.ADD_OTHER_GUARDIAN
            } else if (c.driverParkingId == null || venue.candidate(c.driverParkingId) == null) {
                needsCheck += "하차 후 운전자가 주차할 곳이 확인되지 않았어요"
            } else if (venue.candidate(c.driverParkingId)?.lotId in blockedLots) {
                excluded += "하차 후 운전자가 주차할 구역을 이용할 수 없다고 알려 주셨어요"
            }
        }

        if (c.eligibility == Eligibility.DISABLED_ONLY && !cond.hasDisabledParkingPermit) {
            excluded += "장애인 전용 구역이에요. 이용 자격을 확인한 경우에만 안내해요"
            hints += ConditionHint.CONFIRM_PERMIT
        }

        c.hours?.let { if (!it.isOpenAt(at)) excluded += "도착 시각에 운영하지 않아요 (운영 ${it.label})" }

        val entrance = venue.entrance(c.walk.toEntranceId)
        if (entrance == null) {
            needsCheck += "연결된 건물 입구 정보가 없어요"
        } else {
            if (entrance.id in unavailable.entranceIds) excluded += "${Josa.eulReul(entrance.name)} 이용할 수 없다고 알려 주셨어요"
            entrance.hours?.let { if (!it.isOpenAt(at)) excluded += "도착 시각에 ${Josa.iGa(entrance.name)} 닫혀 있어요 (출입 ${it.label})" }
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

        if (cond.mobilityAid.isWheelchair) {
            val w = c.walk.wheelchair
            when {
                w.known && w.value == false -> excluded += "휠체어로 지나가기 어려운 구간이 있어요"
                !w.known -> needsCheck += "휠체어 통행 폭과 경사가 확인되지 않았어요"
            }
        }

        return when {
            excluded.isNotEmpty() -> Verdict(VerdictKind.EXCLUDED, excluded, hint = hints.singleOrNull()?.takeIf { excluded.size == 1 && needsCheck.isEmpty() })
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

    private fun positiveReasons(venue: Venue, c: Candidate, cond: TravelConditions): List<String> = buildList {
        val entranceName = venue.entrance(c.walk.toEntranceId)?.name ?: "입구"
        when {
            c.type == CandidateType.PARKING && cond.companionRequired -> add("함께 주차한 뒤 ${entranceName}까지 같이 걸어가요")
            c.type == CandidateType.PARKING -> add("주차한 뒤 ${Josa.euroRo(entranceName)} 걸어가요")
            cond.companionRequired && cond.otherGuardian -> {
                val parking = c.driverParkingId?.let(venue::candidate)?.name
                add("다른 보호자와 먼저 내리고, 운전자는 ${parking ?: "지정 구역"}에 주차해요")
            }
            else -> add("${entranceName} 바로 앞에서 내려요")
        }
        if (c.walk.stairs.known && c.walk.stairs.value == false) add("확인된 보행 동선에 계단이 없어요")
        if (cond.mobilityAid.isWheelchair && c.walk.wheelchair.known && c.walk.wheelchair.value == true) add("휠체어 통행 폭과 경사를 확인한 동선이에요")
        if (c.walk.distanceM.known) add("${entranceName}까지 약 ${c.walk.distanceM.value}m")
    }

    private fun cautions(c: Candidate): List<String> = buildList {
        if (c.liveAvailabilitySource == null && c.type == CandidateType.PARKING) add("빈자리는 현장에서 확인해야 해요")
        if (!c.walk.distanceM.known) add("보행 거리는 확인된 값이 없어요")
    }
}
