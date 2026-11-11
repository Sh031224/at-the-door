package app.atthedoor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 차량 화면용 테마. Pleos 기본 화면이 밝은 테마라 밝은 테마를 기본으로 하고, 시스템이 어두우면 따른다.
 * 글자는 팔 길이 거리에서 읽히도록 크게, 터치 영역은 최소 72dp로 둔다.
 */
@Immutable
data class VerdictColors(
    val recommended: Color,
    val eligible: Color,
    val needsCheck: Color,
    val excluded: Color,
    val onBadge: Color,
)

@Immutable
data class Spacing(val xs: androidx.compose.ui.unit.Dp = 8.dp, val s: androidx.compose.ui.unit.Dp = 16.dp, val m: androidx.compose.ui.unit.Dp = 24.dp, val l: androidx.compose.ui.unit.Dp = 40.dp)

val LocalVerdictColors = staticCompositionLocalOf { lightVerdict }
val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** 최소 터치 높이. 차량 화면 가이드의 큰 터치 영역 권고에 맞춘다. */
val TouchTarget = 76.dp

private val lightVerdict = VerdictColors(
    recommended = Color(0xFF16794C),
    eligible = Color(0xFF2B5FAA),
    needsCheck = Color(0xFF9A5B00),
    excluded = Color(0xFF6B7280),
    onBadge = Color.White,
)

private val darkVerdict = VerdictColors(
    recommended = Color(0xFF4CC38A),
    eligible = Color(0xFF7FA8F0),
    needsCheck = Color(0xFFF2B35B),
    excluded = Color(0xFF9CA3AF),
    onBadge = Color(0xFF0E1116),
)

private val light = lightColorScheme(
    primary = Color(0xFF1B4FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F7),
    onPrimaryContainer = Color(0xFF0D2A57),
    secondary = Color(0xFF16794C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F0E3),
    onSecondaryContainer = Color(0xFF0A3B24),
    tertiary = Color(0xFF9A5B00),
    tertiaryContainer = Color(0xFFFCEBD2),
    onTertiaryContainer = Color(0xFF4A2B00),
    background = Color(0xFFF0F1F3),
    onBackground = Color(0xFF15181D),
    surface = Color.White,
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFE6E8EC),
    onSurfaceVariant = Color(0xFF4B5361),
    outline = Color(0xFFC3C8D0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBE2E0),
    onErrorContainer = Color(0xFF5B0F0B),
)

private val dark = darkColorScheme(
    primary = Color(0xFF8DB4F5),
    onPrimary = Color(0xFF0D2A57),
    primaryContainer = Color(0xFF1F3B69),
    onPrimaryContainer = Color(0xFFDCE6F7),
    secondary = Color(0xFF4CC38A),
    secondaryContainer = Color(0xFF124A31),
    onSecondaryContainer = Color(0xFFD8F0E3),
    tertiary = Color(0xFFF2B35B),
    tertiaryContainer = Color(0xFF4A2B00),
    onTertiaryContainer = Color(0xFFFCEBD2),
    background = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    surfaceVariant = Color(0xFF222833),
    onSurfaceVariant = Color(0xFFB4BCC8),
    outline = Color(0xFF3A4250),
    errorContainer = Color(0xFF5B0F0B),
    onErrorContainer = Color(0xFFFBE2E0),
)

/** 한국어를 어절 단위로 줄바꿈한다(API 33+). ‘주차장 / 입구’처럼 단어 중간에서 끊기지 않게 한다. */
private val phrase = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

private val base = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 23.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 21.sp, lineHeight = 29.sp),
    bodySmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
)

private val typography = with(base) {
    copy(
        displaySmall = displaySmall.copy(lineBreak = phrase),
        headlineLarge = headlineLarge.copy(lineBreak = phrase),
        headlineMedium = headlineMedium.copy(lineBreak = phrase),
        headlineSmall = headlineSmall.copy(lineBreak = phrase),
        titleLarge = titleLarge.copy(lineBreak = phrase),
        titleMedium = titleMedium.copy(lineBreak = phrase),
        bodyLarge = bodyLarge.copy(lineBreak = phrase),
        bodyMedium = bodyMedium.copy(lineBreak = phrase),
        bodySmall = bodySmall.copy(lineBreak = phrase),
        labelLarge = labelLarge.copy(lineBreak = phrase),
        labelMedium = labelMedium.copy(lineBreak = phrase),
    )
}

@Composable
fun AtTheDoorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalVerdictColors provides if (darkTheme) darkVerdict else lightVerdict,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(colorScheme = if (darkTheme) dark else light, typography = typography, content = content)
    }
}
