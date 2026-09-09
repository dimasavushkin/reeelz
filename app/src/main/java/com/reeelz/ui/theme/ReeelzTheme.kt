package com.reeelz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.reeelz.R

val Ink = Color(0xFF080A0E)
val Surface = Color(0xFF11141A)
val SurfaceRaised = Color(0xFF191D25)
val Accent = Color.White
val AccentGlow = Color.White
val Muted = Color(0xFF9298A6)
val Divider = Color(0xFF2A2F39)

private val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold),
)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B0D10),
    primaryContainer = Color(0xFF292D34),
    onPrimaryContainer = Color.White,
    background = Ink,
    onBackground = Color(0xFFF5F2F8),
    surface = Surface,
    onSurface = Color(0xFFF5F2F8),
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color(0xFFCED1D8),
    outline = Divider,
    error = Color(0xFFFFB4AB),
)

private val ReeelzTypography = Typography(
    headlineLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = GoogleSans, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = GoogleSans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = GoogleSans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun ReeelzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = ReeelzTypography, content = content)
}
