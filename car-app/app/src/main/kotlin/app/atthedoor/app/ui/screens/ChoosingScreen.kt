package app.atthedoor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.EntranceImage
import app.atthedoor.app.ui.components.LabelValue
import app.atthedoor.app.ui.components.Pill
import app.atthedoor.app.ui.components.PlanDiagram
import app.atthedoor.app.ui.components.PrimaryButton
import app.atthedoor.app.ui.components.SecondaryButton
import app.atthedoor.app.ui.components.SectionCard
import app.atthedoor.app.ui.components.VerdictBadge
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.model.VerificationStatus
import app.atthedoor.domain.plan.CandidateResult
import app.atthedoor.domain.plan.ConditionHint
import app.atthedoor.domain.plan.VerdictKind

/** S03 도착 계획 비교. 추천·제외 이유, 근거와 확인 날짜, 미확인 항목을 함께 보여 준다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoosingScreen(state: TripUiState, vm: TripViewModel) {
    val venue = state.venue ?: return
    val evaluation = state.evaluation ?: return
    val conditions = state.trip?.conditions ?: state.draft
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("같은 목적지, 함께 가는 사람에 맞춘 도착 계획", style = MaterialTheme.typography.headlineLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.labels.forEach { Pill(it) }
            }
        }
        Spacer(Modifier.width(16.dp))
        SecondaryButton("조건 고치기", vm::editConditions, icon = Icons.Filled.Edit)
    }
    if (evaluation.selectable.isEmpty()) NoPlanCard(state, vm)
    // 실패·취소로 돌아왔을 때만 ‘다시 안내’로 부른다. 처음 비교할 때는 모두 ‘이 계획으로 안내’다.
    val retrying = state.trip?.notice != null
    evaluation.results.forEach { r ->
        CandidateCard(
            venue, r,
            primary = r.verdict.kind == VerdictKind.RECOMMENDED,
            retry = retrying && r.candidate.id == state.trip?.selectedCandidateId,
            onChoose = { vm.choosePlan(r.candidate) },
            onHint = vm::applyHint,
        )
    }
}

/** 조건을 만족하는 확인된 계획이 없을 때. 없는 길을 만들지 않고, 확인할 곳과 다음 행동을 준다. */
@Composable
private fun NoPlanCard(state: TripUiState, vm: TripViewModel) {
    val venue = state.venue ?: return
    SectionCard {
        Text("확인된 계획이 없어요", style = MaterialTheme.typography.headlineMedium)
        val toCheck = state.evaluation?.of(VerdictKind.NEEDS_CHECK).orEmpty().flatMap { it.verdict.reasons }.distinct()
        Text(
            if (toCheck.isNotEmpty()) "아래 정보를 시설에 확인하면 계획을 고를 수 있어요." else "지금 조건으로 갈 수 있는 확인된 길이 없어요. 조건이나 목적지를 바꿔 보세요.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        toCheck.forEach { ReasonLine(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "확인할 것: $it") }
        venue.contact?.let { LabelValue("시설 연락처", if (venue.demo) "$it (시연용 번호)" else it) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondaryButton("조건 고치기", vm::editConditions, icon = Icons.Filled.Edit)
            SecondaryButton("다른 목적지 고르기", vm::editConditions)
        }
    }
}

@Composable
private fun CandidateCard(
    venue: Venue,
    r: CandidateResult,
    primary: Boolean,
    retry: Boolean,
    onChoose: () -> Unit,
    onHint: (ConditionHint) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictBadge(r.verdict.kind)
            Spacer(Modifier.width(16.dp))
            Text(r.candidate.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (r.selectable) {
                val label = if (retry) "같은 계획으로 다시 안내" else "이 계획으로 안내"
                if (primary) PrimaryButton(label, onChoose, icon = Icons.Filled.Place)
                else SecondaryButton(label, onChoose, icon = Icons.Filled.Place)
            }
        }
        if (r.selectable) PlanDiagram(venue, r.candidate)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                r.verdict.reasons.forEach { reason ->
                    when (r.verdict.kind) {
                        VerdictKind.RECOMMENDED, VerdictKind.ELIGIBLE -> ReasonLine(Icons.Filled.Check, cs.secondary, reason)
                        VerdictKind.NEEDS_CHECK -> ReasonLine(Icons.Filled.Warning, cs.tertiary, "확인할 것: $reason")
                        VerdictKind.EXCLUDED -> ReasonLine(Icons.Filled.Warning, cs.onSurfaceVariant, reason)
                    }
                }
                // 현장 확인 사항(예: 주차 여유 공간)은 판정과 별개라 회색 정보 아이콘으로 구분한다.
                r.verdict.cautions.forEach { ReasonLine(Icons.Filled.Info, cs.onSurfaceVariant, "현장 확인: $it", muted = true) }
                r.verdict.hint?.let { hint ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hint.label, style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.weight(1f))
                        SecondaryButton("이 조건으로 다시 보기", { onHint(hint) })
                    }
                }
                Text(evidenceLine(r), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (primary) {
                val entrance = venue.entrance(r.candidate.walk.toEntranceId)
                EntranceImage(entrance?.photos?.firstOrNull(), contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.width(380.dp))
            }
        }
    }
}

@Composable
private fun ReasonLine(icon: ImageVector, tint: Color, text: String, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

private fun evidenceLine(r: CandidateResult): String {
    val e = r.evidence ?: return "근거: 확인된 정보 없음"
    val status = when (e.status) {
        VerificationStatus.VERIFIED -> "현장 확인"
        VerificationStatus.DEMO -> "시연 데이터"
        VerificationStatus.UNVERIFIED -> "미확인"
    }
    return "근거: ${e.label} ($status)"
}
