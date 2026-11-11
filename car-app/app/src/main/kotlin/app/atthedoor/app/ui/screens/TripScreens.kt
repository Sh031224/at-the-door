package app.atthedoor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.NaviStatus
import app.atthedoor.app.ShareState
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.DangerButton
import app.atthedoor.app.ui.components.EntranceImage
import app.atthedoor.app.ui.components.LabelValue
import app.atthedoor.app.ui.components.NoticeBanner
import app.atthedoor.app.ui.components.NoticeTone
import app.atthedoor.app.ui.components.PlanDiagram
import app.atthedoor.app.ui.components.PrimaryButton
import app.atthedoor.app.ui.components.QrCode
import app.atthedoor.app.ui.components.SecondaryButton
import app.atthedoor.app.ui.components.SectionCard
import app.atthedoor.app.ui.components.StepList
import app.atthedoor.app.ui.components.ToggleTile
import app.atthedoor.app.ui.components.VerdictBadge
import app.atthedoor.domain.model.Josa
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.trip.FeedbackKind
import app.atthedoor.domain.trip.TripPhase

/** S04 내비 요청 중. onRoutingCompleted를 받기 전에는 ‘안내 중’으로 표시하지 않는다. */
@Composable
fun NaviRequestScreen(state: TripUiState, vm: TripViewModel) {
    val plan = state.selected ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 5.dp)
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    if (state.navi == NaviStatus.SEARCHING) "내비게이션이 경로를 찾고 있어요" else "내비게이션에 목적지를 보내는 중이에요",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text("목적 지점: ${plan.accessPoint.approach}", style = MaterialTheme.typography.titleLarge)
            }
        }
        Text("경로 탐색이 끝나야 안내 중으로 바뀌어요. 30초 안에 안내가 시작되지 않으면 계획을 그대로 두고 알려 드려요.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.venue?.let { PlanDiagram(it, plan) }
    }
    SecondaryButton("계획 비교로 돌아가기", vm::cancelNavigation)
}

/** S04 차량 접근. 주차 확인은 사람이 누른다. 차량 신호는 위쪽 안내로 권유만 한다. */
@Composable
fun ApproachScreen(state: TripUiState, vm: TripViewModel) {
    val plan = state.selected ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Text("내비게이션 안내 중", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(plan.name, style = MaterialTheme.typography.headlineMedium)
        LabelValue("내비게이션 목적 지점", plan.accessPoint.approach)
        state.venue?.let { PlanDiagram(it, plan) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        SecondaryButton("함께 가는 사람이 바뀌었어요", { editing = true }, Modifier.weight(1f), icon = Icons.Filled.Edit)
        SecondaryButton("내비 다시 안내", vm::retryNavigationFromApproach, Modifier.weight(1f), icon = Icons.Filled.Refresh)
    }
    if (editing) ConditionsDialog(state.trip?.conditions ?: state.draft, onDismiss = { editing = false }) {
        editing = false
        vm.changeConditions(it)
    }
}

/** S05 주차 확인 뒤 + S06 입구까지. QR은 주행 제한과 NO_SETUP이 모두 풀렸을 때만 그린다. */
@Composable
fun HandoffScreen(state: TripUiState, vm: TripViewModel, setupBlocked: Boolean) {
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    val phase = state.trip?.phase
    Text(
        if (phase == TripPhase.PARKED) "주차를 확인했어요. ${Josa.euroRo(entrance?.name ?: "입구")} 함께 가요" else "${entrance?.name ?: "입구"}까지 이동 중",
        style = MaterialTheme.typography.headlineLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionCard(Modifier.weight(1.3f)) {
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.fillMaxWidth())
            Text("이동 순서", style = MaterialTheme.typography.titleLarge)
            StepList(plan.walk.steps)
            Text("실내 위치는 추적하지 않아요. 실제 통행 가능 여부는 현장에서 확인해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PhoneCard(state, vm, setupBlocked)
            ParkingNoteCard(state.trip?.parkingNote, state.trip?.parkedAt != null, plan.floors, vm::saveParkingNote)
        }
    }
    // 휴대폰 안내를 열었으면 도착은 걷는 사람이 휴대폰에서 누른다. 차량 화면 버튼은 휴대폰 없이 갈 때를 위한 보조 수단이다.
    val phoneGuides = state.share is ShareState.Ready
    if (phoneGuides && phase == TripPhase.WALKING) {
        NoticeBanner("입구에 닿으면 휴대폰에서 ‘입구에 도착했어요’를 눌러 주세요. 차량 화면이 따라 바뀌어요.", tone = NoticeTone.INFO)
    }
}

/**
 * 단계별 주 행동. 내용이 길어도 가려지지 않게 스크롤 밖 화면 아래에 고정한다.
 * 막힘·만차 제보는 경고 배너처럼 보이지 않도록 ‘무엇이 문제 · 무엇을 할지’를 함께 쓴 외곽선 버튼으로 둔다.
 */
@Composable
fun PhaseActions(state: TripUiState, vm: TripViewModel) {
    val phase = state.trip?.phase ?: return
    val phoneGuides = state.share is ShareState.Ready
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        when (phase) {
            TripPhase.APPROACHING -> {
                PrimaryButton("주차했어요", { vm.confirmParking(null) }, Modifier.weight(1.4f), icon = Icons.Filled.Check)
                ReportButton("만차예요 · 다른 곳 찾기", vm::reportParkingFull, Modifier.weight(1f))
            }
            TripPhase.PARKED -> {
                PrimaryButton(if (phoneGuides) "휴대폰을 챙겨 출발" else "걷기 시작", vm::startWalking, Modifier.weight(1.4f))
                ReportButton("입구가 막혀 있어요 · 다른 입구 찾기", { vm.reportEntranceBlocked() }, Modifier.weight(1f))
            }
            TripPhase.WALKING -> {
                if (phoneGuides) SecondaryButton("휴대폰 없이 여기서 도착 확인", vm::arrivedAtEntrance, Modifier.weight(1.4f))
                else PrimaryButton("입구에 도착했어요", vm::arrivedAtEntrance, Modifier.weight(1.4f), icon = Icons.Filled.Check)
                ReportButton("입구가 막혀 있어요 · 다른 입구 찾기", { vm.reportEntranceBlocked() }, Modifier.weight(1f))
            }
            TripPhase.VISITING -> {
                PrimaryButton("차로 돌아가기", vm::startReturn, Modifier.weight(1.4f))
                SecondaryButton("아직 도착하지 않았어요", vm::undoArrival, Modifier.weight(1f))
            }
            TripPhase.RETURNING -> {
                PrimaryButton("차에 탔어요", vm::backInCar, Modifier.weight(1.4f), icon = Icons.Filled.Check)
                ReportButton("길이 막혀 있어요 · 다른 길 찾기", { vm.reportEntranceBlocked() }, Modifier.weight(1f))
            }
            else -> Unit
        }
    }
}

fun hasPhaseActions(phase: TripPhase?) = phase in setOf(TripPhase.APPROACHING, TripPhase.PARKED, TripPhase.WALKING, TripPhase.VISITING, TripPhase.RETURNING)

@Composable
fun ReportButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 112.dp else app.atthedoor.app.ui.theme.TouchTarget),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(text, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Icon(Icons.Filled.ArrowForward, null, Modifier.size(28.dp))
    }
}

@Composable
private fun PhoneCard(state: TripUiState, vm: TripViewModel, setupBlocked: Boolean) {
    SectionCard {
        Text("휴대폰으로 이어 보기", style = MaterialTheme.typography.titleLarge)
        val share = state.share
        if (setupBlocked) {
            Text("QR은 주차 상태에서만 보여 드려요.", style = MaterialTheme.typography.bodyLarge)
            return@SectionCard
        }
        when (share) {
            is ShareState.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(share.link.url, Modifier.size(260.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("휴대폰 카메라로 비추면 설치 없이 바로 열려요", style = MaterialTheme.typography.titleMedium)
                        Text("입구 이미지·이동 순서·주차 메모만 담고, 24시간 뒤 사라져요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SecondaryButton("휴대폰 없이 이 화면으로 안내 받기", vm::skipPhone, Modifier.fillMaxWidth())
            }
            ShareState.Creating -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text("휴대폰 안내 링크를 만드는 중이에요", style = MaterialTheme.typography.bodyLarge)
            }
            ShareState.Failed -> {
                NoticeBanner("휴대폰 연결을 만들지 못했어요. 이 화면의 사진과 순서로 이동해 주세요.", tone = NoticeTone.WARN)
                SecondaryButton("다시 시도", vm::retryShare, icon = Icons.Filled.Refresh)
            }
            ShareState.Skipped -> Text("휴대폰 연결을 건너뛰었어요. 차량 화면의 안내를 참고해 주세요.", style = MaterialTheme.typography.bodyLarge)
            ShareState.None -> SecondaryButton("휴대폰 링크 만들기", vm::retryShare)
        }
    }
}

/** 주차 구역 메모. 차량 화면에서는 글자 입력 대신 층·구역 프리셋을 고른다. 자세한 칸 번호는 휴대폰에서 남긴다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParkingNoteCard(note: String?, locationSaved: Boolean, knownFloors: List<String>, onSave: (String) -> Unit) {
    // 층 선택지는 시설 데이터의 이 구역 층으로 좁히고, 정보가 없으면 일반 선택지를 쓴다.
    // 선택 상태는 저장된 메모에서 계산한다. 휴대폰에서 남긴 메모도 같은 칩으로 보인다.
    val floors = knownFloors.ifEmpty { listOf("지상", "B1", "B2", "B3") }
    var picked by remember { mutableStateOf<String?>(null) }
    val floor = note?.split(" ")?.firstOrNull()?.takeIf { it in floors } ?: picked
    SectionCard {
        Text("주차 위치 메모", style = MaterialTheme.typography.titleLarge)
        Text(note ?: "아직 메모가 없어요", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            floors.forEach { f -> ToggleTile(f, floor == f, { picked = f; onSave(f) }, compact = true) }
        }
        floor?.let { f ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("A", "B", "C", "D", "E").forEach { z -> ToggleTile("${z}구역", note?.startsWith("$f ${z}구역") == true, { onSave("$f ${z}구역") }, compact = true) }
            }
        }
        Text(
            if (locationSaved) "주차 확인 시점의 차량 위치를 이 차량에만 저장했어요. 층과 구역은 위성 위치로 알 수 없어 직접 남겨 주세요. 칸 번호는 휴대폰에서 적을 수 있어요."
            else "위치를 받지 못했어요. 층과 구역을 직접 남겨 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** S07 방문. 공연·진료 중에는 알림이나 설문을 띄우지 않고 조용히 멈춘다. 돌아갈 때 필요한 정보만 남긴다. */
@Composable
fun VisitScreen(state: TripUiState, vm: TripViewModel) {
    val entrance = state.selectedEntrance
    Text("입구 도착을 확인했어요", style = MaterialTheme.typography.headlineLarge)
    Text("문앞 안내는 건물 입구까지예요. 좋은 시간 보내세요.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionCard(Modifier.weight(1f)) {
            Text("주차 위치", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.trip?.parkingNote ?: "메모 없음", style = MaterialTheme.typography.displaySmall)
            LabelValue("돌아갈 때 나갈 문", entrance?.name ?: "입구")
        }
        SectionCard(Modifier.weight(1f)) {
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.fillMaxWidth())
        }
    }
}

/** 복귀. 저장한 주차 메모와 입구 이미지를 다시 보여 주되, 지금도 지나갈 수 있는지는 사용자가 확인한다. */
@Composable
fun ReturnScreen(state: TripUiState, vm: TripViewModel) {
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    Text("차로 돌아가는 길", style = MaterialTheme.typography.headlineLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionCard(Modifier.weight(1f)) {
            LabelValue("주차 위치", state.trip?.parkingNote ?: "메모 없음")
            LabelValue("나갈 문", entrance?.name ?: "입구")
            if (plan.walk.returnSteps.isNotEmpty()) {
                Text("돌아가는 순서", style = MaterialTheme.typography.titleLarge)
                StepList(plan.walk.returnSteps)
            } else {
                Text("올 때 지나온 길 (거꾸로 따라가세요)", style = MaterialTheme.typography.titleLarge)
                StepList(plan.walk.steps.asReversed())
            }
            Text("이전에 지나갔다고 지금도 열려 있다고 보장하지 않아요. 막혀 있으면 알려 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(Modifier.weight(1f)) {
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.fillMaxWidth())
        }
    }
}

/** 확인된 대안 없음. 임의의 문이나 비상구를 안내하지 않고, 시설 연락처와 선택지를 준다. */
@Composable
fun NeedsCheckScreen(state: TripUiState, vm: TripViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Text("확인된 대안이 없어요", style = MaterialTheme.typography.headlineLarge)
        Text("같은 조건을 만족하는 계획을 찾지 못했어요. 없는 정보를 만들어 안내하지 않아요.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.evaluation?.results?.forEach { r ->
            Row(verticalAlignment = Alignment.Top) {
                VerdictBadge(r.verdict.kind)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(r.candidate.name, style = MaterialTheme.typography.titleMedium)
                    Text(r.verdict.reasons.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.venue?.let { v -> v.contact?.let { LabelValue("시설 연락처", if (v.demo) "$it (시연용 번호)" else it) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        SecondaryButton("조건 바꾸기", { editing = true }, Modifier.weight(1f), icon = Icons.Filled.Edit)
        SecondaryButton("새로 계획하기", vm::startOver, Modifier.weight(1f))
        SecondaryButton("외출 취소", vm::cancel, Modifier.weight(1f))
    }
    if (editing) ConditionsDialog(state.trip?.conditions ?: state.draft, onDismiss = { editing = false }) {
        editing = false
        vm.changeConditions(it)
    }
}

/** S08 외출 종료. 선택적으로만 정보 오류를 받는다. 제보는 이 기기에만 남고 확정 정보로 바뀌지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoneScreen(state: TripUiState, vm: TripViewModel) {
    val trip = state.trip ?: return
    val completed = trip.phase == TripPhase.COMPLETED
    var picked by remember { mutableStateOf(emptySet<FeedbackKind>()) }
    SectionCard {
        Text(if (completed) "오늘 외출을 마쳤어요" else "외출을 취소했어요", style = MaterialTheme.typography.headlineLarge)
        state.selected?.let { LabelValue("이용한 계획", it.name) }
        if (completed) {
            if (trip.feedback == null) {
                Text("안내와 다른 곳이 있었나요? (선택, 여러 개 고를 수 있어요)", style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeedbackKind.entries.forEach { k -> ToggleTile(k.label, k in picked, { picked = if (k in picked) picked - k else picked + k }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrimaryButton("알려 주기", { vm.giveFeedback(picked) }, enabled = picked.isNotEmpty())
                    SecondaryButton("건너뛰기", { vm.giveFeedback(emptySet()) })
                }
                Text("제보는 이 차량에만 남고, 검증 전에는 다른 사람의 계획에 반영하지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (trip.feedback.orEmpty().isEmpty()) "고마워요." else "알려 주셔서 고마워요. 검증 전까지는 참고 정보로만 남겨요.", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (completed && trip.feedback == null) SecondaryButton("새 외출 계획", vm::startOver, Modifier.fillMaxWidth())
    else PrimaryButton("새 외출 계획", vm::startOver, Modifier.fillMaxWidth())
}

@Composable
fun ConditionsDialog(initial: TravelConditions, onDismiss: () -> Unit, onApply: (TravelConditions) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("함께 가는 사람과 조건", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConditionsEditor(draft) { draft = it }
                Text("조건을 바꾸면 계획 전체를 다시 평가해요.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { PrimaryButton("다시 평가하기", { onApply(draft) }, enabled = draft != initial) },
        dismissButton = { SecondaryButton("닫기", onDismiss) },
    )
}
