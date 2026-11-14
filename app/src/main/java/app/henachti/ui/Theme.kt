package app.henachti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.henachti.R

/** Warm Beige & Soft Gold: cream paper, chocolate cards, soft gold accents. */
object Palette {
    val Cream = Color(0xFFF6F0E6)
    val CreamDeep = Color(0xFFEEE5D5)
    val Sand = Color(0xFFE2D7C3)
    val Chocolate = Color(0xFF2B2219)
    val ChocolateDeep = Color(0xFF1C1612)
    val Gold = Color(0xFFC9A75C)
    val GoldSoft = Color(0xFFE2CC8E)
    val GoldDeep = Color(0xFFA8863F)
    /** Gold for text on cream: 4.9:1. GoldDeep stays for icons, lines and large numerals. */
    val GoldText = Color(0xFF7F6429)
    val Ink = Color(0xFF2A2420)
    val InkSoft = Color(0xFF5B5248)
    // 5:1 on Cream, 4.6:1 on CreamDeep (the old 8C8172 was 3.4:1 and failed for small text)
    val Muted = Color(0xFF6E6458)
    val Success = Color(0xFF3E8E5E)
    val Danger = Color(0xFF9B3B2E)

    // names used by older screens
    val Paper: Color get() = Cream
    val PaperDeep: Color get() = CreamDeep
    val Line: Color get() = Sand
    val Bronze: Color get() = GoldDeep
}

/** Heebo, bundled, variable weight: the one face of the whole app, siddur included. */

val Sans = FontFamily(
    Font(R.font.heebo, FontWeight.Light),
    Font(R.font.heebo, FontWeight.Normal),
    Font(R.font.heebo, FontWeight.Medium),
    Font(R.font.heebo, FontWeight.SemiBold),
    Font(R.font.heebo, FontWeight.Bold),
)

// One family for the whole interface (Heebo), five sizes (40 / 22 / 16 / 14 / 12), three weights.
private val HenachtiTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
)

/** Reading style for prayer text: the same face as the app, large, with generous leading for the vowel marks. */
fun prayerTextStyle(sizeSp: Int): TextStyle =
    TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.8f).sp)

@Composable
fun HenachtiTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Palette.Chocolate,
        onPrimary = Palette.Cream,
        secondary = Palette.Gold,
        onSecondary = Palette.Chocolate,
        background = Palette.Cream,
        onBackground = Palette.Ink,
        surface = Palette.Cream,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.CreamDeep,
        onSurfaceVariant = Palette.InkSoft,
        outline = Palette.Sand,
        error = Palette.Danger,
    )
    MaterialTheme(colorScheme = colors, typography = HenachtiTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Cream)
            ) { content() }
        }
    }
}
