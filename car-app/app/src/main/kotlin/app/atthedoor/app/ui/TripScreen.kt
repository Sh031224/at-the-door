package app.atthedoor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.NoticeBanner
import app.atthedoor.app.ui.components.NoticeTone
import app.atthedoor.app.ui.components.Pill
import app.atthedoor.app.ui.screens.ApproachScreen
import app.atthedoor.app.ui.screens.ChoosingScreen
import app.atthedoor.app.ui.screens.DemoControlsPanel
import app.atthedoor.app.ui.screens.DoneScreen
import app.atthedoor.app.ui.screens.DrivingPanel
import app.atthedoor.app.ui.screens.HandoffScreen
import app.atthedoor.app.ui.screens.NaviRequestScreen
import app.atthedoor.app.ui.screens.NeedsCheckScreen
import app.atthedoor.app.ui.screens.PhaseActions
import app.atthedoor.app.ui.screens.PlanningActionBar
import app.atthedoor.app.ui.screens.hasPhaseActions
import app.atthedoor.app.ui.screens.PlanningScreen
import app.atthedoor.app.ui.screens.ReturnScreen
import app.atthedoor.app.ui.screens.VisitScreen
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase

/**
 * 앱의 뼈대. 왼쪽에는 여행 진행 단계, 오른쪽에는 단계별 화면을 둔다.
 * 주행 제한 중에는 여행이 어느 단계든 최소 화면(현재 계획, 다음 지점, 내비 상태)만 보여 준다.
 */
@Composable
fun TripScreen(vm: TripViewModel, onSwitchMode: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val restricted by vm.drivingRestricted.collectAsStateWithLifecycle()
    val setupBlocked by vm.setupBlocked.collectAsStateWithLifecycle()
    val cue by vm.handoffCue.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (vm.isFake) FakeBanner()
            Row(Modifier.fillMaxSize()) {
                if (!restricted) {
                    ProgressRail(state, vm, onSwitchMode, Modifier.width(360.dp).fillMaxHeight())
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (restricted) {
                        DrivingPanel(state, onParkingFull = vm::reportParkingFull)
                    } else Column(Modifier.fillMaxSize()) {
                        // 단계나 조건이 바뀌면 맨 위부터 보여 준다. 바뀐 결과와 알림이 화면 밖에 남지 않게 한다.
                        val scroll = rememberScrollState()
                        androidx.compose.runtime.LaunchedEffect(state.trip?.phase, state.trip?.conditions, state.replanned) { scroll.scrollTo(0) }
                        val planning = state.trip?.phase == TripPhase.PLANNING
                        Column(
                            Modifier.fillMaxWidth().weight(1f, fill = true).verticalScroll(scroll).padding(horizontal = 40.dp, vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            // 재계획 결과는 이유가 담긴 replanned 문구로 보여 주므로 일반 문구는 겹쳐 띄우지 않는다.
                            state.trip?.notice?.takeIf { !(it == TripMachine.NOTICE_ALTERNATIVE && state.replanned != null) }?.let { notice ->
                                val tone = if (notice == TripMachine.NOTICE_ALTERNATIVE) NoticeTone.SUCCESS else NoticeTone.WARN
                                NoticeBanner(notice, tone = tone)
                            }
                            cue?.let { NoticeBanner(it.message, tone = NoticeTone.INFO) }
                            state.replanned?.let { NoticeBanner(it, tone = NoticeTone.SUCCESS) }
                            PhaseContent(vm, state, setupBlocked)
                            // 하단 고정 버튼에 마지막 안내문이 가려지지 않게 여백을 둔다.
                            Spacer(Modifier.height(24.dp))
                        }
                        if (!state.loading && (planning || hasPhaseActions(state.trip?.phase))) {
                            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 40.dp, vertical = 20.dp)) {
                                if (planning) PlanningActionBar(state, vm) else PhaseActions(state, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseContent(vm: TripViewModel, state: TripUiState, setupBlocked: Boolean) {
    if (state.loading) return
    when (state.trip?.phase) {
        null, TripPhase.PLANNING -> PlanningScreen(state, vm)
        TripPhase.CHOOSING -> ChoosingScreen(state, vm)
        TripPhase.NAVI_REQUESTED -> NaviRequestScreen(state, vm)
        TripPhase.APPROACHING -> ApproachScreen(state, vm)
        TripPhase.PARKED, TripPhase.WALKING -> HandoffScreen(state, vm, setupBlocked)
        TripPhase.VISITING -> VisitScreen(state, vm)
        TripPhase.RETURNING -> ReturnScreen(state, vm)
        TripPhase.REPLANNING -> Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(44.dp))
            Spacer(Modifier.width(20.dp))
            Text("같은 조건으로 다른 계획을 찾고 있어요", style = MaterialTheme.typography.headlineMedium)
        }
        TripPhase.NEEDS_CHECK -> NeedsCheckScreen(state, vm)
        TripPhase.COMPLETED, TripPhase.CANCELLED -> DoneScreen(state, vm)
    }
}

@Composable
private fun FakeBanner() {
    Text(
        "모의 응답 모드 · 차량·내비 SDK 대신 가짜 구현을 쓰는 중이에요",
        Modifier.fillMaxWidth().background(Color(0xFF7A5A00)).padding(horizontal = 24.dp, vertical = 10.dp),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
    )
}

private data class Stage(val label: String, val phases: Set<TripPhase>)

private val stages = listOf(
    Stage("목적지·조건", setOf(TripPhase.PLANNING)),
    Stage("도착 계획 비교", setOf(TripPhase.CHOOSING, TripPhase.REPLANNING, TripPhase.NEEDS_CHECK)),
    Stage("내비 연결", setOf(TripPhase.NAVI_REQUESTED)),
    Stage("차량 접근", setOf(TripPhase.APPROACHING)),
    Stage("주차·휴대폰 연결", setOf(TripPhase.PARKED)),
    Stage("입구까지 이동", setOf(TripPhase.WALKING)),
    Stage("방문", setOf(TripPhase.VISITING)),
    Stage("차로 복귀", setOf(TripPhase.RETURNING)),
    Stage("완료", setOf(TripPhase.COMPLETED, TripPhase.CANCELLED)),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgressRail(state: TripUiState, vm: TripViewModel, onSwitchMode: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val current = stages.indexOfFirst { state.trip?.phase in it.phases }.coerceAtLeast(0)
    Column(
        modifier.background(cs.surface).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 실제 SDK 시연 중에 모의 모드 전환이 눈에 띄지 않도록 로고를 길게 눌러야 묻는다.
        var askSwitch by remember { mutableStateOf(false) }
        Text(
            "문앞",
            style = MaterialTheme.typography.displaySmall,
            color = cs.primary,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { askSwitch = true }),
        )
        if (askSwitch) {
            AlertDialog(
                onDismissRequest = { askSwitch = false },
                title = { Text(if (vm.isFake) "실제 SDK 모드로 바꿀까요?" else "모의 응답 모드로 바꿀까요?", style = MaterialTheme.typography.headlineSmall) },
                text = { Text("화면을 다시 열고 진행 중인 외출은 유지해요. 모의 응답 모드에서는 화면 위에 항상 표시가 나와요.", style = MaterialTheme.typography.bodyLarge) },
                confirmButton = { TextButton(onClick = { askSwitch = false; onSwitchMode() }) { Text("바꾸기", style = MaterialTheme.typography.labelLarge) } },
                dismissButton = { TextButton(onClick = { askSwitch = false }) { Text("닫기", style = MaterialTheme.typography.labelLarge) } },
            )
        }
        Text("함께 내리는 도착 계획", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        state.venue?.let { v ->
            Text(v.name, style = MaterialTheme.typography.titleLarge)
            v.area?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
            if (v.demo) Pill("시연 데이터 · 실제 시설 검증 아님", container = cs.tertiaryContainer, content = cs.onTertiaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        stages.forEachIndexed { i, stage ->
            val done = i < current
            val active = i == current
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(
                        when {
                            active -> cs.primary
                            done -> cs.secondary
                            else -> cs.surfaceVariant
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "✓" else "${i + 1}", color = if (active || done) cs.onPrimary else cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    stage.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                    color = if (active) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        vm.demo?.let {
            DemoControlsPanel(it)
            TextButton(onClick = onSwitchMode) { Text("실제 SDK 모드로 전환", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
