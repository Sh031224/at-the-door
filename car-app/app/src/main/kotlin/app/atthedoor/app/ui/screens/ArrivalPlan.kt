package app.atthedoor.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.atthedoor.app.TripUiState
import app.atthedoor.app.ui.components.*
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Josa
import app.atthedoor.domain.plan.CandidateResult
import app.atthedoor.domain.trip.TripPhase

/** 실제 차량 화면. 목적지·직접 정한 조건·도착 계획을 한 화면에서 확인한다. */
@Composable
fun ArrivalPlanBody(state: TripUiState, editConditions: () -> Unit, open: (Sheet) -> Unit) {
    val c = Atd.colors
    val venue = state.venue ?: return
    val focus = focusOf(state)
    val reviewing = state.trip?.phase == TripPhase.PLANNING
    val conditions = if (reviewing) state.draft else state.trip?.conditions ?: state.draft
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Panel(Modifier.fillMaxWidth().clickable(enabled = state.trip?.phase != TripPhase.NAVI_REQUESTED) {
            if (!reviewing) editConditions()
            open(Sheet.VENUE)
        }, padding = 24.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyph.Navigate, null, Modifier.size(36.dp), tint = c.inkMuted)
                Spacer(Modifier.width(20.dp))
                Text(venue.name, Modifier.weight(1f), style = Type.title, color = c.ink)
                Text("목적지 변경", style = Type.body, color = c.inkMuted)
                Icon(Glyph.Chevron, null, Modifier.size(32.dp), tint = c.inkMuted)
            }
        }
        if (focus == null) {
            Headline("확인된 계획이 없어요", sub = "직접 정한 조건을 확인하거나 다른 목적지를 골라 주세요.")
            conditions.labels.forEach { Text(it, style = Type.bodyStrong, color = c.inkMuted) }
        } else {
            BoxWithConstraints {
                val wide = maxWidth >= 1100.dp
                val copy: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(if (reviewing) "이동 조건을 먼저 확인해 주세요" else "✓  지금 조건에 맞는 도착 계획", style = Type.bodyStrong, color = if (reviewing) c.inkMuted else c.success)
                        val entrance = venue.entrance(focus.candidate.walk.toEntranceId)?.name ?: "입구"
                        val title = when {
                            focus.candidate.type == CandidateType.DROPOFF -> "입구 가까이 내리고,\n${Josa.euroRo(entrance)} 가요."
                            conditions.companionRequired -> "함께 주차하고,\n${Josa.euroRo(entrance)} 가요."
                            else -> "차를 세우고,\n${Josa.euroRo(entrance)} 가요."
                        }
                        Text(title, style = if (wide) Type.hero else Type.display, color = c.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(42.dp)) {
                            Column {
                                Text(focus.candidate.walk.distanceM.takeIf { it.known }?.value?.let { "${it}m" } ?: "확인 필요", style = Type.display, color = c.ink)
                                Text("${walkPrefix(focus.candidate)} 입구까지", style = Type.caption, color = c.inkMuted)
                            }
                            Column {
                                Text(when { !focus.candidate.walk.stairs.known -> "확인 필요"; focus.candidate.walk.stairs.value == true -> "계단 있음"; else -> "계단 없음" }, style = Type.display, color = c.ink)
                                Text("전체 보행 동선 기준", style = Type.caption, color = c.inkMuted)
                            }
                        }
                        Hairline()
                        Text(if (reviewing) "확인 전 이동 조건" else "직접 정한 이동 조건", style = Type.caption, color = c.inkMuted)
                        Text(conditions.labels.joinToString(" · "), style = Type.bodyStrong, color = c.ink)
                        if (state.trip?.phase != TripPhase.NAVI_REQUESTED) AtdButton("조건 바꾸기", {
                            if (!reviewing) editConditions()
                            open(Sheet.CONDITIONS)
                        }, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
                        Text("조건은 이 차에만 저장해요", style = Type.caption, color = c.inkMuted)
                    }
                }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1.05f)) { copy() }
                    PlanDiagram(focus, venue.name, venue.entrance(focus.candidate.walk.toEntranceId)?.name ?: "입구", Modifier.weight(1f)) { open(Sheet.EVIDENCE) }
                } else Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    copy()
                    PlanDiagram(focus, venue.name, venue.entrance(focus.candidate.walk.toEntranceId)?.name ?: "입구", Modifier.fillMaxWidth()) { open(Sheet.EVIDENCE) }
                }
            }
            if (focus.candidate.type == CandidateType.PARKING && focus.candidate.liveAvailabilitySource == null)
                Text("빈자리는 현장에서 확인해 주세요.", style = Type.caption, color = c.inkMuted)
        }
        VoiceFeedback(state)
    }
}

/** 시설 데이터의 관계만 표현한다. 위치·축척·실시간 경로를 추정하지 않는다. */
@Composable
private fun PlanDiagram(result: CandidateResult, venueName: String, entranceName: String, modifier: Modifier, onEvidence: () -> Unit) {
    val c = Atd.colors
    val plan = result.candidate
    Panel(modifier, padding = 28.dp, spacing = 20.dp) {
        Text("${walkPrefix(plan)} 입구까지", style = Type.title, color = c.ink)
        Text("동선 관계도 · 축척 없음", style = Type.caption, color = c.inkMuted)
        BoxWithConstraints(Modifier.fillMaxWidth().height(330.dp).background(c.bg, RoundedCornerShape(16.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * .19f, size.height * .72f)
                    lineTo(size.width * .53f, size.height * .72f)
                    lineTo(size.width * .53f, size.height * .4f)
                    lineTo(size.width * .77f, size.height * .4f)
                }
                drawPath(path, c.route, style = Stroke(width = 6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 13.dp.toPx()))))
                drawCircle(c.primary, 25.dp.toPx(), Offset(size.width * .19f, size.height * .72f))
            }
            Column(Modifier.align(Alignment.TopEnd).padding(22.dp).width(250.dp).background(c.fill, RoundedCornerShape(14.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(venueName.substringBefore(" 소공연장"), style = Type.bodyStrong, color = c.ink)
                Text(entranceName, style = Type.title, color = c.ink)
            }
            Box(Modifier.offset(x = maxWidth * .19f - 25.dp, y = maxHeight * .72f - 25.dp).size(50.dp), contentAlignment = Alignment.Center) {
                Text(if (plan.type == CandidateType.PARKING) "P" else "↓", style = Type.title, color = c.onPrimary)
            }
            Text(spotName(plan), Modifier.align(Alignment.BottomStart).padding(22.dp), style = Type.caption, color = c.ink)
        }
        Text(walkLabel(plan), style = Type.bodyStrong, color = c.route)
        AtdButton("정보와 확인 사항", onEvidence, kind = ButtonKind.QUIET, trailing = Glyph.Chevron, height = 76.dp)
    }
}
