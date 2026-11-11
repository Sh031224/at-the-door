package app.atthedoor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.app.ui.components.ActionBar
import app.atthedoor.app.ui.components.NoticeStrip
import app.atthedoor.app.ui.components.Stage
import app.atthedoor.app.ui.components.Tone
import app.atthedoor.app.ui.components.TopBar
import app.atthedoor.app.ui.screens.DrivingScreen
import app.atthedoor.app.ui.screens.PhaseBody
import app.atthedoor.app.ui.screens.PhaseCallbacks
import app.atthedoor.app.ui.screens.Sheet
import app.atthedoor.app.ui.screens.Sheets
import app.atthedoor.app.ui.screens.phaseActions
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Grid
import app.atthedoor.app.ui.theme.LocalReduceMotion
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase

/**
 * 앱 뼈대: 상단바(4단계) → 알림 띠 → 본문(질문 하나) → 하단 세그먼트(주 동작은 왼쪽).
 * 주행 제한 중에는 여행이 어느 단계든 반전 블록 최소 화면만 보여 준다.
 */
@Composable
fun TripScreen(vm: TripViewModel, onSwitchMode: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val restricted by vm.drivingRestricted.collectAsStateWithLifecycle()
    val setupBlocked by vm.setupBlocked.collectAsStateWithLifecycle()
    val cue by vm.handoffCue.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    val c = Atd.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.startListening() else vm.micDenied() }
    val callbacks = PhaseCallbacks(
        speak = {
            // 마이크 권한은 ‘말로 정하기’를 처음 누를 때 묻는다. 거절해도 버튼으로 끝까지 진행한다.
            val granted = vm.isFake || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) vm.startListening() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        },
        openNavi = {
            // 차량 내비 앱을 앞으로 가져온다. 경로는 이미 문앞이 보낸 상태다.
            context.packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app")?.let {
                runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        },
        cue = cue,
        dial = { number ->
            // 전화 앱(차량에서는 블루투스 연결 휴대폰)을 번호가 입력된 상태로 연다. 앱이 직접 걸지 않는다.
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )

    // 단계가 바뀌면 열린 시트를 닫는다. 주행이 시작되면 시트도 함께 숨긴다.
    LaunchedEffect(state.trip?.phase, restricted) { if (restricted) sheet = null }
    LaunchedEffect(state.trip?.phase) { sheet = null }

    CompositionLocalProvider(LocalReduceMotion provides restricted) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize()) {
                if (vm.isFake) FakeBanner()
                if (restricted) {
                    DrivingScreen(state, onParkingFull = vm::reportParkingFull)
                } else {
                    TopBar(
                        venueName = state.venue?.name,
                        stage = stageOf(state),
                        demo = state.venue?.demo == true,
                        onMore = { sheet = Sheet.MORE },
                        onLogoLongPress = { sheet = Sheet.DEMO },
                        finished = state.trip?.phase == TripPhase.COMPLETED,
                    )
                    Notices(state, vm::undoHint)
                    val scroll = rememberScrollState()
                    LaunchedEffect(state.trip?.phase, state.trip?.conditions, state.replanned) { scroll.scrollTo(0) }
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = Grid.margin, vertical = 36.dp)) {
                        if (!state.loading) PhaseBody(state, vm, setupBlocked, cue) { sheet = it }
                    }
                    if (!state.loading) phaseActions(state, vm, callbacks) { sheet = it }?.let { (primary, rest) -> ActionBar(primary, *rest.toTypedArray()) }
                }
            }
            if (!restricted) sheet?.let { s -> Sheets(s, state, vm, onClose = { sheet = null }, open = { sheet = it }, onSwitchMode = onSwitchMode) }
        }
    }
}

@Composable
private fun Notices(state: TripUiState, onUndoHint: () -> Unit) {
    val notice = state.trip?.notice?.takeIf { !(it == TripMachine.NOTICE_ALTERNATIVE && state.replanned != null) }
    // 막힘·만차로 길이 바뀐 것은 ‘성공’이 아니라 알림이다. 초록은 사용자가 고른 조건이 반영됐을 때만 쓴다.
    if (notice != null) NoticeStrip(notice, if (notice == TripMachine.NOTICE_ALTERNATIVE) Tone.INFO else Tone.CAUTION)
    state.replanned?.let {
        NoticeStrip(it, Tone.INFO, action = if (state.hintUndo != null) ({
            app.atthedoor.app.ui.components.AtdButton("되돌리기", onUndoHint, kind = app.atthedoor.app.ui.components.ButtonKind.QUIET, height = 72.dp)
        }) else null)
    }
}

private fun stageOf(state: TripUiState): Stage = when (state.trip?.phase) {
    // 주차한 뒤 대안을 못 찾았으면 사람은 여전히 입구 근처에 있다. 단계 표시를 ‘준비’로 되돌리지 않는다.
    TripPhase.NEEDS_CHECK -> if (state.trip.resumePhase?.onFoot == true) Stage.WALK else Stage.PREPARE
    null, TripPhase.PLANNING, TripPhase.CHOOSING, TripPhase.NAVI_REQUESTED -> Stage.PREPARE
    TripPhase.APPROACHING -> Stage.DRIVE
    TripPhase.PARKED, TripPhase.WALKING, TripPhase.VISITING, TripPhase.REPLANNING -> Stage.WALK
    TripPhase.RETURNING, TripPhase.COMPLETED, TripPhase.CANCELLED -> Stage.BACK
}

@Composable
private fun FakeBanner() {
    Text(
        "모의 응답 모드 · 차량·내비 SDK 대신 가짜 구현을 쓰는 중이에요",
        Modifier.fillMaxWidth().background(Color(0xFF6B4E00)).padding(horizontal = 48.dp, vertical = 10.dp),
        color = Color.White,
        style = Type.bodyStrong,
    )
}
