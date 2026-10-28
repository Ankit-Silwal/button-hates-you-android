package com.ankitsilwal.buttonhatesyou.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The web's warm cream palette (src/app/globals.css :root).
object Palette {
    val Bg = Color(0xFFF7EFE3)
    val Wall = Color(0xFFF4E7D4)
    val Floor = Color(0xFFE8D5BB)
    val Ink = Color(0xFF4A3428)
    val InkSoft = Color(0xFF8A6F5E)
    val Cream = Color(0xFFFFFAF2)
    val Btn = Color(0xFFE2674F)
    val BtnDark = Color(0xFFB84A36)
    val Mustard = Color(0xFFF0C36D)
    val MustardDark = Color(0xFFC99A3E)
    val Sage = Color(0xFF9BBF9A)
    val SageDark = Color(0xFF6E9670)
    val Shadow = Color(0x294A3428)
    val Alert = Color(0xFFE5402F)
    val Night = Color(0xFF262A52)
    val NightInk = Color(0xFFF1ECFF)
    val Diamond = Color(0xFF4FB6E8)
}

private val Scheme = lightColorScheme(
    primary = Palette.Btn,
    onPrimary = Color.White,
    primaryContainer = Palette.Mustard,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Sage,
    onSecondary = Palette.Ink,
    tertiary = Palette.Mustard,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Wall,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainer = Palette.Cream,
    surfaceContainerHigh = Palette.Cream,
    surfaceContainerLow = Palette.Bg,
    outline = Color(0xFFD8BC98),
    outlineVariant = Color(0xFFE6CFB1),
    error = Palette.Alert,
)

// A rounded, chunky feel: the system sans in heavier weights.
private val Rounded = FontFamily.SansSerif
private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.3f).toInt()) =
    TextStyle(fontFamily = Rounded, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, color = Palette.Ink)

private val Type = Typography(
    displaySmall = style(32, FontWeight.ExtraBold),
    headlineMedium = style(26, FontWeight.ExtraBold),
    headlineSmall = style(22, FontWeight.Bold),
    titleLarge = style(20, FontWeight.Bold),
    titleMedium = style(16, FontWeight.Bold),
    titleSmall = style(14, FontWeight.Bold),
    bodyLarge = style(16, FontWeight.Medium),
    bodyMedium = style(14, FontWeight.Medium),
    bodySmall = style(12, FontWeight.Medium),
    labelLarge = style(15, FontWeight.Bold),
    labelMedium = style(12, FontWeight.Bold),
    labelSmall = style(11, FontWeight.Bold),
)

private val ShapeSet = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** True when the system's "remove animations" is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ButtonTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = ShapeSet) {
        CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
    }
}
