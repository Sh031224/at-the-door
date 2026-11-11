package app.atthedoor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.DemoControls
import app.atthedoor.app.NaviStatus
import app.atthedoor.app.TripUiState
import app.atthedoor.app.ui.components.DangerButton
import app.atthedoor.app.ui.components.NoticeBanner
import app.atthedoor.app.ui.components.NoticeTone
import app.atthedoor.app.ui.components.ToggleTile
import app.atthedoor.domain.port.GearState
import app.atthedoor.domain.port.NaviResult
import app.atthedoor.domain.trip.TripPhase
import app.atthedoor.platform.fake.FakeNaviScenario

/**
 * 주행 중 최소 화면: 현재 계획, 다음 지점, 내비 연결 상태 세 가지만.
 * 비교·사진·QR·설정은 띄우지 않는다. 접근 중에는 ‘주차장이 꽉 찼어요’ 한 번 누르기만 허용하고 결과는 음성으로 알린다.
 */
@Composable
fun DrivingPanel(state: TripUiState, onParkingFull: () -> Unit) {
    val plan = state.selected
    val phase = state.trip?.phase
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        state.replanned?.let { NoticeBanner(it, tone = NoticeTone.SUCCESS) }
        if (state.venue?.demo == true) app.atthedoor.app.ui.components.Pill("시연 데이터")
        Text("현재 계획", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(plan?.name ?: "정해진 계획이 없어요", style = MaterialTheme.typography.displaySmall)
        plan?.walk?.distanceM?.takeIf { it.known }?.let { Text("주차 후 입구까지 약 ${it.value}m", style = MaterialTheme.typography.headlineSmall) }
        plan?.let {
            Text("다음 지점", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(it.accessPoint.approach, style = MaterialTheme.typography.displaySmall)
        }
        Text(
            when {
                phase == TripPhase.APPROACHING -> "내비게이션 안내 중"
                phase == TripPhase.NAVI_REQUESTED && state.navi == NaviStatus.SEARCHING -> "내비게이션이 경로를 찾는 중"
                phase == TripPhase.NAVI_REQUESTED -> "내비게이션 연결 중"
                else -> "자세한 안내는 정차한 뒤 보여 드려요"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (phase == TripPhase.APPROACHING) {
            ReportButton("만차예요 · 다른 곳 찾기", onParkingFull, Modifier.fillMaxWidth(), large = true)
        }
    }
}

/** 모의 응답 모드 전용. 실제 SDK 모드에서는 에뮬레이터 Virtual Console로 같은 신호를 바꾼다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoControlsPanel(demo: DemoControls) {
    val driving by demo.driving.restricted.collectAsState()
    val gear by demo.vehicle.gear.collectAsState()
    val belt by demo.vehicle.frontPassengerBuckled.collectAsState()
    val door by demo.vehicle.frontPassengerDoorOpen.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("시연 제어", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleTile("주행 중", driving, {
                val next = !driving
                demo.driving.set(next)
                demo.vehicle.gear.value = if (next) GearState.DRIVE else GearState.PARK
            })
            ToggleTile("기어 P", gear == GearState.PARK, { demo.vehicle.gear.value = if (gear == GearState.PARK) GearState.DRIVE else GearState.PARK })
            ToggleTile("동승석 벨트 풀기", belt == false, { demo.vehicle.frontPassengerBuckled.value = belt == false })
            ToggleTile("동승석 문 열기", door == true, { demo.vehicle.frontPassengerDoorOpen.value = door != true })
        }
        Text("내비 응답", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FakeNaviScenario.entries.forEach { s ->
                ToggleTile(
                    when (s) {
                        FakeNaviScenario.SUCCESS -> "성공"
                        FakeNaviScenario.FAIL -> "실패"
                        FakeNaviScenario.TIMEOUT -> "무응답"
                    },
                    demo.navi.scenario == s,
                    { demo.navi.scenario = s },
                )
            }
            ToggleTile("내비에서 안내 취소", false, { demo.navi.simulate(NaviResult.Cancelled) })
        }
    }
}
