package app.atthedoor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.Arrival
import app.atthedoor.app.ShareState
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.AtdButton
import app.atthedoor.app.ui.components.AtdChip
import app.atthedoor.app.ui.components.ButtonKind
import app.atthedoor.app.ui.components.EntranceImage
import app.atthedoor.app.ui.components.Glyph
import app.atthedoor.app.ui.components.Hairline
import app.atthedoor.app.ui.components.LabelValue
import app.atthedoor.app.ui.components.ListRow
import app.atthedoor.app.ui.components.QrCode
import app.atthedoor.app.ui.components.ReasonKind
import app.atthedoor.app.ui.components.ReasonLine
import app.atthedoor.app.ui.components.SideSheet
import app.atthedoor.app.ui.components.VerdictBadge
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.model.Labels
import app.atthedoor.domain.model.MobilityAid
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.VerificationStatus
import app.atthedoor.domain.plan.VerdictKind
import app.atthedoor.domain.trip.TripPhase

/** 매번 볼 필요가 없는 정보와 덜 쓰는 동작은 오른쪽 시트로 보낸다. */
enum class Sheet { VENUE, CONDITIONS, CHANGE, OTHER_PLANS, EVIDENCE, PARKING_NOTE, QR, MORE, DEMO }

@Composable
fun BoxScope.Sheets(sheet: Sheet, state: TripUiState, vm: TripViewModel, onClose: () -> Unit, open: (Sheet) -> Unit, onSwitchMode: () -> Unit) {
    when (sheet) {
        Sheet.VENUE -> SideSheet("목적지", onClose) {
            state.venues.forEach { v ->
                ListRow(
                    v.name,
                    subtitle = listOfNotNull(v.category, v.area, "시연 데이터".takeIf { v.demo }).joinToString(" · "),
                    leading = { Icon(if (v.id == state.venue?.id) Glyph.Check else Glyph.Door, null, tint = if (v.id == state.venue?.id) Atd.colors.primary else Atd.colors.inkMuted, modifier = Modifier.size(40.dp)) },
                    onClick = { vm.selectVenue(v.id); onClose() },
                )
                Hairline()
            }
        }
        Sheet.CONDITIONS -> SideSheet("이동 조건", onClose, footer = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetPreview(state)
                AtdButton("적용", onClose, Modifier.fillMaxWidth())
            }
        }) {
            ConditionsEditor(state.draft, vm::updateDraft)
            Text("도착 예정 시각", style = Type.title, color = Atd.colors.ink)
            Text("운영·출입 시간 판정에 써요", style = Type.caption, color = Atd.colors.inkMuted)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Arrival.entries.forEach { a -> AtdChip(a.label, state.arrival == a, { vm.setArrival(a) }) }
            }
        }
        Sheet.CHANGE -> {
            var draft by remember { mutableStateOf(state.trip?.conditions ?: state.draft) }
            val initial = remember { state.trip?.conditions ?: state.draft }
            SideSheet("함께 가는 사람과 조건", onClose, footer = {
                AtdButton("이 조건으로 다시 평가", { vm.changeConditions(draft); onClose() }, Modifier.fillMaxWidth(), enabled = draft != initial)
            }) {
                Text("조건을 바꾸면 계획 전체를 다시 평가해요.", style = Type.body, color = Atd.colors.inkMuted)
                CompanionChoice(draft) { draft = it }
                ConditionsEditor(draft) { draft = it }
            }
        }
        Sheet.OTHER_PLANS -> SideSheet("다른 계획", onClose, width = 880.dp) {
            var showExcluded by remember { mutableStateOf(false) }
            val results = state.evaluation?.results.orEmpty()
            val focusId = focusOf(state)?.candidate?.id
            results.filter { it.verdict.kind != VerdictKind.EXCLUDED && it.candidate.id != focusId }.forEach { r ->
                ListRow(
                    r.candidate.name,
                    subtitle = if (r.selectable) walkLabel(r.candidate) else r.verdict.reasons.firstOrNull(),
                    leading = { VerdictBadge(r.verdict.kind) },
                    trailing = if (r.selectable) ({ AtdButton("이 계획으로 안내", { vm.choosePlan(r.candidate); onClose() }, kind = ButtonKind.SECONDARY, height = 88.dp) }) else null,
                )
                Hairline()
            }
            val excluded = results.filter { it.verdict.kind == VerdictKind.EXCLUDED }
            if (excluded.isNotEmpty()) {
                AtdButton(if (showExcluded) "추천하지 않는 곳 접기" else "추천하지 않는 곳 ${excluded.size}개 보기", { showExcluded = !showExcluded }, kind = ButtonKind.QUIET, height = 76.dp)
                if (showExcluded) excluded.forEach { r ->
                    ListRow(r.candidate.name, subtitle = r.verdict.reasons.joinToString(" · "), leading = { VerdictBadge(r.verdict.kind) })
                    r.verdict.hint?.let { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.label, Modifier.weight(1f), style = Type.body, color = Atd.colors.primary)
                            AtdButton("이렇게 바꾸기", { vm.applyHint(h); onClose() }, kind = ButtonKind.SECONDARY, height = 88.dp)
                        }
                    }
                    Hairline()
                }
            }
        }
        Sheet.EVIDENCE -> {
            val r = focusOf(state)
            SideSheet("왜 이 계획인가요", onClose, width = 880.dp) {
                if (r == null) return@SideSheet
                val entrance = state.venue?.entrance(r.candidate.walk.toEntranceId)
                Text(r.candidate.name, style = Type.headline, color = Atd.colors.ink)
                r.verdict.reasons.forEach { ReasonLine(it, ReasonKind.OK) }
                r.verdict.cautions.forEach { ReasonLine("현장 확인 · $it", ReasonKind.CHECK) }
                Hairline()
                val e = r.evidence
                LabelValue("근거", e?.let { if (it.status == VerificationStatus.DEMO) "${it.source}, ${it.checkedAt}" else "${it.label} (${statusLabel(it.status)})" } ?: "확인된 정보 없음", valueStyle = Type.body)
                EntranceImage(entrance?.photos?.firstOrNull(), illustration = entrance?.photoKind == app.atthedoor.domain.model.PhotoKind.ILLUSTRATION, contentDescription = "${entrance?.name ?: "입구"} 모습", modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
            }
        }
        Sheet.PARKING_NOTE -> SideSheet("주차 위치 남기기", onClose, footer = { AtdButton("완료", onClose, Modifier.fillMaxWidth()) }) {
            ParkingNotePicker(state.trip?.parkingNote, state.selected?.floors.orEmpty(), vm::saveParkingNote)
        }
        Sheet.QR -> SideSheet("휴대폰으로 이어 보기", onClose) {
            (state.share as? ShareState.Ready)?.let { QrCode(it.link.url, Modifier.size(440.dp).align(Alignment.CenterHorizontally)) }
                ?: Text("휴대폰 링크가 없어요", style = Type.body, color = Atd.colors.inkMuted)
            Text("휴대폰 카메라로 비추면 바로 열려요", style = Type.bodyStrong, color = Atd.colors.ink)
        }
        Sheet.MORE -> SideSheet("더보기", onClose) {
            val phase = state.trip?.phase
            if (phase == TripPhase.APPROACHING) {
                ListRow("내비 다시 안내", subtitle = "같은 계획으로 다시 보내요", leading = { Icon(Glyph.Refresh, null, Modifier.size(40.dp)) }, onClick = { vm.retryNavigationFromApproach(); onClose() })
                Hairline()
            }
            if (phase in setOf(TripPhase.APPROACHING, TripPhase.PARKED, TripPhase.WALKING, TripPhase.RETURNING, TripPhase.NEEDS_CHECK)) {
                ListRow("함께 가는 사람이 바뀌었어요", subtitle = "조건을 바꾸면 계획 전체를 다시 평가해요", leading = { Icon(Glyph.Group, null, Modifier.size(40.dp)) }, onClick = { open(Sheet.CHANGE) })
                Hairline()
            }
            ListRow("새로 계획하기", leading = { Icon(Glyph.Edit, null, Modifier.size(40.dp)) }, onClick = { vm.startOver(); onClose() })
            Hairline()
            if (phase?.finished == false) {
                var confirm by remember { mutableStateOf(false) }
                if (!confirm) {
                    ListRow("외출 그만두기", subtitle = "휴대폰 링크도 바로 지워요", leading = { Icon(Glyph.Close, null, Modifier.size(40.dp), tint = Atd.colors.danger) }, trailing = {}, onClick = { confirm = true })
                } else {
                    Text("외출 안내를 그만둘까요? 휴대폰 링크도 지워져요.", style = Type.bodyStrong, color = Atd.colors.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AtdButton("그만두기", { vm.cancel(); onClose() }, kind = ButtonKind.REPORT)
                        AtdButton("계속하기", { confirm = false }, kind = ButtonKind.SECONDARY)
                    }
                }
                Hairline()
            }
        }
        Sheet.DEMO -> SideSheet("시연 설정", onClose) {
            Text(if (vm.isFake) "지금은 모의 응답 모드예요. 차량·내비 SDK 대신 가짜 구현을 써요." else "지금은 실제 Pleos SDK 모드예요.", style = Type.body, color = Atd.colors.ink)
            AtdButton(if (vm.isFake) "실제 SDK 모드로 바꾸기" else "모의 응답 모드로 바꾸기", { onClose(); onSwitchMode() }, kind = ButtonKind.SECONDARY)
            vm.demo?.let { DemoControls(it) }
        }
    }
}

private fun statusLabel(s: VerificationStatus) = when (s) {
    VerificationStatus.VERIFIED -> "현장 확인"
    VerificationStatus.DEMO -> "시연 데이터"
    VerificationStatus.UNVERIFIED -> "미확인"
}


@Composable
private fun SheetPreview(state: TripUiState) {
    val rec = state.evaluation?.recommended
    Text(
        rec?.let { "이 조건이면 · 추천 ${it.candidate.name} (${walkPrefix(it.candidate)} ${walkLabel(it.candidate)})" } ?: "이 조건이면 · 확인된 계획이 아직 없어요",
        style = Type.bodyStrong,
        color = if (rec != null) Atd.colors.ink else Atd.colors.caution,
    )
}

@Composable
private fun CompanionChoice(c: TravelConditions, onChange: (TravelConditions) -> Unit) {
    Text("입구까지 누가 함께 가나요?", style = Type.title, color = Atd.colors.ink)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AtdChip(Labels.DRIVER_WITH, c.companionRequired && !c.otherGuardian, { onChange(c.copy(companionRequired = true, otherGuardian = false)) })
        AtdChip(Labels.OTHER_GUARDIAN, c.companionRequired && c.otherGuardian, { onChange(c.copy(companionRequired = true, otherGuardian = true)) })
        AtdChip(Labels.ALONE_OK, !c.companionRequired, { onChange(c.copy(companionRequired = false, otherGuardian = false)) })
    }
}

/** 걷는 길 조건, 보조기구, 주차 자격. 사용자가 고른 것만 반영한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionsEditor(c: TravelConditions, onChange: (TravelConditions) -> Unit) {
    val col = Atd.colors
    Text("걷는 길", style = Type.title, color = col.ink)
    Text("‘계단 없이’는 꼭 지키고, ‘짧게 걷기’는 가능하면 맞춰요.", style = Type.caption, color = col.inkMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AtdChip(Labels.STEP_FREE, c.stepFreeRequired, { onChange(c.copy(stepFreeRequired = !c.stepFreeRequired)) })
        AtdChip(Labels.SHORT_WALK, c.preferShortWalk, { onChange(c.copy(preferShortWalk = !c.preferShortWalk)) })
    }
    Text("보조기구", style = Type.title, color = col.ink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobilityAid.entries.forEach { aid -> AtdChip(aid.label, c.mobilityAid == aid, { onChange(c.copy(mobilityAid = aid)) }) }
    }
    Text("주차", style = Type.title, color = col.ink)
    AtdChip(Labels.PERMIT, c.hasDisabledParkingPermit, { onChange(c.copy(hasDisabledParkingPermit = !c.hasDisabledParkingPermit)) })
    Text("표지가 없으면 장애인 전용 구역은 후보에서 빼요.", style = Type.caption, color = col.inkMuted)
    Text("고른 것만 반영해요. 보조기구와 자격 정보는 이 차에만 있어요.", style = Type.caption, color = col.inkMuted)
}

/** 주차 위치 프리셋. 층은 시설 데이터의 이 구역 층으로 좁히고, 칸 번호는 휴대폰에서 적는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParkingNotePicker(note: String?, knownFloors: List<String>, onSave: (String) -> Unit) {
    val floors = knownFloors.ifEmpty { listOf("지상", "B1", "B2", "B3") }
    var picked by remember { mutableStateOf<String?>(null) }
    val floor = note?.split(" ")?.firstOrNull()?.takeIf { it in floors } ?: picked ?: floors.singleOrNull()
    Text(note ?: "아직 남기지 않았어요", style = Type.headline, color = Atd.colors.ink)
    if (floors.size == 1) {
        Text("층 · ${floors.first()}", style = Type.title, color = Atd.colors.ink)
    } else {
        Text("층", style = Type.title, color = Atd.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            floors.forEach { f -> AtdChip(f, floor == f, { picked = f; onSave(f) }) }
        }
    }
    if (floor != null) {
        Text("구역", style = Type.title, color = Atd.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("A", "B", "C", "D", "E").forEach { z -> AtdChip("${z}구역", note?.startsWith("$floor ${z}구역") == true, { onSave("$floor ${z}구역") }) }
        }
    }
    Text("층과 구역은 위성 위치로 알 수 없어 직접 골라요. 칸 번호는 휴대폰에서 적을 수 있어요.", style = Type.caption, color = Atd.colors.inkMuted)
}
