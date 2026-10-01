package com.mathclock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ClassroomColors {
    val Ivory = Color(0xFFFFF8EC)
    val IvoryDeep = Color(0xFFF6ECD8)
    val ClockFrame = Color(0xFF8DB4DA)
    val ClockFrameDark = Color(0xFF5E8DBB)
    val ClockFace = Color(0xFFFFFDF7)
    val Navy = Color(0xFF1F2A44)
    val Teal = Color(0xFF13777A)
    val NotebookYellow = Color(0xFFF4DE8C)
    val NotebookPaper = Color(0xFFFFF6D6)
    val NotebookLine = Color(0xFFE3CD7E)
    val MarginRed = Color(0xFFD98880)
    val Text = Color(0xFF1C2130)
    val TextMuted = Color(0xFF4F5668)
    val Correct = Color(0xFF2E7D4F)
    val Incorrect = Color(0xFFB3412F)
    val Bookmark = Color(0xFF2F5D8A)
}

private val ColorScheme = lightColorScheme(
    primary = ClassroomColors.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5F4),
    onPrimaryContainer = ClassroomColors.Navy,
    secondary = ClassroomColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBEA),
    onSecondaryContainer = Color(0xFF003F41),
    tertiary = Color(0xFF7A5C00),
    tertiaryContainer = ClassroomColors.NotebookYellow,
    onTertiaryContainer = Color(0xFF3B2C00),
    background = ClassroomColors.Ivory,
    onBackground = ClassroomColors.Text,
    surface = ClassroomColors.Ivory,
    onSurface = ClassroomColors.Text,
    surfaceVariant = ClassroomColors.IvoryDeep,
    onSurfaceVariant = ClassroomColors.TextMuted,
    surfaceContainer = Color(0xFFFBF1DF),
    surfaceContainerHigh = Color(0xFFF6EBD6),
    surfaceContainerLow = Color(0xFFFFFBF2),
    outline = Color(0xFF7D8495),
    error = ClassroomColors.Incorrect,
)

private val Rounded = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Whether decorative animation is reduced (user setting). */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun MathClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, shapes = AppShapes, content = content)
}
