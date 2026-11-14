package app.henachti.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.henachti.R

/** The arch: a rounded top on a flat base. The app's one recurring shape. */
val ArchShape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 0, bottomEndPercent = 0)
val CardShape = RoundedCornerShape(22.dp)
val ButtonShape = RoundedCornerShape(16.dp)

/** Drawable for a prayer-card icon id from the JSON. */
@Composable
fun iconPainter(name: String): Painter = painterResource(
    when (name) {
        "sun" -> R.drawable.ic_sun
        "book" -> R.drawable.ic_book
        "moon" -> R.drawable.ic_moon
        "flame" -> R.drawable.ic_flame
        "shield" -> R.drawable.ic_shield
        "tefillin" -> R.drawable.ic_tefillin
        "bread" -> R.drawable.ic_bread
        "candle" -> R.drawable.ic_candle
        "cup" -> R.drawable.ic_cup
        "heart" -> R.drawable.ic_heart
        else -> R.drawable.ic_sparkle
    }
)

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Palette.GoldDeep, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Palette.Sand)
}

@Composable
fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Palette.CreamDeep)
            .border(1.dp, Palette.Sand, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Header row: a leading slot (back or a chip), centered title, settings gear. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterStart) {
            when {
                leading != null -> leading()
                onBack != null -> CircleButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה", tint = Palette.Ink)
                }
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.GoldDeep)
            }
        }
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            if (onSettings != null) CircleButton(onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = Palette.Ink)
            }
        }
    }
}

@Composable
fun Arch(
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    height: Dp = 140.dp,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val fill = if (dark) Brush.verticalGradient(listOf(Palette.ChocolateDeep, Palette.Chocolate))
    else Brush.verticalGradient(listOf(Palette.Cream, Palette.CreamDeep))
    Box(
        modifier
            .size(width, height)
            .clip(ArchShape)
            .background(fill)
            .border(1.5.dp, Palette.Gold, ArchShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
    vector: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Chocolate,
            contentColor = Palette.Cream,
            disabledContainerColor = Palette.Sand,
            disabledContentColor = Palette.Muted,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        } else if (vector != null) {
            Icon(vector, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Older name, same look. */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    DarkButton(text, onClick, modifier, enabled)

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // one flat accent colour, no metallic gradient
    Box(
        modifier
            .height(56.dp)
            .clip(ButtonShape)
            .background(if (enabled) Palette.Gold else Palette.Sand)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Palette.ChocolateDeep else Palette.Muted,
        )
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
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Palette.Chocolate else Palette.Sand),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Palette.Ink,
            disabledContentColor = Palette.Muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Cream card with a soft edge. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gold: Boolean = false,
    content: @Composable () -> Unit,
) {
    var m = modifier
        .clip(CardShape)
        .background(Palette.CreamDeep)
        .border(1.dp, if (gold) Palette.Gold else Palette.Sand, CardShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(16.dp)) { content() }
}

/** A line with a gold icon, a label, and a bold value. */
@Composable
fun StatRow(icon: Painter, label: String, value: String, dot: Color? = null) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Palette.GoldDeep, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (dot != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.GoldDeep)
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Palette.Chocolate else Palette.CreamDeep)
                    .border(1.dp, if (on) Palette.Chocolate else Palette.Sand, RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) Palette.Cream else Palette.InkSoft,
                )
            }
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = Palette.Muted) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Palette.Muted) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.CreamDeep,
            unfocusedContainerColor = Palette.CreamDeep,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink,
            cursorColor = Palette.Ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cream text field for settings forms. */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    minLines: Int = 1,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        minLines = minLines,
        singleLine = minLines == 1,
        placeholder = { Text(placeholder, color = Palette.Muted) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.CreamDeep,
            unfocusedContainerColor = Palette.CreamDeep,
            disabledContainerColor = Palette.CreamDeep,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink,
            disabledTextColor = Palette.Muted,
            cursorColor = Palette.Ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A navigation row: title, subtitle and a chevron. */
@Composable
fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Palette.Muted)
        }
    }
}

/** The toggle from Michael's sketch: a chocolate pill with a cream knob. */
@Composable
fun HenachtiSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Palette.Cream,
            checkedTrackColor = Palette.Chocolate,
            checkedBorderColor = Palette.Chocolate,
            uncheckedThumbColor = Palette.Chocolate,
            uncheckedTrackColor = Palette.Cream,
            uncheckedBorderColor = Palette.Chocolate,
        ),
    )
}

enum class Tab(val route: String, val label: String) {
    HOME("home", "היום"),
    PRAYERS("prayers", "סידור"),
    DEDICATION("dedication", "לזכות"),
}

@Composable
fun BottomNav(current: String?, onSelect: (Tab) -> Unit) {
    // navigationBarsPadding keeps the tabs above the phone's own back/home/recents bar
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Cream)
            .navigationBarsPadding()
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Tab.entries.forEach { tab ->
                val on = current == tab.route
                NavItem(tab, on, Modifier.weight(1f)) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (on) Palette.GoldDeep else Palette.Muted
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (tab) {
            Tab.HOME -> Icon(painterResource(R.drawable.ic_sun), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Tab.PRAYERS -> Icon(painterResource(R.drawable.ic_book), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Tab.DEDICATION -> Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Two buttons side by side. */
@Composable
fun RowScope.HalfSpacer() = Spacer(Modifier.width(12.dp))
