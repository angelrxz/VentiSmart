package cl.inacap.ventismart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta tomada de los mockups de VentiSmart. */
object V {
    val Teal = Color(0xFF127C72)
    val TealDark = Color(0xFF0D5F57)
    val Ink = Color(0xFF14302C)
    val Muted = Color(0xFF5E716D)
    val Faint = Color(0xFF8A9B97)
    val Bg = Color(0xFFF4F7F6)
    val Card = Color(0xFFFFFFFF)
    val Mint = Color(0xFFE5F2EF)
    val MintStrong = Color(0xFFD3EBE6)
    val Line = Color(0xFFE0E8E6)
    val Ok = Color(0xFF1E8A5C)
    val Amber = Color(0xFF9C6B10)
    val AmberBg = Color(0xFFFFF4DC)
    val Red = Color(0xFFC0392B)
    val RedBg = Color(0xFFFDECEC)
    val Disabled = Color(0xFFE6EBEA)
    val DisabledText = Color(0xFF9AA7A4)
}

private val Scheme = lightColorScheme(
    primary = V.Teal, onPrimary = Color.White,
    secondary = V.TealDark, background = V.Bg, onBackground = V.Ink,
    surface = V.Card, onSurface = V.Ink, surfaceVariant = V.Mint, onSurfaceVariant = V.Muted,
    error = V.Red, outline = V.Line,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun VentiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
