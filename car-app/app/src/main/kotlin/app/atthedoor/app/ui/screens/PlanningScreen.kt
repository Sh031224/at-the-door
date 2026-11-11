package app.atthedoor.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.Arrival
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.MicIcon
import app.atthedoor.app.ui.components.NoticeBanner
import app.atthedoor.app.ui.components.NoticeTone
import app.atthedoor.app.ui.components.Pill
import app.atthedoor.app.ui.components.PrimaryButton
import app.atthedoor.app.ui.components.SecondaryButton
import app.atthedoor.app.ui.components.SectionCard
import app.atthedoor.app.ui.components.ToggleTile
import app.atthedoor.app.ui.theme.TouchTarget
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.plan.Evaluation
import app.atthedoor.domain.plan.VerdictKind

/** S01 목적지 선택 + S02 이동 조건. 음성은 입력 방식 중 하나이고, 정형 입력만으로 끝까지 진행된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(state: TripUiState, vm: TripViewModel) {
    Text("어디로, 누구와 가나요?", style = MaterialTheme.typography.headlineLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            VenuePicker(state, vm::selectVenue)
            SectionCard {
                ConditionsEditor(state.draft, vm::updateDraft)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            VoiceCard(state, vm)
            SectionCard {
                Text("도착 예정 시각", style = MaterialTheme.typography.titleLarge)
                Text("운영·출입 시간 판정에 써요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Arrival.entries.forEach { a ->
                        ToggleTile(a.label, state.arrival == a, { vm.setArrival(a) }, compact = true)
                    }
                }
            }
            PreviewCard(state.evaluation)
        }
    }
}

@Composable
private fun VenuePicker(state: TripUiState, onSelect: (String) -> Unit) {
    SectionCard {
        Text("목적지", style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.venues.forEach { v ->
                val selected = v.id == state.venue?.id
                val cs = MaterialTheme.colorScheme
                Surface(
                    onClick = { onSelect(v.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) cs.primaryContainer else cs.surface,
                    border = BorderStroke(if (selected) 3.dp else 2.dp, if (selected) cs.primary else cs.outline),
                ) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleLarge)
                            Text(listOfNotNull(v.category, v.area).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        if (v.demo) Pill("시연 데이터")
                    }
                }
            }
        }
    }
}

/** 이동 조건 편집. 출발 전 계획과 여행 중 조건 변경에서 함께 쓴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionsEditor(c: TravelConditions, onChange: (TravelConditions) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text("이동 조건", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleTile("계단 없는 동선", c.stepFreeRequired, { onChange(c.copy(stepFreeRequired = !c.stepFreeRequired)) }, Modifier.weight(1f), description = "꼭 지켜야 해요")
        ToggleTile("보행 거리 짧게", c.preferShortWalk, { onChange(c.copy(preferShortWalk = !c.preferShortWalk)) }, Modifier.weight(1f), description = "가능하면 좋아요")
    }
    Text("차에서 내려 입구까지 함께 이동해야 하나요?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleTile("운전자와 입구까지 함께", c.companionRequired, { onChange(c.copy(companionRequired = !c.companionRequired, otherGuardian = if (c.companionRequired) false else c.otherGuardian)) }, Modifier.weight(1f))
        ToggleTile(
            "다른 보호자도 함께해요",
            c.otherGuardian,
            { if (c.companionRequired) onChange(c.copy(otherGuardian = !c.otherGuardian)) },
            Modifier.weight(1f),
            description = if (c.companionRequired) "운전자가 주차하는 동안 곁에 있을 사람" else "함께 이동이 필요할 때 골라요",
        )
    }
    Text("보조기구 (직접 고른 것만 반영해요)", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobilityAid.entries.forEach { aid ->
            ToggleTile(aid.label, c.mobilityAid == aid, { onChange(c.copy(mobilityAid = aid)) })
        }
    }
    ToggleTile(
        "장애인 전용 주차 자격 확인함",
        c.hasDisabledParkingPermit,
        { onChange(c.copy(hasDisabledParkingPermit = !c.hasDisabledParkingPermit)) },
        Modifier.fillMaxWidth(),
        description = "확인하지 않으면 전용 구역은 후보에서 빼요",
    )
    Text("이동 조건·보조기구·자격 정보는 이 차량 안에만 두고 휴대폰 링크로 보내지 않아요.", style = MaterialTheme.typography.bodySmall, color = muted)
}

@Composable
private fun VoiceCard(state: TripUiState, vm: TripViewModel) {
    val v = state.voice
    val context = androidx.compose.ui.platform.LocalContext.current
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.startListening() else vm.micDenied() }
    val speak = {
        val granted = vm.isFake || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.startListening() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    SectionCard {
        Text("말로 정하기", style = MaterialTheme.typography.titleLarge)
        Text("예: “새봄문화회관 가. 엄마랑 둘이고, 계단 없이 가까이 걸으면 좋겠어.”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (v.listening) {
            SecondaryButton("듣는 중… 눌러서 멈추기", vm::stopListening, Modifier.fillMaxWidth(), icon = MicIcon)
            v.partial?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium) }
        } else {
            SecondaryButton("말하기", speak, Modifier.fillMaxWidth(), icon = MicIcon)
        }
        v.heard?.let { Text("들은 말: “$it”", style = MaterialTheme.typography.bodyLarge) }
        if (v.recognized.isNotEmpty()) {
            Text("이렇게 알아들었어요. 아래 조건에서 고칠 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                v.recognized.take(4).forEach { Pill(it, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        v.error?.let { NoticeBanner(it, tone = NoticeTone.WARN) }
    }
}

/** 계획 화면의 주 버튼. 내용이 길어도 가려지지 않게 스크롤 밖 하단에 고정한다. */
@Composable
fun PlanningActionBar(state: TripUiState, vm: TripViewModel) {
    PrimaryButton("이 조건으로 계획 보기", vm::confirmConditions, Modifier.fillMaxWidth(), enabled = state.venue != null)
}

/** 조건을 바꾸는 즉시 규칙 엔진이 다시 평가한 결과를 요약한다. */
@Composable
private fun PreviewCard(evaluation: Evaluation?) {
    evaluation ?: return
    SectionCard {
        Text("지금 조건으로 보면", style = MaterialTheme.typography.titleLarge)
        val rec = evaluation.recommended
        Text(
            rec?.let { "추천: ${it.candidate.name}" } ?: "조건에 맞는 확인된 계획이 아직 없어요",
            style = MaterialTheme.typography.headlineSmall,
            color = if (rec != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
        )
        Text(
            "추천 ${evaluation.of(VerdictKind.RECOMMENDED).size} · 가능 ${evaluation.of(VerdictKind.ELIGIBLE).size} · 확인 필요 ${evaluation.of(VerdictKind.NEEDS_CHECK).size} · 추천하지 않음 ${evaluation.of(VerdictKind.EXCLUDED).size}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
