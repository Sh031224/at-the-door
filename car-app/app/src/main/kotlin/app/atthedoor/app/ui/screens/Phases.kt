package app.atthedoor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.NaviStatus
import app.atthedoor.app.ShareState
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.AtdButton
import app.atthedoor.app.ui.components.AtdChip
import app.atthedoor.app.ui.components.ButtonKind
import app.atthedoor.app.ui.components.ChoiceTile
import app.atthedoor.app.ui.components.EntranceImage
import app.atthedoor.app.ui.components.Glyph
import app.atthedoor.app.ui.components.Hairline
import app.atthedoor.app.ui.components.Headline
import app.atthedoor.app.ui.components.InverseBlock
import app.atthedoor.app.ui.components.LabelValue
import app.atthedoor.app.ui.components.ListRow
import app.atthedoor.app.ui.components.Panel
import app.atthedoor.app.ui.components.QrCode
import app.atthedoor.app.ui.components.ReasonKind
import app.atthedoor.app.ui.components.ReasonLine
import app.atthedoor.app.ui.components.RouteStrip
import app.atthedoor.app.ui.components.Segment
import app.atthedoor.app.ui.components.SegmentKind
import app.atthedoor.app.ui.components.StepList
import app.atthedoor.app.ui.components.VerdictBadge
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Josa
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.plan.CandidateResult
import app.atthedoor.domain.plan.VerdictKind
import app.atthedoor.domain.trip.FeedbackKind
import app.atthedoor.domain.trip.HandoffCue
import app.atthedoor.domain.trip.TripPhase

/** 본문 7:5 분할(914/648dp). */
private val LeftW = 914.dp
private val Gap = 24.dp

/** 지금 보여 줄 계획: 고른 계획이 선택 가능하면 그것, 아니면 추천. 화면은 판정을 바꾸지 않고 고르기만 한다. */
fun focusOf(state: TripUiState): CandidateResult? {
    val ev = state.evaluation ?: return null
    val selected = state.trip?.selectedCandidateId?.let { id -> ev.results.firstOrNull { it.candidate.id == id } }
    return selected?.takeIf { it.selectable } ?: ev.recommended
}

/** 다른 선택 가능 후보와 같은 이유는 빼고, 차이 나는 이유만 최대 2개 보여 준다. 현장 확인 사항(cautions)은 줄이지 않는다. */
fun distinctReasons(focus: CandidateResult, others: List<CandidateResult>): List<String> {
    val shared = others.filter { it.selectable && it.candidate.id != focus.candidate.id }.map { it.verdict.reasons.toSet() }
    val distinct = focus.verdict.reasons.filter { r -> shared.isEmpty() || shared.any { r !in it } }
    return distinct.ifEmpty { focus.verdict.reasons }.take(2)
}

fun walkLabel(c: Candidate): String = buildString {
    append(if (c.walk.distanceM.known) "약 ${c.walk.distanceM.value}m" else "거리 확인 필요")
    append(" · ")
    append(
        when {
            !c.walk.stairs.known -> "계단 여부 확인 필요"
            c.walk.stairs.value == true -> "계단 있음"
            else -> "계단 없음"
        },
    )
}

fun companionLabel(c: TravelConditions): String = when {
    !c.companionRequired -> "입구까지 함께 가지 않아도 돼요"
    c.otherGuardian -> "다른 보호자도 함께해요"
    else -> "제가 같이 내려요"
}

@Composable
fun PhaseBody(state: TripUiState, vm: TripViewModel, setupBlocked: Boolean, cue: HandoffCue?, open: (Sheet) -> Unit) {
    when (state.trip?.phase) {
        null, TripPhase.PLANNING -> PlanningBody(state, vm, open)
        TripPhase.CHOOSING, TripPhase.NAVI_REQUESTED -> ChoosingBody(state, vm, open)
        TripPhase.APPROACHING -> ApproachBody(state, cue)
        TripPhase.PARKED -> ParkedBody(state, vm, setupBlocked, open)
        TripPhase.WALKING -> WalkingBody(state, open)
        TripPhase.VISITING -> VisitBody(state, open)
        TripPhase.RETURNING -> ReturnBody(state)
        TripPhase.REPLANNING -> Row(Modifier.padding(top = 120.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(64.dp), color = Atd.colors.primary, strokeWidth = 5.dp)
            Spacer(Modifier.width(28.dp))
            Text("같은 조건으로 다른 계획을 찾고 있어요", style = Type.headline, color = Atd.colors.ink)
        }
        TripPhase.NEEDS_CHECK -> NoAlternativeBody(state)
        TripPhase.COMPLETED, TripPhase.CANCELLED -> DoneBody(state, vm)
    }
}

/** 단계별 하단 세그먼트. 첫 번째가 주 동작(왼쪽 절반). */
fun phaseActions(state: TripUiState, vm: TripViewModel, open: (Sheet) -> Unit): Pair<Segment, List<Segment>>? {
    val phase = state.trip?.phase ?: return null
    val more = Segment("더보기", { open(Sheet.MORE) }, icon = Glyph.More)
    return when (phase) {
        TripPhase.PLANNING -> Segment("이 조건으로 계획 보기", vm::confirmConditions, SegmentKind.PRIMARY, Glyph.Arrow, enabled = state.venue != null) to emptyList()
        TripPhase.CHOOSING -> {
            val focus = focusOf(state)
            if (focus == null) {
                Segment("조건 바꾸기", vm::editConditions, SegmentKind.PRIMARY, Glyph.Edit) to listOf(Segment("다른 목적지 고르기", { vm.editConditions(); open(Sheet.VENUE) }))
            } else {
                val others = (state.evaluation?.results?.size ?: 1) - 1
                val retry = state.trip.notice != null && focus.candidate.id == state.trip.selectedCandidateId
                Segment(if (retry) "같은 계획으로 다시 안내" else "이 계획으로 안내", { vm.choosePlan(focus.candidate) }, SegmentKind.PRIMARY, Glyph.Navigate) to
                    listOf(Segment("다른 계획 ${others}개 보기", { open(Sheet.OTHER_PLANS) }))
            }
        }
        TripPhase.NAVI_REQUESTED -> Segment(
            if (state.navi == NaviStatus.SEARCHING) "내비가 경로를 찾고 있어요 · 보통 10초" else "내비에 목적지를 보내는 중이에요",
            {}, SegmentKind.PRIMARY, Glyph.Navigate, enabled = false,
        ) to listOf(Segment("보내기 취소", vm::cancelNavigation))
        TripPhase.APPROACHING -> Segment("주차했어요", { vm.confirmParking(null) }, SegmentKind.PRIMARY, Glyph.Check) to
            listOf(Segment("만차예요 · 다른 곳 찾기", vm::reportParkingFull, SegmentKind.REPORT, Glyph.Warning), more)
        TripPhase.PARKED -> Segment("입구로 출발", vm::startWalking, SegmentKind.PRIMARY, Glyph.Walk) to buildList {
            if (state.share is ShareState.Ready) add(Segment("휴대폰 없이 보기", vm::skipPhone))
            add(Segment("입구가 막혀 있어요", { vm.reportEntranceBlocked() }, SegmentKind.REPORT, Glyph.Warning))
        }
        TripPhase.WALKING -> Segment("입구에 도착했어요", vm::arrivedAtEntrance, SegmentKind.PRIMARY, Glyph.Check) to
            listOf(Segment("입구가 막혀 있어요 · 다른 입구 찾기", { vm.reportEntranceBlocked() }, SegmentKind.REPORT, Glyph.Warning))
        TripPhase.VISITING -> Segment("차로 돌아가기", vm::startReturn, SegmentKind.PRIMARY, Glyph.Car) to listOf(Segment("아직 도착하지 않았어요", vm::undoArrival))
        TripPhase.RETURNING -> Segment("차에 탔어요", vm::backInCar, SegmentKind.PRIMARY, Glyph.Check) to
            listOf(Segment("길이 막혀 있어요 · 다른 길 찾기", { vm.reportEntranceBlocked() }, SegmentKind.REPORT, Glyph.Warning))
        TripPhase.REPLANNING -> null
        TripPhase.NEEDS_CHECK -> Segment("조건 바꾸기", { open(Sheet.CHANGE) }, SegmentKind.PRIMARY, Glyph.Edit) to
            listOf(Segment("새로 계획하기", vm::startOver), Segment("외출 그만두기", vm::cancel))
        TripPhase.COMPLETED -> if (state.trip.feedback == null) {
            val n = state.feedbackDraft.size
            Segment(if (n == 0) "안내대로였어요" else "${n}개 알려 주기", { vm.giveFeedback(state.feedbackDraft) }, SegmentKind.PRIMARY, Glyph.Check) to
                listOf(Segment("새 외출 계획", vm::startOver))
        } else Segment("새 외출 계획", vm::startOver, SegmentKind.PRIMARY, Glyph.Arrow) to emptyList()
        TripPhase.CANCELLED -> Segment("새 외출 계획", vm::startOver, SegmentKind.PRIMARY, Glyph.Arrow) to emptyList()
    }
}

// ── C1 누구와 내리나요 ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningBody(state: TripUiState, vm: TripViewModel, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val d = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).clickable { open(Sheet.VENUE) }.padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("어디로", style = Type.label, color = c.inkMuted)
            Spacer(Modifier.width(28.dp))
            Text(state.venue?.let { "${it.name} · ${it.area ?: ""}" } ?: "목적지를 골라 주세요", Modifier.weight(1f), style = Type.title, color = c.ink)
            Text("바꾸기", style = Type.label, color = c.route)
            Icon(Glyph.Chevron, null, tint = c.route, modifier = Modifier.size(36.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Column(Modifier.width(LeftW), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Text("차에서 내려 입구까지 누가 함께 가나요?", style = Type.display, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    ChoiceTile(
                        "제가 같이 내려요", "주차하고 둘이 함께 걸어가요",
                        selected = d.companionRequired && !d.otherGuardian,
                        onClick = { vm.updateDraft(d.copy(companionRequired = true, otherGuardian = false)) },
                        modifier = Modifier.weight(1f).heightIn(min = 260.dp), icon = Glyph.Pair,
                    )
                    ChoiceTile(
                        "다른 보호자도 있어요", "주차하는 동안 곁에 있을 사람이 있어요",
                        selected = d.companionRequired && d.otherGuardian,
                        onClick = { vm.updateDraft(d.copy(companionRequired = true, otherGuardian = true)) },
                        modifier = Modifier.weight(1f).heightIn(min = 260.dp), icon = Glyph.Group,
                    )
                }
                AtdChip("입구까지 함께 가지 않아도 돼요", selected = !d.companionRequired, onClick = { vm.updateDraft(d.copy(companionRequired = false, otherGuardian = false)) })
                Hairline()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("나머지 조건", style = Type.label, color = c.inkMuted)
                    Spacer(Modifier.weight(1f))
                    AtdButton("바꾸기", { open(Sheet.CONDITIONS) }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clickable { open(Sheet.CONDITIONS) }) {
                    summaryChips(d, state.arrival.label).forEach { AtdChip(it, selected = false, onClick = null) }
                }
                Text("이동 조건은 이 차에만 저장되고 휴대폰 링크로 보내지 않아요.", style = Type.caption, color = c.inkMuted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                VoicePanel(state, vm)
                PreviewPanel(state)
            }
        }
    }
}

private fun summaryChips(d: TravelConditions, arrival: String): List<String> = buildList {
    add(if (d.stepFreeRequired) "계단 없이" else "계단 상관없음")
    add(if (d.preferShortWalk) "짧게 걷기" else "거리 상관없음")
    add(if (d.mobilityAid.label == "없음") "보조기구 없음" else d.mobilityAid.label)
    if (d.hasDisabledParkingPermit) add("장애인 주차 자격 확인함")
    add("도착 $arrival")
}

@Composable
private fun PreviewPanel(state: TripUiState) {
    val c = Atd.colors
    val rec = state.evaluation?.recommended
    Panel {
        Text("이 조건이면", style = Type.label, color = c.inkMuted)
        if (rec != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerdictBadge(VerdictKind.RECOMMENDED)
                Spacer(Modifier.width(14.dp))
                Text(rec.candidate.name, style = Type.title, color = c.ink)
            }
            Text("주차 후 입구까지 ${walkLabel(rec.candidate)}", style = Type.body, color = c.ink)
        } else {
            Text("조건에 맞는 확인된 계획이 아직 없어요", style = Type.title, color = c.caution)
        }
        val others = state.evaluation?.selectable?.size?.minus(if (rec != null) 1 else 0) ?: 0
        if (others > 0) Text("다른 가능한 계획 ${others}개", style = Type.caption, color = c.inkMuted)
    }
}

@Composable
private fun VoicePanel(state: TripUiState, vm: TripViewModel) {
    val c = Atd.colors
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
    Panel {
        AtdButton(if (v.listening) "듣고 있어요 · 눌러서 멈추기" else "말로 정하기", if (v.listening) vm::stopListening else speak, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, icon = Glyph.Mic)
        when {
            v.listening -> Text(v.partial?.let { "“$it”" } ?: "듣고 있어요…", style = Type.body, color = c.ink)
            v.recognized.isNotEmpty() -> Text("“${v.heard}”\n→ ${v.recognized.joinToString(" · ")}(으)로 맞췄어요", style = Type.body, color = c.success)
            v.error != null -> Text(v.error, style = Type.body, color = c.caution)
            else -> Text("“엄마랑 둘이고, 계단 없이 가까이”처럼 말해 보세요", style = Type.body, color = c.inkMuted)
        }
    }
}

// ── C2 이 계획으로 갈까요 ────────────────────────────────

@Composable
private fun ChoosingBody(state: TripUiState, vm: TripViewModel, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val venue = state.venue ?: return
    val conditions = state.trip?.conditions ?: state.draft
    val focus = focusOf(state)
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Headline(
                if (focus == null) "확인된 계획이 없어요" else "이 계획으로 갈까요?",
                Modifier.weight(1f),
                sub = (listOf(companionLabel(conditions)) + conditions.labels.filter { "함께" !in it }).joinToString(" · "),
            )
            if (state.trip?.phase == TripPhase.CHOOSING) AtdButton("조건 바꾸기", vm::editConditions, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
        }
        if (focus == null) NoPlanPanel(state) else PlanHero(venue, focus, state, open)
        val hint = state.evaluation?.results?.firstOrNull { it.verdict.hint != null }
        if (hint != null && state.trip?.phase == TripPhase.CHOOSING) {
            Row(
                Modifier.fillMaxWidth().background(c.primarySoft).padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Glyph.Info, null, tint = c.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(18.dp))
                val gain = hint.candidate.walk.distanceM.takeIf { it.known }?.let { " (입구까지 약 ${it.value}m)" }.orEmpty()
                Text("${hint.verdict.hint!!.label}: ${hint.candidate.name}$gain", Modifier.weight(1f), style = Type.bodyStrong, color = c.ink)
                AtdButton("이렇게 바꾸기", { vm.applyHint(hint.verdict.hint!!) }, kind = ButtonKind.SECONDARY, height = 88.dp)
            }
        }
    }
}

@Composable
private fun PlanHero(venue: Venue, r: CandidateResult, state: TripUiState, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val entrance = venue.entrance(r.candidate.walk.toEntranceId)
    val others = state.evaluation?.results.orEmpty()
    Panel(padding = 36.dp, spacing = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VerdictBadge(r.verdict.kind)
            Spacer(Modifier.width(18.dp))
            Text(r.candidate.name, style = Type.headline, color = c.ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RouteStrip(r.candidate.accessPoint.approach, r.candidate.name, r.candidate.type == CandidateType.DROPOFF, entrance?.name ?: "입구 정보 없음", walk = null)
                Text("주차 후 입구까지 ${walkLabel(r.candidate)}", style = Type.title, color = c.ink)
                distinctReasons(r, others).forEach { ReasonLine(it, ReasonKind.OK) }
                r.verdict.cautions.take(1).forEach { ReasonLine(it, ReasonKind.CHECK) }
                AtdButton("왜 이 계획인가요", { open(Sheet.EVIDENCE) }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
            }
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.width(520.dp).aspectRatio(1.5f))
        }
    }
}

@Composable
private fun NoPlanPanel(state: TripUiState) {
    val c = Atd.colors
    val venue = state.venue
    val toCheck = state.evaluation?.of(VerdictKind.NEEDS_CHECK).orEmpty().flatMap { it.verdict.reasons }.distinct()
    Panel {
        Text(if (toCheck.isNotEmpty()) "아래 정보를 시설에 확인하면 계획을 고를 수 있어요" else "지금 조건으로 갈 수 있는 확인된 길이 없어요", style = Type.title, color = c.ink)
        toCheck.forEach { ReasonLine("확인할 것: $it", ReasonKind.CHECK) }
        venue?.contact?.let { LabelValue("시설 연락처", if (venue.demo) "$it (시연용 번호)" else it, valueStyle = Type.headline) }
    }
}

// ── C3 가는 중 ─────────────────────────────────────────

@Composable
private fun ApproachBody(state: TripUiState, cue: HandoffCue?) {
    val c = Atd.colors
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        if (cue != null) Headline("도착했나요? 주차했으면 아래 ‘주차했어요’를 눌러 주세요", sub = cue.message)
        else Headline("${Josa.euroRo(plan.accessPoint.approach)} 가고 있어요")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            InverseBlock(Modifier.width(LeftW), sub = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(c.success))
                    Spacer(Modifier.width(14.dp))
                    Text("내비게이션 안내 중", style = Type.title, color = c.onInverse)
                }
            }) {
                Text("다음 지점", style = Type.title, color = c.onInverse.copy(alpha = 0.8f))
                Text(plan.accessPoint.approach, style = Type.headline, color = c.onInverse)
                Text("계획 · ${plan.name}", style = Type.body, color = c.onInverse.copy(alpha = 0.8f))
            }
            Panel(Modifier.weight(1f)) {
                Text("주차 후 이렇게 가요", style = Type.label, color = c.inkMuted)
                RouteStrip(plan.accessPoint.approach, plan.name, plan.type == CandidateType.DROPOFF, entrance?.name ?: "입구", walk = null, compact = true)
                Text(walkLabel(plan), style = Type.bodyStrong, color = c.ink)
                if (plan.liveAvailabilitySource == null && plan.type == CandidateType.PARKING) ReasonLine("빈자리는 현장에서 확인해요", ReasonKind.CHECK)
            }
        }
    }
}

// ── C4 주차했어요 ──────────────────────────────────────

@Composable
private fun ParkedBody(state: TripUiState, vm: TripViewModel, setupBlocked: Boolean, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    val phoneOk = state.share !is ShareState.Skipped && state.share !is ShareState.Failed
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Headline(if (phoneOk) "휴대폰으로 입구 안내를 이어 보세요" else "차 화면으로 ${entrance?.name ?: "입구"}까지 안내할게요")
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Panel(Modifier.width(560.dp)) {
                when {
                    setupBlocked -> Text("차를 완전히 세우면 QR이 보여요", style = Type.title, color = c.ink)
                    state.share is ShareState.Ready -> {
                        QrCode((state.share as ShareState.Ready).link.url, Modifier.size(400.dp).align(Alignment.CenterHorizontally))
                        Text("휴대폰 카메라로 비추면 바로 열려요", style = Type.bodyStrong, color = c.ink)
                        Text("설치·로그인 없이 열리고 24시간 뒤 사라져요", style = Type.caption, color = c.inkMuted)
                    }
                    state.share is ShareState.Creating -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(40.dp), color = c.primary)
                        Spacer(Modifier.width(18.dp))
                        Text("휴대폰 링크를 만드는 중이에요", style = Type.body, color = c.ink)
                    }
                    state.share is ShareState.Failed -> {
                        Text("휴대폰 연결을 만들지 못했어요", style = Type.title, color = c.ink)
                        Text("차 화면의 사진과 순서로 안내할게요", style = Type.body, color = c.inkMuted)
                        AtdButton("다시 만들기", vm::retryShare, kind = ButtonKind.SECONDARY, icon = Glyph.Refresh)
                    }
                    state.share is ShareState.Skipped -> {
                        Text("휴대폰 없이 차 화면으로 안내해요", style = Type.title, color = c.ink)
                        AtdButton("QR 다시 보기", vm::retryShare, kind = ButtonKind.QUIET, icon = Glyph.Qr, height = 76.dp)
                    }
                    else -> AtdButton("휴대폰 링크 만들기", vm::retryShare, kind = ButtonKind.SECONDARY, icon = Glyph.Qr)
                }
            }
            Panel(Modifier.weight(1f)) {
                EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.fillMaxWidth().height(300.dp))
                StepList(plan.walk.steps)
                Hairline()
                ParkingNoteRow(state.trip?.parkingNote) { open(Sheet.PARKING_NOTE) }
            }
        }
    }
}

@Composable
private fun ParkingNoteRow(note: String?, onClick: () -> Unit) {
    ListRow(
        title = note ?: "주차 위치 남기기 (선택)",
        subtitle = if (note == null) "층과 구역은 위성 위치로 알 수 없어 직접 골라요" else "주차 위치",
        leading = { Icon(Glyph.Parking, null, tint = Atd.colors.ink, modifier = Modifier.size(40.dp)) },
        onClick = onClick,
    )
}

// ── C5 입구로 가는 중 ─────────────────────────────────

@Composable
private fun WalkingBody(state: TripUiState, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    val phone = state.share is ShareState.Ready
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Headline(
            if (phone) "휴대폰에서 안내하고 있어요" else "${entrance?.name ?: "입구"}까지 함께 가요",
            sub = if (phone) "입구에 닿으면 휴대폰이나 이 화면에서 ‘입구에 도착했어요’를 눌러 주세요" else null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.width(LeftW).aspectRatio(1.5f))
            Panel(Modifier.weight(1f)) {
                StepList(plan.walk.steps, style = Type.body)
                Text("실내 위치는 추적하지 않아요. 지나갈 수 있는지는 현장에서 확인해 주세요.", style = Type.caption, color = c.inkMuted)
                Hairline()
                ParkingNoteRow(state.trip?.parkingNote) { open(Sheet.PARKING_NOTE) }
                if (phone) AtdButton("QR 다시 보기", { open(Sheet.QR) }, kind = ButtonKind.QUIET, icon = Glyph.Qr, height = 76.dp)
            }
        }
    }
}

// ── C6 방문 중 ─────────────────────────────────────────

@Composable
private fun VisitBody(state: TripUiState, open: (Sheet) -> Unit) {
    val c = Atd.colors
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Headline("입구 도착을 확인했어요. 좋은 시간 보내세요", sub = "문앞 안내는 건물 입구까지예요. 공연·진료 중에는 알림을 띄우지 않아요.")
        Panel(Modifier.fillMaxWidth(), padding = 48.dp) {
            Text("주차 위치", style = Type.label, color = c.inkMuted)
            val note = state.trip?.parkingNote
            if (note != null) Text(note, style = Type.hero, color = c.ink)
            else AtdButton("주차 위치 남기기", { open(Sheet.PARKING_NOTE) }, kind = ButtonKind.SECONDARY, icon = Glyph.Parking)
            Text("돌아갈 때 나갈 문 · ${state.selectedEntrance?.name ?: "입구"}", style = Type.title, color = c.ink)
        }
    }
}

// ── C7 차로 돌아가기 ───────────────────────────────────

@Composable
private fun ReturnBody(state: TripUiState) {
    val c = Atd.colors
    val plan = state.selected ?: return
    val entrance = state.selectedEntrance
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Headline("차로 돌아가는 길", Modifier.weight(1f), sub = "지난번에 지나갔다고 지금도 열려 있다고 보장하지 않아요")
            Column(horizontalAlignment = Alignment.End) {
                Text("주차 위치", style = Type.caption, color = c.inkMuted)
                Text(state.trip?.parkingNote ?: "메모 없음", style = Type.headline, color = c.ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            EntranceImage(entrance?.photos?.firstOrNull(), "${entrance?.name ?: "입구"} 모습", Modifier.width(LeftW).aspectRatio(1.5f))
            Panel(Modifier.weight(1f)) {
                Text("${Josa.euroRo(entrance?.name ?: "입구")} 나가서", style = Type.label, color = c.inkMuted)
                if (plan.walk.returnSteps.isNotEmpty()) StepList(plan.walk.returnSteps)
                else {
                    Text("올 때 지나온 길을 거꾸로 따라가세요", style = Type.body, color = c.inkMuted)
                    StepList(plan.walk.steps.asReversed())
                }
            }
        }
    }
}

// ── X2 대안 없음 ───────────────────────────────────────

@Composable
private fun NoAlternativeBody(state: TripUiState) {
    val c = Atd.colors
    var showWhy by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Headline("확인된 대안이 없어요", sub = "같은 조건을 만족하는 계획을 찾지 못했어요. 없는 정보를 만들어 안내하지 않아요.")
        Panel(Modifier.fillMaxWidth(), padding = 40.dp) {
            Text("시설에 문의해 보세요", style = Type.title, color = c.ink)
            val v = state.venue
            Text(v?.contact?.let { if (v.demo) "$it  (시연용 번호)" else it } ?: "등록된 연락처가 없어요", style = Type.display, color = c.ink)
        }
        AtdButton(if (showWhy) "찾지 못한 이유 접기" else "찾지 못한 이유 ${state.evaluation?.results?.size ?: 0}개 보기", { showWhy = !showWhy }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
        if (showWhy) Panel(Modifier.fillMaxWidth(), spacing = 0.dp) {
            state.evaluation?.results?.forEach { r ->
                ListRow(r.candidate.name, subtitle = r.verdict.reasons.joinToString(" · "), leading = { VerdictBadge(r.verdict.kind) })
                Hairline()
            }
        }
    }
}

// ── C8 마쳤어요 ────────────────────────────────────────

@Composable
private fun DoneBody(state: TripUiState, vm: TripViewModel) {
    val c = Atd.colors
    val trip = state.trip ?: return
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        if (trip.phase == TripPhase.CANCELLED) {
            Headline("외출 안내를 그만뒀어요", sub = "공유 링크는 바로 지웠어요.")
            return@Column
        }
        Headline("오늘 외출을 마쳤어요", sub = state.selected?.let { "이용한 계획 · ${it.name}" })
        if (trip.feedback == null) {
            Text("안내와 다른 곳이 있었나요? (선택, 여러 개 고를 수 있어요)", style = Type.title, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                listOf(FeedbackKind.ENTRANCE to Glyph.Door, FeedbackKind.ROUTE to Glyph.Walk, FeedbackKind.PARKING to Glyph.Parking).forEach { (k, icon) ->
                    ChoiceTile(k.label, null, selected = k in state.feedbackDraft, onClick = { vm.toggleFeedback(k) }, modifier = Modifier.weight(1f).heightIn(min = 200.dp), icon = icon)
                }
            }
            Text("제보는 이 차에만 남고, 검증 전에는 다른 사람의 계획에 반영하지 않아요.", style = Type.caption, color = c.inkMuted)
        } else {
            Text(if (trip.feedback.orEmpty().isEmpty()) "고마워요. 다음 외출도 함께할게요." else "알려 주셔서 고마워요. 검증 전까지는 참고 정보로만 남겨요.", style = Type.title, color = c.ink)
        }
    }
}
