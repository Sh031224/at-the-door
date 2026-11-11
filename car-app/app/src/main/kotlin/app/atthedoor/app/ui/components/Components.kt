package app.atthedoor.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.atthedoor.app.ui.theme.LocalVerdictColors
import app.atthedoor.app.ui.theme.TouchTarget
import app.atthedoor.domain.plan.VerdictKind
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTarget),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTarget),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = TouchTarget),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** 큰 토글 칩. 차량 화면에서는 작은 체크박스 대신 넓은 선택 영역을 쓴다. */
@Composable
fun ToggleTile(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, description: String? = null, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        modifier = modifier
            .heightIn(min = TouchTarget)
            .semantics { this.selected = selected; role = Role.Checkbox },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) cs.primaryContainer else cs.surface,
        border = BorderStroke(if (selected) 3.dp else 2.dp, if (selected) cs.primary else cs.outline),
    ) {
        if (compact) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) cs.onPrimaryContainer else cs.onSurface, maxLines = 1, softWrap = false)
            }
            return@Surface
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (selected) cs.primary else cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
                if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VerdictBadge(kind: VerdictKind) {
    val v = LocalVerdictColors.current
    val (label, color) = when (kind) {
        VerdictKind.RECOMMENDED -> "추천" to v.recommended
        VerdictKind.ELIGIBLE -> "가능" to v.eligible
        VerdictKind.NEEDS_CHECK -> "확인 필요" to v.needsCheck
        VerdictKind.EXCLUDED -> "추천하지 않음" to v.excluded
    }
    Text(
        label,
        Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 14.dp, vertical = 6.dp),
        color = v.onBadge,
        style = MaterialTheme.typography.labelMedium,
    )
}

enum class NoticeTone { INFO, WARN, ERROR, SUCCESS }

@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.INFO, action: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        NoticeTone.INFO -> cs.primaryContainer to cs.onPrimaryContainer
        NoticeTone.WARN -> cs.tertiaryContainer to cs.onTertiaryContainer
        NoticeTone.ERROR -> cs.errorContainer to cs.onErrorContainer
        NoticeTone.SUCCESS -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (tone == NoticeTone.INFO || tone == NoticeTone.SUCCESS) Icons.Filled.Info else Icons.Filled.Warning, null, tint = fg, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, Modifier.weight(1f), color = fg, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            Spacer(Modifier.width(16.dp))
            action()
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() } }
}

@Composable
fun StepList(steps: List<String>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (steps.isEmpty()) Text("확인된 이동 순서가 없어요", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = cs.onPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(18.dp))
                Text(s, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal))
            }
        }
    }
}

/** assets/photos 아래 입구 이미지. 시연용 일러스트는 이미지 자체에 ‘실제 사진 아님’ 표시가 들어 있다. */
@Composable
fun EntranceImage(path: String?, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(path) {
        path?.let { p -> runCatching { context.assets.open("photos/$p").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    }
    val cs = MaterialTheme.colorScheme
    Box(modifier.aspectRatio(1.5f).clip(RoundedCornerShape(20.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text("등록된 입구 사진이 없어요", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(
        bitmap,
        contentDescription = null,
        modifier = modifier
            .semantics { contentDescription = "휴대폰 안내 QR 코드" }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, Color(0xFFDDDDDD), RoundedCornerShape(16.dp))
            .padding(12.dp),
    )
}

private fun qrBitmap(text: String, size: Int = 600): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(999.dp)).background(container).padding(horizontal = 16.dp, vertical = 6.dp),
        color = content,
        style = MaterialTheme.typography.labelMedium,
    )
}
