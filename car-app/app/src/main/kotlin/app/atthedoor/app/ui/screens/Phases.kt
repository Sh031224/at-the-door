package app.atthedoor.app.ui.screens

import androidx.compose.material.icons.outlined.Lock
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
import app.atthedoor.domain.model.Labels
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

/** 계획 이름("후문 일반 주차 구역 → 정문 경사로")에서 차를 세우는 곳만 뗀다. */
fun spotName(c: Candidate): String = c.name.substringBefore(" → ")

/** 승하차 계획이면 ‘내린 뒤’, 주차 계획이면 ‘주차 후’ */
fun walkPrefix(c: Candidate): String = if (c.type == CandidateType.DROPOFF) "내린 뒤" else "주차 후"

/** ‘새봄문화회관 후문 주차장 입구’처럼 시설 이름이 앞에 붙은 지점 이름을 화면용으로 줄인다. */
fun shortPoint(name: String, venue: Venue?): String {
    val prefix = venue?.aliases.orEmpty().sortedByDescending { it.length }.firstOrNull { name.startsWith("$it ") }
    return prefix?.let { name.removePrefix(it).trim() } ?: name
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

/** 화면 밖 동작(마이크 권한, 전화 걸기)을 하단 바에서 부를 때 쓰는 콜백 */
class PhaseCallbacks(val speak: () -> Unit, val dial: (String) -> Unit, val openNavi: () -> Unit, val cue: HandoffCue?)

/** 단계별 하단 세그먼트. 첫 번째가 주 동작(왼쪽 절반). */
fun phaseActions(state: TripUiState, vm: TripViewModel, cb: PhaseCallbacks, open: (Sheet) -> Unit): Pair<Segment, List<Segment>>? {
    val phase = state.trip?.phase ?: return null
    return when (phase) {
        TripPhase.PLANNING -> Segment("이 조건으로 계획 보기", vm::confirmConditions, SegmentKind.PRIMARY, Glyph.Arrow, enabled = state.venue != null) to
            listOf(if (state.voice.listening) Segment("듣고 있어요 · 멈추기", vm::stopListening, icon = Glyph.Mic) else Segment("말로 정하기", cb.speak, icon = Glyph.Mic))
        TripPhase.CHOOSING -> {
            val focus = focusOf(state)
            if (focus == null) {
                Segment("조건 바꾸기", vm::editConditions, SegmentKind.PRIMARY, Glyph.Edit) to listOf(Segment("다른 목적지 고르기", { vm.editConditions(); open(Sheet.VENUE) }))
            } else {
                // 시트에 보이는 줄 수(가능 + 확인 필요)와 같게 센다. 추천하지 않는 곳은 시트 안에서 따로 접어 둔다.
                val others = state.evaluation?.results?.count { it.verdict.kind != VerdictKind.EXCLUDED && it.candidate.id != focus.candidate.id } ?: 0
                val retry = state.trip.notice != null && focus.candidate.id == state.trip.selectedCandidateId
                Segment(if (retry) "같은 계획으로 다시 안내" else "이 계획으로 안내", { vm.choosePlan(focus.candidate) }, SegmentKind.PRIMARY, Glyph.Navigate) to
                    listOf(Segment(if (others > 0) "다른 계획 ${others}개 보기" else "다른 후보 보기", { open(Sheet.OTHER_PLANS) }))
            }
        }
        TripPhase.NAVI_REQUESTED -> Segment(
            if (state.navi == NaviStatus.SEARCHING) "내비가 경로를 찾고 있어요 · 보통 10초" else "내비에 목적지를 보내는 중이에요",
            {}, SegmentKind.PRIMARY, Glyph.Navigate, enabled = false, busy = true,
        ) to listOf(Segment("보내기 취소", vm::cancelNavigation))
        // 도착 신호(기어 P 변화, 내비 도착) 전에는 ‘주차했어요’를 맨 앞에 두지 않는다. 출발 직후 잘못 누르지 않게 한다.
        TripPhase.APPROACHING -> if (cb.cue != null || state.naviArrived) {
            Segment("주차했어요", { vm.confirmParking(null) }, SegmentKind.PRIMARY, Glyph.Check) to
                listOf(Segment("만차예요 · 다른 곳 찾기", vm::reportParkingFull, SegmentKind.REPORT, Glyph.Warning))
        } else {
            Segment("내비 화면 보기", cb.openNavi, SegmentKind.PRIMARY, Glyph.Navigate) to
                listOf(Segment("주차했어요", { vm.confirmParking(null) }, icon = Glyph.Check))
        }
        TripPhase.PARKED -> Segment("입구로 출발", vm::startWalking, SegmentKind.PRIMARY, Glyph.Walk) to
            listOfNotNull(Segment("휴대폰 없이 진행", vm::skipPhone).takeIf { state.share is ShareState.Ready })
        TripPhase.WALKING -> Segment("입구에 도착했어요", vm::arrivedAtEntrance, SegmentKind.PRIMARY, Glyph.Check) to
            listOf(Segment("입구가 막혀 있어요 · 다른 입구 찾기", { vm.reportEntranceBlocked() }, SegmentKind.REPORT, Glyph.Warning))
        TripPhase.VISITING -> Segment("차로 돌아가기", vm::startReturn, SegmentKind.PRIMARY, Glyph.Car) to listOf(Segment("아직 도착하지 않았어요", vm::undoArrival))
        TripPhase.RETURNING -> Segment("차에 탔어요", vm::backInCar, SegmentKind.PRIMARY, Glyph.Check) to
            listOf(Segment("길이 막혀 있어요 · 다른 길 찾기", { vm.reportEntranceBlocked() }, SegmentKind.REPORT, Glyph.Warning))
        TripPhase.REPLANNING -> null
        TripPhase.NEEDS_CHECK -> {
            val contact = state.venue?.contact
            if (contact != null) Segment("시설에 전화", { cb.dial(contact) }, SegmentKind.PRIMARY, Glyph.Phone) to
                listOf(Segment("조건 바꾸기", { open(Sheet.CHANGE) }, icon = Glyph.Edit), Segment("새로 계획하기", vm::startOver))
            else Segment("조건 바꾸기", { open(Sheet.CHANGE) }, SegmentKind.PRIMARY, Glyph.Edit) to listOf(Segment("새로 계획하기", vm::startOver))
        }
        TripPhase.COMPLETED -> if (state.trip.feedback == null) {
            val n = state.feedbackDraft.size
            Segment(if (n == 0) "안내대로였어요" else "${n}개 알려 주기", { vm.giveFeedback(state.feedbackDraft) }, SegmentKind.PRIMARY, Glyph.Check) to
                listOf(Segment("새로 계획하기", vm::startOver))
        } else Segment("새로 계획하기", vm::startOver, SegmentKind.PRIMARY, Glyph.Arrow) to emptyList()
        TripPhase.CANCELLED -> Segment("새로 계획하기", vm::startOver, SegmentKind.PRIMARY, Glyph.Arrow) to emptyList()
    }
}

// ── C1 누구와 내리나요 ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningBody(state: TripUiState, vm: TripViewModel, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val d = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
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
        Text("차에서 내려 입구까지 누가 함께 가나요?", style = Type.display, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            ChoiceTile(Labels.DRIVER_WITH, "주차하고 둘이 함께 걸어가요", d.companionRequired && !d.otherGuardian,
                { vm.updateDraft(d.copy(companionRequired = true, otherGuardian = false)) }, Modifier.weight(1f).heightIn(min = 220.dp), icon = Glyph.Pair)
            ChoiceTile(Labels.OTHER_GUARDIAN, "주차하는 동안 곁에 있을 사람이 있어요", d.companionRequired && d.otherGuardian,
                { vm.updateDraft(d.copy(companionRequired = true, otherGuardian = true)) }, Modifier.weight(1f).heightIn(min = 220.dp), icon = Glyph.Group)
            ChoiceTile(Labels.ALONE_OK, "혼자 입구까지 갈 수 있어요", !d.companionRequired,
                { vm.updateDraft(d.copy(companionRequired = false, otherGuardian = false)) }, Modifier.weight(1f).heightIn(min = 220.dp), icon = Glyph.Person)
        }
        VoiceFeedback(state)
        Panel(Modifier.fillMaxWidth(), padding = 28.dp, spacing = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("나머지 조건", style = Type.label, color = c.inkMuted)
                Spacer(Modifier.width(24.dp))
                Text(summaryChips(d, state.arrival.label).joinToString("  ·  "), Modifier.weight(1f).clickable { open(Sheet.CONDITIONS) }, style = Type.bodyStrong, color = c.ink)
                AtdButton("바꾸기", { open(Sheet.CONDITIONS) }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 88.dp)
            }
            Hairline()
            PreviewLine(state)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Lock, null, tint = c.ink, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("이동 조건은 이 차에만 저장되고 휴대폰 링크로 보내지 않아요.", style = Type.caption, color = c.ink)
        }
    }
}

private fun summaryChips(d: TravelConditions, arrival: String): List<String> = buildList {
    add(if (d.stepFreeRequired) Labels.STEP_FREE else "계단 상관없음")
    add(if (d.preferShortWalk) Labels.SHORT_WALK else "거리 상관없음")
    add(if (d.mobilityAid == app.atthedoor.domain.model.MobilityAid.NONE) "보조기구 없음" else d.mobilityAid.label)
    if (d.hasDisabledParkingPermit) add(Labels.PERMIT)
    add("$arrival 도착")
}

/** 조건을 바꿀 때마다 다시 계산한 결과를 한 줄로. 같은 결정을 두 번 묻지 않도록 카드가 아닌 줄로 둔다. */
@Composable
private fun PreviewLine(state: TripUiState) {
    val c = Atd.colors
    val rec = state.evaluation?.recommended
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("이 조건이면", style = Type.label, color = c.inkMuted)
        Spacer(Modifier.width(24.dp))
        if (rec != null) {
            VerdictBadge(VerdictKind.RECOMMENDED)
            Spacer(Modifier.width(14.dp))
            Text("${rec.candidate.name} · ${walkPrefix(rec.candidate)} 입구까지 ${walkLabel(rec.candidate)}", style = Type.bodyStrong, color = c.ink)
        } else {
            Text("조건에 맞는 확인된 계획이 아직 없어요", style = Type.bodyStrong, color = c.caution)
        }
    }
}

/** 음성 입력 상태. 버튼은 하단 바에 있고, 여기에는 들은 말과 맞춘 조건만 보여 준다. */
@Composable
private fun VoiceFeedback(state: TripUiState) {
    val c = Atd.colors
    val v = state.voice
    val (text, color) = when {
        v.listening -> (v.partial?.let { "“$it”" } ?: "듣고 있어요… “엄마랑 둘이고, 계단 없이 가까이”처럼 말해 보세요") to c.ink
        v.recognized.isNotEmpty() -> "“${v.heard}” → ${v.recognized.joinToString(" · ")}(으)로 맞췄어요" to c.success
        v.error != null -> v.error to c.caution
        else -> return
    }
    Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 32.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyph.Mic, null, tint = color, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, style = Type.bodyStrong, color = color)
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
                sub = conditions.labels.joinToString(" · "),
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
                val gain = hint.candidate.walk.distanceM.takeIf { it.known }?.let { "(입구까지 약 ${it.value}m)" }.orEmpty()
                val who = if (hint.verdict.hint == app.atthedoor.domain.plan.ConditionHint.ADD_OTHER_GUARDIAN) "다른 보호자가 함께라면" else "장애인 주차 표지가 있다면"
                Text("$who ${hint.candidate.name}$gain${"도"} 쓸 수 있어요", Modifier.weight(1f), style = Type.bodyStrong, color = c.ink)
                Spacer(Modifier.width(24.dp))
                AtdButton(
                    if (hint.verdict.hint == app.atthedoor.domain.plan.ConditionHint.ADD_OTHER_GUARDIAN) "다른 보호자로 바꿔 보기" else "주차 표지로 바꿔 보기",
                    { vm.applyHint(hint.verdict.hint!!) }, kind = ButtonKind.SECONDARY, height = 88.dp,
                )
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
                RouteStrip(shortPoint(r.candidate.accessPoint.approach, venue), r.candidate.name, r.candidate.type == CandidateType.DROPOFF, entrance?.name ?: "입구 정보 없음", walk = null)
                Text("${walkPrefix(r.candidate)} 입구까지 ${walkLabel(r.candidate)}", style = Type.title, color = c.ink)
                distinctReasons(r, others).forEach { ReasonLine(it, ReasonKind.OK) }
                r.verdict.cautions.take(1).forEach { ReasonLine(it, ReasonKind.CHECK) }
                AtdButton("왜 이 계획인가요", { open(Sheet.EVIDENCE) }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
            }
            EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.width(520.dp).aspectRatio(1.5f))
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
        val point = shortPoint(plan.accessPoint.approach, state.venue)
        if (cue != null) Headline(if (state.naviArrived) "주차했나요?" else "여기에 주차했나요?", sub = "${cue.message}\n다른 곳에 세웠으면 오른쪽 위 ⋯에서 ‘다른 곳에 세웠어요’를 골라 주세요.")
        else Headline(state.route?.label?.let { "가고 있어요 · $it" } ?: "가고 있어요", sub = "${plan.name} · 안내를 시작할 때 내비가 알려 준 예상이에요".takeIf { state.route?.label != null } ?: plan.name)
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            InverseBlock(Modifier.width(760.dp), sub = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(if (cue != null) c.inkFaint else c.success))
                    Spacer(Modifier.width(14.dp))
                    // 기어 P 같은 차량 신호만으로는 목적지 근처라고 말하지 않는다. 내비 도착 신호가 있을 때만 그렇게 쓴다.
                    Text(
                        when {
                            state.naviArrived -> "목적지 근처예요"
                            cue != null -> "차가 멈췄어요"
                            else -> "내비게이션 안내 중"
                        },
                        style = Type.title, color = c.onInverse,
                    )
                }
            }) {
                Text(if (cue != null) "세우는 곳" else "다음 지점", style = Type.title, color = c.onInverse.copy(alpha = 0.8f))
                Text(if (cue != null) spotName(plan) else point, style = Type.hero, color = c.onInverse)
            }
            Panel(Modifier.weight(1f)) {
                Text("${walkPrefix(plan)} 이렇게 가요", style = Type.label, color = c.inkMuted)
                RouteStrip(point, plan.name, plan.type == CandidateType.DROPOFF, entrance?.name ?: "입구", walk = null, compact = true)
                Text(walkLabel(plan), style = Type.bodyStrong, color = c.ink)
                if (plan.liveAvailabilitySource == null && plan.type == CandidateType.PARKING) ReasonLine("빈자리는 현장에서 확인해야 해요", ReasonKind.CHECK)
                EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.fillMaxWidth().aspectRatio(2f))
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
        Headline(
            if (phoneOk) "휴대폰으로 입구 안내를 이어 보세요" else "차 화면으로 ${entrance?.name ?: "입구"}까지 안내할게요",
            sub = if (phoneOk) "QR을 비춘 휴대폰을 챙겨 ‘입구로 출발’을 누르세요. 걸으면서 순서와 입구를 볼 수 있어요." else null,
        )
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
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.width(360.dp).aspectRatio(1.2f))
                    StepList(plan.walk.steps, Modifier.weight(1f))
                }
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
            "${Josa.euroRo(entrance?.name ?: "입구")} 가요",
            sub = listOfNotNull(
                "${walkPrefix(plan)} ${walkLabel(plan)}",
                if (phone) "휴대폰 링크로 이어서 안내해요" else null,
                "도착하면 ‘입구에 도착했어요’",
            ).joinToString(" · "),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.width(LeftW).aspectRatio(1.5f))
            Panel(Modifier.weight(1f)) {
                StepList(plan.walk.steps, style = Type.body)
                Text("통행 가능 여부는 현장에서 확인해 주세요.", style = Type.caption, color = c.inkMuted)
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
    val note = state.trip?.parkingNote
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Headline("입구 도착을 확인했어요. 좋은 시간 보내세요", sub = "문앞 안내는 건물 입구까지예요. 방문 중에는 알림을 띄우지 않아요.")
        Panel(Modifier.fillMaxWidth(), padding = 40.dp, spacing = 0.dp) {
            Text("주차한 곳", style = Type.label, color = c.inkMuted)
            Text(state.selected?.let(::spotName) ?: "", style = Type.headline, color = c.ink)
            if (note != null) Text(note, style = Type.hero, color = c.ink, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Hairline()
            ListRow(
                if (note == null) "주차 위치 남기기 (선택)" else "주차 위치 · $note",
                subtitle = if (note == null) "돌아올 때 찾기 쉬워요" else null,
                leading = { Icon(Glyph.Parking, null, Modifier.size(40.dp), tint = c.ink) },
                onClick = { open(Sheet.PARKING_NOTE) },
            )
            Hairline()
            ListRow("이용한 입구 · 돌아갈 때 나갈 문", subtitle = state.selectedEntrance?.name ?: "입구", leading = { Icon(Glyph.Door, null, Modifier.size(40.dp), tint = c.ink) })
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
            Headline("차로 돌아가는 길", Modifier.weight(1f), sub = "올 때 지나온 길이라도 지금 열려 있는지는 현장에서 확인해 주세요")
            Column(horizontalAlignment = Alignment.End) {
                Text("주차한 곳", style = Type.caption, color = c.inkMuted)
                Text(listOfNotNull(spotName(plan), state.trip?.parkingNote).joinToString(" · "), style = Type.title, color = c.ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.width(LeftW).aspectRatio(1.5f))
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
        val blocked = state.trip?.blockedEntranceIds.orEmpty().mapNotNull { state.venue?.entrance(it)?.name }
        if (blocked.isNotEmpty()) ReasonLine("${blocked.joinToString("·")} 모두 이용할 수 없다고 알려 주셨어요", ReasonKind.BLOCK)
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
            Text("안내와 다른 곳이 있었나요? 여러 개 골라도 돼요", style = Type.title, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                listOf(FeedbackKind.ENTRANCE to Glyph.Door, FeedbackKind.ROUTE to Glyph.Walk, FeedbackKind.PARKING to Glyph.Parking).forEach { (k, icon) ->
                    ChoiceTile(k.label, null, selected = k in state.feedbackDraft, onClick = { vm.toggleFeedback(k) }, modifier = Modifier.weight(1f).heightIn(min = 200.dp), icon = icon, multi = true)
                }
            }
            Text("제보는 이 차에만 남고, 검증 전에는 다른 사람의 계획에 반영하지 않아요.", style = Type.caption, color = c.inkMuted)
        } else {
            Text(if (trip.feedback.orEmpty().isEmpty()) "고마워요. 다음 외출도 함께할게요." else "알려 주셔서 고마워요. 검증 전까지는 참고 정보로만 남겨요.", style = Type.title, color = c.ink)
        }
    }
}
