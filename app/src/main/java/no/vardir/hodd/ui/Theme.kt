package no.vardir.hodd.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFFAF8F4)
val Ink = Color(0xFF181822)
val Violet = Color(0xFF5945ED)
val Muted = Color(0xFF76747D)
val Line = Color(0xFFE7E3DE)
val Heading = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Ink)
private val scheme = lightColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Color(0xFFEAE5FF),
    secondary = Color(0xFF466653), background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = Color(0xFFF0ECE6),
    onSurfaceVariant = Muted, outline = Color(0xFF928D99), outlineVariant = Line,
)
@Composable
fun HoddTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(
        headlineLarge = Heading.copy(fontSize = 36.sp, lineHeight = 39.sp),
        headlineMedium = Heading.copy(fontSize = 29.sp, lineHeight = 34.sp),
        titleLarge = Heading.copy(fontSize = 23.sp, lineHeight = 28.sp),
    ), content = content)
}
