package app.atthedoor.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.Touch
import app.atthedoor.app.ui.theme.Type
import app.atthedoor.domain.plan.VerdictKind
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private val Corner = RoundedCornerShape(4.dp)

// ── 버튼 ─────────────────────────────────────────────

enum class ButtonKind { PRIMARY, SECONDARY, REPORT, QUIET }

/**
 * 모든 버튼. 주 버튼은 딥 네이비 채움, 보조는 흰 바탕 + 1.5dp 선, 제보(만차·막힘)는 경고 채움,
 * 조용한 버튼은 선 없는 글자 버튼이다. 모서리는 4dp.
 */
@Composable
fun AtdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = Touch.button,
    textStyle: TextStyle = Type.label,
    trailing: ImageVector? = null,
) {
    val c = Atd.colors
    val bg = when (kind) {
        ButtonKind.PRIMARY -> if (enabled) c.primary else c.inkFaint
        ButtonKind.SECONDARY -> c.surface
        ButtonKind.REPORT -> c.cautionFill
        ButtonKind.QUIET -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.PRIMARY -> c.onPrimary
        ButtonKind.SECONDARY -> if (enabled) c.ink else c.inkFaint
        ButtonKind.REPORT -> c.onCautionFill
        ButtonKind.QUIET -> c.route
    }
    val border: BorderStroke? = if (kind == ButtonKind.SECONDARY) BorderStroke(1.5.dp, c.line) else null
    Row(
        modifier
            .heightIn(min = height)
            .clip(Corner)
            .background(bg)
            .let { if (border != null) it.border(border, Corner) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text, style = textStyle, color = fg, maxLines = 2, textAlign = TextAlign.Center)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Icon(trailing, null, tint = fg, modifier = Modifier.size(30.dp))
        }
    }
}

// ── 선택 ─────────────────────────────────────────────

/** 큰 선택 타일. 선택되면 네이비 3dp 테두리와 오른쪽 위 체크. 질문 하나의 답을 고를 때 쓴다. */
@Composable
fun ChoiceTile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** 여러 개 고르기면 사각 체크박스, 하나만 고르면 원형 라디오 */
    multi: Boolean = false,
) {
    val c = Atd.colors
    val mark = if (multi) RoundedCornerShape(4.dp) else CircleShape
    Column(
        modifier
            .clip(Corner)
            .background(if (selected) c.surfaceAlt else c.surface)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) c.primary else c.line, Corner)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; role = if (multi) Role.Checkbox else Role.RadioButton }
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, tint = if (selected) c.primary else c.inkMuted, modifier = Modifier.size(56.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(mark).background(if (selected) c.primary else c.surface).border(2.dp, if (selected) c.primary else c.lineStrong, mark),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Glyph.Check, null, tint = c.onPrimary, modifier = Modifier.size(28.dp)) }
        }
        Text(title, style = Type.title, color = c.ink)
        if (subtitle != null) Text(subtitle, style = Type.body, color = c.inkMuted)
    }
}

/** 칩. 높이 76dp, 4dp 모서리, 선택 시 네이비 테두리와 굵은 글자. onClick이 없으면 표시용이다. */
@Composable
fun AtdChip(text: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = Atd.colors
    Box(
        modifier
            .heightIn(min = Touch.min)
            .clip(Corner)
            .background(if (selected) c.primarySoft else c.surface)
            .border(if (selected) 3.dp else 1.dp, if (selected) c.primary else c.lineStrong, Corner)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick).semantics { this.selected = selected } else m }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Glyph.Check, null, tint = c.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = if (selected) Type.bodyStrong else Type.body, color = if (selected) c.primary else c.ink, maxLines = 1)
        }
    }
}

// ── 표시 ─────────────────────────────────────────────

/** 판정 배지. 색과 함께 글자를 늘 붙인다. */
@Composable
fun VerdictBadge(kind: VerdictKind) {
    val c = Atd.colors
    val label = when (kind) {
        VerdictKind.RECOMMENDED -> "추천"
        VerdictKind.ELIGIBLE -> "가능"
        VerdictKind.NEEDS_CHECK -> "확인 필요"
        VerdictKind.EXCLUDED -> "추천하지 않음"
    }
    val bg = when (kind) {
        VerdictKind.RECOMMENDED -> c.successBg
        VerdictKind.NEEDS_CHECK -> c.cautionBg
        else -> c.surfaceAlt
    }
    val fg = when (kind) {
        VerdictKind.RECOMMENDED -> c.recommended
        VerdictKind.NEEDS_CHECK -> c.caution
        VerdictKind.ELIGIBLE -> c.ink
        VerdictKind.EXCLUDED -> c.inkMuted
    }
    Text(label, Modifier.clip(RoundedCornerShape(2.dp)).background(bg).padding(horizontal = 14.dp, vertical = 4.dp), color = fg, style = Type.caption.copy(fontWeight = FontWeight.Bold))
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier, large: Boolean = false) {
    val c = Atd.colors
    Text(
        "시연 데이터",
        modifier.clip(RoundedCornerShape(2.dp)).background(c.demoBg).padding(horizontal = 14.dp, vertical = 6.dp),
        color = c.demo,
        style = if (large) Type.bodyStrong else Type.caption.copy(fontWeight = FontWeight.SemiBold),
    )
}

enum class Tone { INFO, CAUTION, ERROR, SUCCESS }

/** 화면 위 알림 띠. 본문을 아래로 밀어 겹치지 않는다. */
@Composable
fun NoticeStrip(text: String, tone: Tone, modifier: Modifier = Modifier, large: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val c = Atd.colors
    val bg = when (tone) { Tone.INFO -> c.infoBg; Tone.CAUTION -> c.cautionBg; Tone.ERROR -> c.dangerBg; Tone.SUCCESS -> c.successBg }
    val fg = when (tone) { Tone.INFO -> c.info; Tone.CAUTION -> c.caution; Tone.ERROR -> c.danger; Tone.SUCCESS -> c.success }
    val icon = when (tone) { Tone.SUCCESS -> Glyph.Check; Tone.INFO -> Glyph.Info; else -> Glyph.Warning }
    Row(
        modifier.fillMaxWidth().heightIn(min = if (large) 120.dp else 72.dp).background(bg).padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 44.dp else 32.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, Modifier.weight(1f), style = if (large) Type.title else Type.bodyStrong, color = c.ink)
        if (action != null) action()
    }
}

/** 흰 평면 구역. 카드 안에 카드를 넣지 않고 1px 선으로만 나눈다. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 32.dp, spacing: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Atd.colors
    Column(modifier.background(c.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).border(1.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Atd.colors.line))
}

@Composable
fun Headline(text: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = Type.display, color = Atd.colors.ink)
        if (sub != null) Text(sub, style = Type.body, color = Atd.colors.inkMuted)
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier, valueStyle: TextStyle = Type.title) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Type.caption, color = Atd.colors.inkMuted)
        Text(value, style = valueStyle, color = Atd.colors.ink)
    }
}

/** 번호가 붙은 이동 순서 */
@Composable
fun StepList(steps: List<String>, modifier: Modifier = Modifier, style: TextStyle = Type.body) {
    val c = Atd.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (steps.isEmpty()) Text("확인된 이동 순서가 없어요", style = Type.body, color = c.inkMuted)
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(CircleShape).border(2.dp, c.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Type.bodyStrong, color = c.primary)
                }
                Spacer(Modifier.width(20.dp))
                Text(s, Modifier.padding(top = 2.dp), style = style, color = c.ink)
            }
        }
    }
}

enum class ReasonKind { OK, CHECK, BLOCK }

/** 판정 이유 한 줄. 아이콘과 색으로 종류를 구분한다. */
@Composable
fun ReasonLine(text: String, kind: ReasonKind, modifier: Modifier = Modifier) {
    val c = Atd.colors
    val icon = when (kind) { ReasonKind.OK -> Glyph.Check; ReasonKind.CHECK -> Glyph.Question; ReasonKind.BLOCK -> Glyph.Block }
    val tint = when (kind) { ReasonKind.OK -> c.success; ReasonKind.CHECK -> c.caution; ReasonKind.BLOCK -> c.inkMuted }
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(32.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = Type.body, color = c.ink)
    }
}

// ── 이미지 ───────────────────────────────────────────

/** assets/photos 아래 입구 이미지. 시연용 일러스트면 왼쪽 위에 ‘시연 일러스트 · 실제 사진 아님’을 덧씌운다(잘려도 사라지지 않게). */
@Composable
fun EntranceImage(path: String?, contentDescription: String, modifier: Modifier = Modifier, illustration: Boolean = false) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(path) {
        path?.let { p -> runCatching { context.assets.open("photos/$p").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    }
    val c = Atd.colors
    Box(modifier.clip(Corner).background(c.surfaceAlt).border(1.dp, c.line, Corner), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text("등록된 입구 사진이 없어요", style = Type.body, color = c.inkMuted, textAlign = TextAlign.Center)
        if (bitmap != null && illustration) {
            Text(
                "시연 일러스트 · 실제 사진 아님",
                Modifier.align(Alignment.TopStart).padding(16.dp).clip(RoundedCornerShape(2.dp)).background(c.demoBg).padding(horizontal = 12.dp, vertical = 4.dp),
                color = c.demo,
                style = Type.badge,
            )
        }
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(
        bitmap,
        contentDescription = null,
        modifier = modifier.semantics { contentDescription = "휴대폰 안내 QR 코드" }.background(Color.White).border(1.dp, Atd.colors.line).padding(16.dp),
    )
}

private fun qrBitmap(text: String, size: Int = 600): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ── 시트 ─────────────────────────────────────────────

/**
 * 오른쪽에서 여는 시트. 근거, 다른 계획, 조건, 주차 메모처럼 매번 볼 필요가 없는 정보를 둔다.
 * 바깥 어두운 막을 누르면 닫힌다.
 */
@Composable
fun BoxScope.SideSheet(title: String, onClose: () -> Unit, width: Dp = 760.dp, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Atd.colors
    Box(
        Modifier.matchParentSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    )
    Column(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(width).background(c.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(start = 40.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(Touch.min).clickable(onClick = onClose).semantics { contentDescription = "닫기" }, contentAlignment = Alignment.Center) {
                Icon(Glyph.Close, null, tint = c.ink, modifier = Modifier.size(36.dp))
            }
        }
        Hairline()
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
        // 아래에 내용이 더 있으면 본문 밖 별도 줄로 알려 준다. 내용 위에 겹치지 않는다.
        if (scroll.maxValue > 0 && scroll.value < scroll.maxValue) {
            Hairline()
            Row(Modifier.fillMaxWidth().height(48.dp).background(c.surfaceAlt), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("아래로 밀면 더 있어요", style = Type.caption, color = c.ink)
                Icon(androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown, null, tint = c.ink, modifier = Modifier.size(32.dp))
            }
        }
        if (footer != null) {
            Hairline()
            Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp)) { footer() }
        }
    }
}

/** 시트·목록의 한 줄 행. onClick이 있으면 오른쪽에 화살표를 붙인다. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Atd.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 96.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.bodyStrong, color = c.ink)
            if (subtitle != null) Text(subtitle, style = Type.body, color = c.inkMuted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(24.dp))
            trailing()
        }
        else if (onClick != null) Icon(Glyph.Chevron, null, tint = c.inkMuted, modifier = Modifier.size(36.dp))
    }
}
