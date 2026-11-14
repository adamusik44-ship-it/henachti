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

/**
 * Playful and calm (inspired by the EON shot Adam saved on Dribbble): a cream page, a few strong
 * flat colours, a yellow sun. The old names stay so every screen picks up the new colours:
 * Chocolate is now the deep teal, Gold the sun yellow, GoldDeep/GoldText the wine red.
 */
object Palette {
    val Cream = Color(0xFFFBF6E9)
    val CreamDeep = Color(0xFFF4EDDC)
    val Sand = Color(0xFFE5DCC6)
    val Chocolate = Color(0xFF0E4744)       // deep teal: dark panels, secondary pills, arches
    val ChocolateDeep = Color(0xFF0A3432)
    val Gold = Color(0xFFFFC94A)            // the sun: dots, rings, highlights
    val GoldSoft = Color(0xFFFFE08A)
    val GoldDeep = Color(0xFF7A0E2C)        // wine: icons and large numerals on cream
    /** Wine for text on cream: well above 7:1. */
    val GoldText = Color(0xFF7A0E2C)
    val Ink = Color(0xFF1D1B19)
    val InkSoft = Color(0xFF4A453F)
    val Muted = Color(0xFF66605A)            // 5.6:1 on Cream
    val Success = Color(0xFF2F7D5B)
    val Danger = Color(0xFFB3261E)

    // the playful set
    val Teal = Chocolate
    val TealSoft = Color(0xFF165E59)
    val Wine = GoldDeep
    val Coral = Color(0xFFF2542D)
    val Blush = Color(0xFFF9C9D9)
    val Lime = Color(0xFFD6E09C)
    val Sun = Gold

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
    Font(R.font.heebo, FontWeight.ExtraBold),
    Font(R.font.heebo, FontWeight.Black),
)

// One family (Heebo), five sizes (56 / 26 / 16 / 14 / 12). Black for the big words, Bold for titles.
private val HenachtiTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp),
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
