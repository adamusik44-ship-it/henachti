package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.henachti.data.Prayer
import app.henachti.data.PrayerRepository
import app.henachti.data.Prefs
import app.henachti.ui.Arch
import app.henachti.ui.CardShape
import app.henachti.ui.ChipRow
import app.henachti.ui.CircleButton
import app.henachti.ui.Eyebrow
import app.henachti.ui.Hairline
import app.henachti.ui.Palette
import app.henachti.ui.SearchField
import app.henachti.ui.TopBar
import app.henachti.ui.iconPainter
import app.henachti.ui.prayerTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf("daily", "shabbat", "special")
private val CATEGORY_LABELS = listOf("חול", "שבת וחג", "ברכות ועוד")

@Composable
fun PrayersScreen(onOpen: (String) -> Unit, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val prayers by produceState<List<Prayer>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { PrayerRepository.load(context) }.getOrDefault(emptyList()) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar("הסידור", "נוסח ספרד", onBack = onBack, onSettings = onSettings)
        ChipRow(CATEGORY_LABELS, tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            SearchField(query, { query = it }, "חיפוש בסידור...")
        }
        Spacer(Modifier.height(12.dp))

        val list = prayers
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Gold)
            }
        } else {
            val shown = if (query.isBlank()) list.filter { it.category == CATEGORIES[tab] }
            else list.filter { it.title.contains(query) || it.subtitle.contains(query) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { prayer ->
                    PrayerCard(prayer) { onOpen(prayer.id) }
                }
            }
        }
    }
}

@Composable
private fun PrayerCard(prayer: Prayer, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(CardShape)
            .background(Palette.CreamDeep)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(Palette.Chocolate),
            contentAlignment = Alignment.Center,
        ) {
            Arch(width = 62.dp, height = 78.dp) {
                Icon(iconPainter(prayer.icon), contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(28.dp))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(prayer.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (prayer.subtitle.isNotBlank()) {
                Text(prayer.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun PrayerReaderScreen(id: String, prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val prayer by produceState<Prayer?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) { runCatching { PrayerRepository.byId(context, id) }.getOrNull() }
    }
    var size by remember { mutableIntStateOf(prefs.prayerFontSize) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = prayer?.title ?: "",
            subtitle = prayer?.subtitle,
            onBack = onBack,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            CircleButton({ size = (size - 2).coerceAtLeast(18); prefs.prayerFontSize = size }) {
                Text("א-", style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            }
            Spacer(Modifier.width(8.dp))
            CircleButton({ size = (size + 2).coerceAtMost(40); prefs.prayerFontSize = size }) {
                Text("א+", style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
            }
        }

        val p = prayer
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Gold)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                p.sections.forEachIndexed { index, section ->
                    item(key = "h-$index") {
                        Spacer(Modifier.height(18.dp))
                        Eyebrow(section.heading)
                        Spacer(Modifier.height(6.dp))
                        Hairline()
                        Spacer(Modifier.height(12.dp))
                    }
                    items(section.paragraphs) { paragraph ->
                        if (PrayerRepository.isInstruction(paragraph)) {
                            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        } else {
                            Text(paragraph, style = prayerTextStyle(size), color = Palette.Ink)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    Spacer(Modifier.height(28.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "הטקסט: סידור מצודה (1981), באדיבות Sefaria, רישיון CC-BY.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Muted,
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}
