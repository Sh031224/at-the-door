package app.atthedoor.domain.plan

import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue

/**
 * SpeechToText가 돌려준 발화 텍스트에서 이동 조건을 뽑는 키워드 규칙.
 *
 * - 알아들은 항목만 바꾸고, 언급하지 않은 항목은 기존 값을 그대로 둔다.
 * - ‘오래 못 걷는다’에서 보행 한계를 몇 미터로 정하지 않는다. ‘보행 거리 짧게’ 선호로만 바꾼다.
 * - 질병·장애를 추정하지 않는다. 휠체어·유아차처럼 사용자가 직접 말한 보조기구만 반영한다.
 * - 결과는 항상 화면에서 사용자가 확인한다.
 */
object ConditionParser {

    data class Parsed(
        val conditions: TravelConditions,
        val venueId: String?,
        /** 이번 발화에서 알아들은 항목. 화면에 ‘이렇게 알아들었어요’로 보여 준다. */
        val recognized: List<String>,
    ) {
        val understoodAnything: Boolean get() = recognized.isNotEmpty()
    }

    fun parse(text: String, base: TravelConditions, venues: List<Venue> = emptyList()): Parsed {
        val t = text.replace(" ", "")
        var c = base
        val recognized = mutableListOf<String>()

        val venue = venues.firstOrNull { v -> (v.aliases + v.name).any { it.replace(" ", "") in t } }
        venue?.let { recognized += "목적지: ${it.name}" }

        if ("계단" in t && listOf("없이", "없는", "말고", "피해", "못", "힘들", "어려").any { it in t }) {
            c = c.copy(stepFreeRequired = true); recognized += "계단 없는 동선"
        }
        if (listOf("가까이", "가까운", "조금만걷", "짧게", "오래못걷", "많이못걷", "멀리못걷", "천천히").any { it in t }) {
            c = c.copy(preferShortWalk = true); recognized += "보행 거리 짧게"
        }

        val others = listOf("셋이", "넷이", "누나도", "형도", "동생도", "언니도", "오빠도", "아내도", "남편도", "같이내려", "다른보호자")
        val solo = listOf("둘이", "단둘", "나혼자", "나밖에", "나만")
        when {
            others.any { it in t } -> {
                c = c.copy(companionRequired = true, otherGuardian = true); recognized += "다른 보호자와 함께"
            }
            solo.any { it in t } -> {
                c = c.copy(companionRequired = true, otherGuardian = false); recognized += "운전자와 입구까지 함께"
            }
            listOf("같이가", "함께가", "같이걸", "내가같이", "모시고").any { it in t } -> {
                c = c.copy(companionRequired = true); recognized += "입구까지 동행"
            }
        }

        val aid = when {
            "전동휠체어" in t -> MobilityAid.POWER_WHEELCHAIR
            "휠체어" in t -> MobilityAid.FOLDING_WHEELCHAIR
            "유모차" in t || "유아차" in t -> MobilityAid.STROLLER
            "보행기" in t || "보행보조" in t -> MobilityAid.WALKER
            else -> null
        }
        aid?.let { c = c.copy(mobilityAid = it); recognized += "보조기구: ${it.label}" }

        return Parsed(c, venue?.id, recognized)
    }
}
