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
        state.replanned?.let { NoticeStrip(it, Tone.SUCCESS, large = true) }
        Column(Modifier.weight(1f).padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (state.venue?.demo == true) DemoBadge(large = true)
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
                    InverseBlock(Modifier.fillMaxWidth(), sub = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Glyph.Navigate, null, tint = c.onInverse, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text("다음 지점", style = Type.title, color = c.onInverse.copy(alpha = 0.8f))
                                Text(plan.accessPoint.approach, style = Type.headline, color = c.onInverse)
                            }
                        }
                    }) {
                        Text(plan.name, style = Type.drive, color = c.onInverse)
                        plan.walk.distanceM.takeIf { it.known }?.let { Text("주차 후 입구까지 약 ${it.value}m", style = Type.title, color = c.onInverse.copy(alpha = 0.8f)) }
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
        if (phase == TripPhase.APPROACHING) {
            AtdButton(
                "만차예요 · 다른 곳 찾기",
                onParkingFull,
                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
                kind = ButtonKind.REPORT,
                icon = Glyph.Warning,
                height = Touch.drive,
                textStyle = Type.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
        }
    }
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
