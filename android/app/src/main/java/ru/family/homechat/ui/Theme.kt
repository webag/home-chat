package ru.family.homechat.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandStart = Color(0xFF4F7DF3)
val BrandEnd = Color(0xFF8B5CF6)
val BrandGradient = Brush.linearGradient(listOf(BrandStart, BrandEnd))

/** Chat-specific colors that Material's scheme has no slot for. */
@Immutable
data class ChatColors(
    val background: Brush,
    val bubbleIn: Brush,
    val onBubbleIn: Color,
    val bubbleOut: Brush,
    val pill: Color,
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(SolidColor(Color.White), SolidColor(Color.White), Color.Black, BrandGradient, Color.White)
}

private val Light = lightColorScheme(
    primary = Color(0xFF4F64F0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FF), onPrimaryContainer = Color(0xFF0F1B6B),
    secondary = Color(0xFF7C5CF0), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE6FF), onSecondaryContainer = Color(0xFF2A1475),
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF161A26),
    surface = Color(0xFFF7F8FC), onSurface = Color(0xFF161A26),
    surfaceVariant = Color(0xFFE6E8F2), onSurfaceVariant = Color(0xFF5F6578),
    outline = Color(0xFFC5C9D8), outlineVariant = Color(0xFFE1E4EE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFEDEEF6), surfaceContainerHigh = Color(0xFFE8EAF3),
    surfaceContainerHighest = Color(0xFFE2E5F0), surfaceDim = Color(0xFFDCDFEA), surfaceBright = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9B4FF), onPrimary = Color(0xFF14206E),
    primaryContainer = Color(0xFF2D3A9E), onPrimaryContainer = Color(0xFFE1E5FF),
    secondary = Color(0xFFC4B2FF), onSecondary = Color(0xFF2E1677),
    secondaryContainer = Color(0xFF45309A), onSecondaryContainer = Color(0xFFEDE6FF),
    background = Color(0xFF0F1117), onBackground = Color(0xFFE5E7F0),
    surface = Color(0xFF0F1117), onSurface = Color(0xFFE5E7F0),
    surfaceVariant = Color(0xFF2A2E3B), onSurfaceVariant = Color(0xFF9DA3B6),
    outline = Color(0xFF4A5063), outlineVariant = Color(0xFF2A2E3B),
    surfaceContainerLowest = Color(0xFF0A0C11), surfaceContainerLow = Color(0xFF161922),
    surfaceContainer = Color(0xFF1A1D27), surfaceContainerHigh = Color(0xFF232733),
    surfaceContainerHighest = Color(0xFF2C3040), surfaceDim = Color(0xFF0F1117), surfaceBright = Color(0xFF343949),
)

private val LightChat = ChatColors(
    background = Brush.verticalGradient(listOf(Color(0xFFEDF0FA), Color(0xFFF3EEFB))),
    bubbleIn = SolidColor(Color.White),
    onBubbleIn = Color(0xFF161A26),
    bubbleOut = BrandGradient,
    pill = Color(0xD9FFFFFF),
)

private val DarkChat = ChatColors(
    background = Brush.verticalGradient(listOf(Color(0xFF0B0D12), Color(0xFF120F1C))),
    bubbleIn = SolidColor(Color(0xFF1F2330)),
    onBubbleIn = Color(0xFFE5E7F0),
    bubbleOut = Brush.linearGradient(listOf(Color(0xFF3F63D8), Color(0xFF6E4AD8))),
    pill = Color(0xCC1F2330),
)

private val BaseType = Typography()
private val AppType = Typography(
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseType.bodyLarge.copy(lineHeight = 22.sp, letterSpacing = 0.2.sp),
    labelSmall = BaseType.labelSmall.copy(letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HomeChatTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalChatColors provides if (dark) DarkChat else LightChat) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppType,
            shapes = AppShapes,
            content = content,
        )
    }
}
