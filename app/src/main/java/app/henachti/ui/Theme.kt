package app.henachti.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.henachti.R

/** Paper and ink. Calm, printed, no neon. */
object Palette {
    val Paper = Color(0xFFF4EFE6)
    val PaperDeep = Color(0xFFEAE3D6)
    val Ink = Color(0xFF1B1A17)
    val InkSoft = Color(0xFF4A463F)
    val Muted = Color(0xFF7A7367)
    val Bronze = Color(0xFF8A6A3C)
    val Line = Color(0xFFD8D0C2)
    val Danger = Color(0xFF8C3B2E)
    val Success = Color(0xFF3F6B45)
}

/** Frank Ruhl Libre, variable weight 300 to 900, bundled so it works offline. */
val Serif = FontFamily(
    Font(R.font.frank_ruhl_libre, FontWeight.Light),
    Font(R.font.frank_ruhl_libre, FontWeight.Normal),
    Font(R.font.frank_ruhl_libre, FontWeight.Medium),
    Font(R.font.frank_ruhl_libre, FontWeight.Bold),
    Font(R.font.frank_ruhl_libre, FontWeight.Black),
)

private val HenachtiTypography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 50.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp),
)

@Composable
fun HenachtiTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Palette.Ink,
        onPrimary = Palette.Paper,
        secondary = Palette.Bronze,
        onSecondary = Palette.Paper,
        background = Palette.Paper,
        onBackground = Palette.Ink,
        surface = Palette.Paper,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.PaperDeep,
        onSurfaceVariant = Palette.InkSoft,
        outline = Palette.Line,
        error = Palette.Danger,
    )
    MaterialTheme(colorScheme = colors, typography = HenachtiTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Paper)
            ) { content() }
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Palette.Bronze, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Palette.Line)
}

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Ink,
            contentColor = Palette.Paper,
            disabledContainerColor = Palette.Line,
            disabledContentColor = Palette.Muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (enabled) Palette.Ink else Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Palette.Ink,
            disabledContentColor = Palette.Muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
