package app.atthedoor.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Grid
import app.atthedoor.app.ui.theme.Touch
import app.atthedoor.app.ui.theme.Type

/** 사용자 말로 된 4단계. 시스템 단계(내비 연결, 재계획)는 따로 세지 않는다. */
enum class Stage(val label: String) { PREPARE("준비"), DRIVE("가는 중"), WALK("입구까지"), BACK("돌아오기") }

/** 상단바: 앱 이름·현재 단계·시연 배지·더보기. 목적지는 본문에서 확인한다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopBar(venueName: String?, stage: Stage, demo: Boolean, onMore: () -> Unit, onLogoLongPress: () -> Unit, finished: Boolean = false) {
    val c = Atd.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Row(Modifier.fillMaxWidth().height(Grid.topBar).padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "문앞",
                Modifier.combinedClickable(onClick = {}, onLongClick = onLogoLongPress).padding(end = 16.dp),
                style = Type.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = c.ink,
            )
            Text(if (finished) "외출 완료" else if (stage == Stage.PREPARE) "출발 전" else stage.label, style = Type.body, color = c.inkMuted)
            Spacer(Modifier.weight(1f))
            if (demo) DemoBadge()
            Box(Modifier.size(Touch.min).clickable(onClick = onMore).semantics { contentDescription = "더보기" }, contentAlignment = Alignment.Center) {
                Icon(Glyph.More, null, tint = c.ink, modifier = Modifier.size(40.dp))
            }
        }
        Hairline()
    }
}

enum class SegmentKind { PRIMARY, SECONDARY, REPORT }

data class Segment(
    val text: String,
    val onClick: () -> Unit,
    val kind: SegmentKind = SegmentKind.SECONDARY,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    /** 진행 중 표시. 주차 중에만 쓰므로 움직임이 허용된다. */
    val busy: Boolean = false,
)

/**
 * 하단 액션 영역. 주 동작은 왼쪽의 넓은 버튼, 보조 동작은 오른쪽에 둔다.
 * 세그먼트는 최대 3개. 보조가 없으면 주 동작이 전체 폭을 쓴다.
 */
@Composable
fun ActionBar(primary: Segment, vararg secondaries: Segment) {
    val c = Atd.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = 16.dp).height(Grid.actionBar), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SegmentCell(primary, Modifier.weight(if (secondaries.isEmpty()) 1f else 3f))
            if (secondaries.isNotEmpty()) {
                Row(Modifier.weight(if (secondaries.size > 1) 2f else 1f).fillMaxHeight()) {
                    secondaries.forEach { s ->
                        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                        SegmentCell(s, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentCell(s: Segment, modifier: Modifier) {
    val c = Atd.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        s.kind == SegmentKind.PRIMARY && s.busy -> c.primary.copy(alpha = 0.75f)
        s.kind == SegmentKind.PRIMARY && !s.enabled -> c.fill
        s.kind == SegmentKind.PRIMARY -> if (pressed) c.primaryPressed else c.primary
        else -> if (pressed) c.fillPressed else c.fill
    }
    val fg = when {
        s.busy -> c.onPrimary
        !s.enabled -> c.inkFaint
        s.kind == SegmentKind.PRIMARY -> c.onPrimary
        s.kind == SegmentKind.REPORT -> c.report
        else -> c.ink
    }
    Row(
        modifier.fillMaxHeight().background(bg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(interactionSource = source, indication = null, enabled = s.enabled, role = Role.Button, onClick = s.onClick)
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(40.dp), color = c.onPrimary, strokeWidth = 4.dp)
            Spacer(Modifier.width(16.dp))
        } else if (s.icon != null) {
            Icon(s.icon, null, tint = fg, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(s.text, style = if (s.kind == SegmentKind.PRIMARY) Type.labelPrimary else Type.label, color = fg, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** 턴바이턴 문법의 검정 반전 블록. 주행 화면과 접근 중 ‘다음 지점’에만 쓴다. */
@Composable
fun InverseBlock(modifier: Modifier = Modifier, sub: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Atd.colors
    Column(modifier.border(1.dp, c.line)) {
        Column(Modifier.fillMaxWidth().background(c.inverse).padding(horizontal = 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        if (sub != null) Box(Modifier.fillMaxWidth().background(c.inverseSub).padding(horizontal = 40.dp, vertical = 24.dp)) { sub() }
    }
}

/** 차량 접근 → 주차·승하차 → 입구, 세 지점을 잇는 경로 띠. 지도 대신 판단에 필요한 구간만 보여 준다. */
@Composable
fun RouteStrip(approach: String, spot: String, spotIsDropoff: Boolean, entrance: String, walk: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = Atd.colors
    val nodes = listOf(Triple(Glyph.Car, "차로 가는 곳", approach), Triple(if (spotIsDropoff) Glyph.Person else Glyph.Parking, if (spotIsDropoff) "내리는 곳" else "세우는 곳", spot), Triple(Glyph.Door, "들어갈 입구", entrance))
    Row(modifier.fillMaxWidth().semantics { contentDescription = nodes.joinToString(", 다음 ") { "${it.second} ${it.third}" } + (walk?.let { ", 걷는 길 $it" } ?: "") }, verticalAlignment = Alignment.Top) {
        nodes.forEachIndexed { i, (icon, label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 48.dp else 60.dp).background(c.surfaceAlt).border(1.dp, c.line), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = c.ink, modifier = Modifier.size(if (compact) 30.dp else 38.dp))
                    }
                    if (i < nodes.lastIndex) {
                        Box(Modifier.weight(1f).height(2.dp).background(if (i == 1) c.route else c.lineStrong))
                    }
                }
                Text(label, style = Type.caption, color = c.inkMuted)
                Text(value, style = if (compact) Type.body else Type.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 16.dp))
                if (i == 1 && walk != null) Text("걷는 길 $walk", style = Type.caption, color = c.route)
            }
        }
    }
}
