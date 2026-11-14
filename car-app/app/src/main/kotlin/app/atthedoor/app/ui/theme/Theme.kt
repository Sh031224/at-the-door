package app.atthedoor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 차량 화면 룩앤필. 순정 인포테인먼트처럼 밝은 회색 바탕 위 흰 평면, 그림자 없이 1px 구분선,
 * 둥근 패널, 검정 고대비 글자와 중립색 주 동작, 동선 표시용 파란색을 쓴다.
 * 로고·상표·전용 서체는 쓰지 않고 시스템 서체를 쓴다.
 */

@Immutable
data class AtdColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val lineStrong: Color,
    /** 보조 버튼·세그먼트 면(순정 ‘안내 종료’ 바 회색) */
    val fill: Color,
    val fillPressed: Color,
    /** 턴바이턴 문법의 반전 블록. 주행 화면과 ‘다음 지점’에만 쓴다. */
    val inverse: Color,
    val inverseSub: Color,
    val onInverse: Color,
    val primarySoft: Color,
    val primaryPressed: Color,
    val report: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val route: Color,
    val accent: Color,
    val caution: Color,
    val cautionBg: Color,
    val cautionFill: Color,
    val onCautionFill: Color,
    val danger: Color,
    val dangerBg: Color,
    val info: Color,
    val infoBg: Color,
    val success: Color,
    val successBg: Color,
    val demo: Color,
    val demoBg: Color,
    // 판정 색. 색만으로 뜻을 전하지 않도록 배지에는 늘 글자를 함께 쓴다.
    val recommended: Color,
    val eligible: Color,
    val needsCheck: Color,
    val excluded: Color,
)

private val Light = AtdColors(
    bg = Color(0xFFF5F6F8), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF7F7F7), line = Color(0xFFE0E2E6),
    lineStrong = Color(0xFF9E9E9E), fill = Color(0xFFEDEDED), fillPressed = Color(0xFFDCDCDC),
    inverse = Color(0xFF111111), inverseSub = Color(0xFF333333), onInverse = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFE6ECF3), primaryPressed = Color(0xFF32353C), report = Color(0xFFA84300),
    ink = Color(0xFF111111), inkMuted = Color(0xFF555555), inkFaint = Color(0xFFABABAB),
    primary = Color(0xFF43464E), onPrimary = Color(0xFFFFFFFF), route = Color(0xFF2D4E75), accent = Color(0xFFF08A00),
    caution = Color(0xFF9A5B00), cautionBg = Color(0xFFFFF3DF), cautionFill = Color(0xFFF2B33D), onCautionFill = Color(0xFF111317),
    danger = Color(0xFFB3261E), dangerBg = Color(0xFFFCE8E6), info = Color(0xFF0B5CAD), infoBg = Color(0xFFE6F0FB),
    success = Color(0xFF436C61), successBg = Color(0xFFE3F3E8), demo = Color(0xFF6B4E00), demoBg = Color(0xFFFFE9A8),
    recommended = Color(0xFF0A6B45), eligible = Color(0xFF111111), needsCheck = Color(0xFF7A4600), excluded = Color(0xFF555555),
)

private val Dark = AtdColors(
    bg = Color(0xFF0B0B0C), surface = Color(0xFF161718), surfaceAlt = Color(0xFF1E1F21), line = Color(0xFF34363A),
    lineStrong = Color(0xFF5A5D62), fill = Color(0xFF26272A), fillPressed = Color(0xFF323438),
    inverse = Color(0xFF000000), inverseSub = Color(0xFF1E1F21), onInverse = Color(0xFFF2F2F2),
    primarySoft = Color(0xFF14243A), primaryPressed = Color(0xFF245A99), report = Color(0xFFFF9A5C),
    ink = Color(0xFFF3F4F6), inkMuted = Color(0xFFA3AAB3), inkFaint = Color(0xFF5A616A),
    primary = Color(0xFF2D6CB5), onPrimary = Color(0xFFFFFFFF), route = Color(0xFF6EA8FF), accent = Color(0xFFFF9F1A),
    caution = Color(0xFFFFC266), cautionBg = Color(0xFF3A2A10), cautionFill = Color(0xFFF2B33D), onCautionFill = Color(0xFF111317),
    danger = Color(0xFFFF8A80), dangerBg = Color(0xFF3D1512), info = Color(0xFF8CC1FF), infoBg = Color(0xFF12263D),
    success = Color(0xFF6FD39A), successBg = Color(0xFF12301E), demo = Color(0xFFFFE08A), demoBg = Color(0xFF3A3010),
    recommended = Color(0xFF3FBF85), eligible = Color(0xFFF2F2F2), needsCheck = Color(0xFFF5B54A), excluded = Color(0xFFA9ABAF),
)

val LocalAtdColors = staticCompositionLocalOf { Light }

/** 주행 제한 중이면 움직임을 모두 끈다. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Atd {
    val colors: AtdColors @Composable get() = LocalAtdColors.current
}

/** 터치 크기(dp). 차량 화면의 큰 터치 영역 권고에 맞춘다. */
object Touch {
    val min = 88.dp
    val button = 96.dp
    val segment = 112.dp
    val drive = 136.dp
}

/** 그리드(dp). 앱 창 1682×1210 기준: 상단바 88, 알림 72(있을 때), 하단 액션 136. */
object Grid {
    val margin = 48.dp
    val gutter = 24.dp
    val topBar = 96.dp
    val notice = 80.dp
    val actionBar = 112.dp
}

private val phrase = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

private fun t(size: Int, line: Int, weight: FontWeight) = TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, lineBreak = phrase)

/** 글자 단계. 최소 22sp, 주행 화면에서는 30sp 미만을 쓰지 않는다. */
object Type {
    val drive = t(64, 76, FontWeight.Bold)
    val driveSub = t(40, 52, FontWeight.Medium)
    val hero = t(72, 84, FontWeight.Bold)
    val display = t(48, 60, FontWeight.Bold)
    val headline = t(40, 52, FontWeight.Bold)
    val title = t(32, 42, FontWeight.SemiBold)
    val body = t(26, 36, FontWeight.Normal)
    val bodyStrong = t(26, 36, FontWeight.SemiBold)
    val label = t(28, 34, FontWeight.Medium)
    val labelPrimary = t(30, 36, FontWeight.Bold)
    val caption = t(24, 32, FontWeight.Normal)
    val badge = t(22, 28, FontWeight.Bold)
}

private val material = Typography(
    displaySmall = Type.display, headlineLarge = Type.headline, headlineMedium = Type.headline, headlineSmall = Type.title,
    titleLarge = Type.title, titleMedium = Type.bodyStrong, bodyLarge = Type.body, bodyMedium = Type.body, bodySmall = Type.caption,
    labelLarge = Type.label, labelMedium = Type.caption,
)

@Composable
fun AtTheDoorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) Dark else Light
    val scheme = if (darkTheme) {
        darkColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bg, surface = c.surface, onSurface = c.ink, onBackground = c.ink, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.inkMuted, outline = c.line, error = c.danger)
    } else {
        lightColorScheme(primary = c.primary, onPrimary = c.onPrimary, background = c.bg, surface = c.surface, onSurface = c.ink, onBackground = c.ink, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.inkMuted, outline = c.line, error = c.danger)
    }
    CompositionLocalProvider(LocalAtdColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = material,
            shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp)),
            content = content,
        )
    }
}
