package app.atthedoor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atthedoor.app.DemoControls
import app.atthedoor.app.NaviStatus
import app.atthedoor.app.TripUiState
import app.atthedoor.app.ui.components.AtdButton
import app.atthedoor.app.ui.components.AtdChip
import app.atthedoor.app.ui.components.ButtonKind
import app.atthedoor.app.ui.components.DemoBadge
import app.atthedoor.app.ui.components.Glyph
import app.atthedoor.app.ui.components.InverseBlock
import app.atthedoor.app.ui.components.NoticeStrip
import app.atthedoor.app.ui.components.Tone
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Touch
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.port.GearState
import app.atthedoor.domain.port.NaviResult
import app.atthedoor.domain.trip.TripPhase
import app.atthedoor.platform.fake.FakeNaviScenario

/**
 * 주행 중 최소 화면. 순정 턴바이턴처럼 검정 반전 블록에 현재 계획과 다음 지점, 그 아래 내비 상태.
 * 사진·비교·QR·설정은 띄우지 않고, 접근 중에는 ‘만차예요’ 한 번 누르기만 허용한다. 움직임은 없다.
 */
@Composable
fun DrivingScreen(state: TripUiState, onParkingFull: () -> Unit) {
    val c = Atd.colors
    val plan = state.selected
    val phase = state.trip?.phase
    Column(Modifier.fillMaxSize().background(c.bg)) {
        state.replanned?.let { NoticeStrip(it, Tone.INFO, large = true) }
        Column(Modifier.weight(1f).padding(horizontal = 48.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("문앞 · ${drivingStage(phase)}", style = Type.title, color = c.inkMuted)
                Spacer(Modifier.width(20.dp))
                if (state.venue?.demo == true) DemoBadge(large = true)
            }
            when {
                plan == null || phase == TripPhase.PLANNING || phase == TripPhase.CHOOSING ->
                    Text("정차한 뒤 계획을 이어서 정해요", style = Type.drive, color = c.ink)
                phase in setOf(TripPhase.PARKED, TripPhase.WALKING, TripPhase.VISITING, TripPhase.RETURNING) -> {
                    InverseBlock(Modifier.fillMaxWidth()) {
                        Text("지금 계획", style = Type.title, color = c.onInverse.copy(alpha = 0.8f))
                        Text(plan.name, style = Type.drive, color = c.onInverse)
                    }
                    Text("주차 위치 · ${state.trip?.parkingNote ?: "메모 없음"}", style = Type.driveSub, color = c.ink)
                }
                else -> {
                    // 턴바이턴처럼 지금 향하는 곳을 가장 크게, 계획 이름은 그 아래에 둔다.
                    InverseBlock(Modifier.fillMaxWidth(), sub = {
                        Column {
                            state.route?.label?.takeIf { phase == TripPhase.APPROACHING }?.let { Text("남은 길 $it", style = Type.driveSub.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.onInverse) }
                            Text(plan.name, style = Type.driveSub, color = c.onInverse)
                            plan.walk.distanceM.takeIf { it.known }?.let { Text("${walkPrefix(plan)} 입구까지 약 ${it.value}m", style = Type.title, color = c.onInverse.copy(alpha = 0.85f)) }
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Glyph.Navigate, null, tint = c.onInverse, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.width(24.dp))
                            Column {
                                Text("다음 지점", style = Type.title, color = c.onInverse.copy(alpha = 0.8f))
                                Text(shortPoint(plan.accessPoint.approach, state.venue), Modifier.padding(vertical = 12.dp), style = Type.hero.copy(fontSize = 92.sp, lineHeight = 108.sp), color = c.onInverse)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).background(if (phase == TripPhase.APPROACHING) c.success else c.caution))
                        Spacer(Modifier.width(18.dp))
                        Text(
                            when {
                                phase == TripPhase.APPROACHING -> "내비게이션 안내 중"
                                state.navi == NaviStatus.SEARCHING -> "내비가 경로를 찾는 중"
                                else -> "내비게이션 연결 중"
                            },
                            style = Type.driveSub,
                            color = c.ink,
                        )
                    }
                }
            }
        }
        // 만차 제보는 내비가 도착을 알린 뒤에만 연다. 목적지에서 먼 주행 중에는 누를 거리를 두지 않는다.
        if (phase == TripPhase.APPROACHING && !state.naviArrived) {
            Text("만차면 도착한 뒤 알려 주세요", Modifier.padding(horizontal = 48.dp, vertical = 32.dp), style = Type.driveSub, color = c.inkMuted)
        }
        if (phase == TripPhase.APPROACHING && state.naviArrived) {
            AtdButton(
                "만차예요 · 다른 곳 찾기",
                onParkingFull,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.SECONDARY,
                icon = Glyph.Warning,
                height = Touch.segment,
                textStyle = Type.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
        }
    }
}

/** 상단 4단계와 같은 이름으로 주행 화면 머리글을 만든다. */
private fun drivingStage(phase: TripPhase?): String = when (phase) {
    null, TripPhase.PLANNING, TripPhase.CHOOSING, TripPhase.NAVI_REQUESTED, TripPhase.NEEDS_CHECK -> "준비"
    TripPhase.APPROACHING -> "가는 중"
    TripPhase.PARKED, TripPhase.WALKING, TripPhase.VISITING, TripPhase.REPLANNING -> "입구까지"
    TripPhase.RETURNING, TripPhase.COMPLETED, TripPhase.CANCELLED -> "돌아오기"
}

/** 모의 응답 모드 전용. 실제 SDK 모드에서는 에뮬레이터 Virtual Console로 같은 신호를 바꾼다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoControls(demo: DemoControls) {
    val driving by demo.driving.restricted.collectAsState()
    val gear by demo.vehicle.gear.collectAsState()
    val belt by demo.vehicle.frontPassengerBuckled.collectAsState()
    val door by demo.vehicle.frontPassengerDoorOpen.collectAsState()
    Text("시연 제어", style = Type.title, color = Atd.colors.ink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AtdChip("주행 중", driving, {
            val next = !driving
            demo.driving.set(next)
            demo.vehicle.gear.value = if (next) GearState.DRIVE else GearState.PARK
        })
        AtdChip("기어 P", gear == GearState.PARK, { demo.vehicle.gear.value = if (gear == GearState.PARK) GearState.DRIVE else GearState.PARK })
        AtdChip("동승석 벨트 풀기", belt == false, { demo.vehicle.frontPassengerBuckled.value = belt == false })
        AtdChip("동승석 문 열기", door == true, { demo.vehicle.frontPassengerDoorOpen.value = door != true })
    }
    Text("내비 응답", style = Type.title, color = Atd.colors.ink)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FakeNaviScenario.entries.forEach { s ->
            AtdChip(
                when (s) { FakeNaviScenario.SUCCESS -> "성공"; FakeNaviScenario.FAIL -> "실패"; FakeNaviScenario.TIMEOUT -> "무응답" },
                demo.navi.scenario == s,
                { demo.navi.scenario = s },
            )
        }
        AtdChip("내비에서 안내 취소", false, { demo.navi.simulate(NaviResult.Cancelled) })
    }
}
