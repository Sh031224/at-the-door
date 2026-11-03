package app.atthedoor.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.atthedoor.app.TripUiState
import app.atthedoor.app.TripViewModel
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.plan.CandidateResult
import app.atthedoor.domain.plan.VerdictKind
import app.atthedoor.domain.trip.TripPhase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 첫 구현 단위의 단일 화면. 여행 단계(TripPhase)와 주행 제한 상태에 따라 내용을 바꾼다.
 * 주행 제한 중에는 어떤 단계든 최소 화면만 보여 준다(여행 상태와 UI 제한을 분리).
 */
@Composable
fun TripScreen(vm: TripViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val restricted by vm.drivingRestricted.collectAsStateWithLifecycle()
    val setupBlocked by vm.setupBlocked.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (vm.isFake) FakeBanner()
            Header(state)
            state.trip?.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                restricted -> DrivingPanel(state)
                else -> PhaseContent(vm, state, setupBlocked)
            }
        }
    }
}

@Composable
private fun FakeBanner() {
    Text(
        "모의 응답 모드: 차량·내비 SDK 대신 가짜 구현을 사용 중",
        Modifier.fillMaxWidth().background(Color(0xFF8A6D00)).padding(8.dp),
        color = Color.White,
    )
}

@Composable
private fun Header(state: TripUiState) {
    Column {
        Text(state.venue?.name ?: "시설 데이터를 불러오는 중", fontSize = 28.sp)
        if (state.venue?.demo == true) Text("데모 데이터 · 실제 시설 검증 결과가 아님", color = Color.Gray)
    }
}

/** 주행 중 최소 화면: 현재 계획, 다음 지점, 내비 연결 상태 */
@Composable
private fun DrivingPanel(state: TripUiState) {
    val plan = state.trip?.selectedCandidateId?.let { state.venue?.candidate(it) }
    Text(plan?.name ?: "계획 없음", fontSize = 32.sp)
    plan?.let { Text("다음 지점: ${it.accessPoint.approach}", fontSize = 24.sp) }
    Text(
        when (state.trip?.phase) {
            TripPhase.APPROACHING -> "내비게이션 안내 중"
            TripPhase.NAVI_REQUESTED -> "내비게이션 연결 중"
            else -> "주차 후 자세한 안내를 볼 수 있어요"
        },
        fontSize = 22.sp,
    )
}

@Composable
private fun PhaseContent(vm: TripViewModel, state: TripUiState, setupBlocked: Boolean) {
    when (state.trip?.phase) {
        null, TripPhase.PLANNING -> ConditionsPanel(state.conditions, vm::setConditions, vm::confirmConditions)
        TripPhase.CHOOSING -> PlanComparison(vm, state)
        TripPhase.NAVI_REQUESTED -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(if (state.naviSearching) "경로를 찾는 중이에요" else "내비게이션에 목적지를 보내는 중이에요")
        }
        TripPhase.APPROACHING -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("주차한 뒤 눌러 주세요")
            Button(onClick = { vm.confirmParking(null) }) { Text("주차했어요") }
            OutlinedButton(onClick = { vm.reportUnavailable() }) { Text("이 주차 구역을 이용할 수 없어요") }
        }
        TripPhase.PARKED, TripPhase.WALKING -> WalkHandoff(vm, state, setupBlocked)
        TripPhase.VISITING -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("입구에 도착했어요. 방문을 마치면 눌러 주세요.")
            Button(onClick = vm::startReturn) { Text("차로 돌아가기") }
        }
        TripPhase.RETURNING -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("주차 위치: ${state.trip.parkingNote ?: "메모 없음"}")
            Button(onClick = vm::backInCar) { Text("차에 탔어요") }
        }
        TripPhase.REPLANNING -> Text("같은 조건으로 다른 계획을 찾는 중이에요")
        TripPhase.NEEDS_CHECK -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("확인된 대안이 없어요.", fontSize = 22.sp)
            state.venue?.contact?.let { Text("시설 연락처: $it") }
            OutlinedButton(onClick = vm::startOver) { Text("다른 목적지로 다시 계획") }
        }
        TripPhase.COMPLETED, TripPhase.CANCELLED -> Button(onClick = vm::startOver) { Text("새 여행 계획") }
    }
}

@Composable
private fun ConditionsPanel(
    c: TravelConditions,
    onChange: (TravelConditions) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("이동 조건을 확인해 주세요", fontSize = 22.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(c.stepFreeRequired, { onChange(c.copy(stepFreeRequired = !c.stepFreeRequired)) }, { Text("계단 없는 동선") })
            FilterChip(c.preferShortWalk, { onChange(c.copy(preferShortWalk = !c.preferShortWalk)) }, { Text("보행 거리 짧게") })
        }
        Text("차에서 내려 입구까지 함께 이동해야 하나요?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(c.companionRequired, { onChange(c.copy(companionRequired = !c.companionRequired)) }, { Text("입구까지 동행 필요") })
            FilterChip(c.otherGuardian, { onChange(c.copy(otherGuardian = !c.otherGuardian)) }, { Text("다른 보호자 있음") })
        }
        Button(onClick = onConfirm) { Text("이 조건으로 계획 보기") }
    }
}

@Composable
private fun PlanComparison(vm: TripViewModel, state: TripUiState) {
    val results = state.evaluation?.results.orEmpty()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(results, key = { it.candidate.id }) { r -> CandidateCard(r) { vm.choosePlan(r.candidate) } }
    }
}

@Composable
private fun CandidateCard(r: CandidateResult, onChoose: () -> Unit) {
    val label = when (r.verdict.kind) {
        VerdictKind.RECOMMENDED -> "추천"
        VerdictKind.ELIGIBLE -> "가능"
        VerdictKind.NEEDS_CHECK -> "확인 필요"
        VerdictKind.EXCLUDED -> "추천하지 않음"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("[$label] ${r.candidate.name}", fontSize = 20.sp)
            r.verdict.reasons.forEach { Text("· $it") }
            if (r.verdict.kind == VerdictKind.RECOMMENDED || r.verdict.kind == VerdictKind.ELIGIBLE) {
                Button(onClick = onChoose) { Text("이 계획으로 안내") }
            }
        }
    }
}

/** 주차 후 휴대폰 이어보기. QR은 주행 제한과 NO_SETUP이 모두 풀렸을 때만 그린다. */
@Composable
private fun WalkHandoff(vm: TripViewModel, state: TripUiState, setupBlocked: Boolean) {
    val venue = state.venue ?: return
    val plan = state.trip?.selectedCandidateId?.let(venue::candidate) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${venue.entrance(plan.walk.toEntranceId)?.name ?: "입구"}까지 이동 순서", fontSize = 22.sp)
        plan.walk.steps.forEachIndexed { i, s -> Text("${i + 1}. $s") }
        when {
            setupBlocked -> Unit
            state.shareLink != null -> {
                Text("휴대폰 카메라로 QR을 열면 안내를 이어 볼 수 있어요")
                val qr = remember(state.shareLink.url) { qrBitmap(state.shareLink.url) }
                Image(qr, contentDescription = "휴대폰 안내 QR", modifier = Modifier.size(220.dp))
            }
            state.shareFailed -> Text("휴대폰 연결을 만들지 못했어요. 이 화면의 안내로 이동해 주세요.")
            else -> Text("휴대폰 안내 링크를 만드는 중이에요")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.trip.phase == TripPhase.PARKED) Button(onClick = vm::startWalking) { Text("걷기 시작") }
            if (state.trip.phase == TripPhase.WALKING) Button(onClick = vm::arrivedAtEntrance) { Text("입구에 도착했어요") }
            OutlinedButton(onClick = { vm.reportUnavailable() }) { Text("이 입구를 이용할 수 없어요") }
        }
    }
}

private fun qrBitmap(text: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
