package app.henachti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.henachti.R
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.ui.Arch
import app.henachti.ui.CardShape
import app.henachti.ui.FormField
import app.henachti.ui.GoldButton
import app.henachti.ui.LinkRow
import app.henachti.ui.Palette
import app.henachti.ui.SoftCard
import app.henachti.ui.TopBar
import app.henachti.ui.iconPainter

/** Pick what today's tefillin are for. Each kind opens its own page to write the name. */
@Composable
fun DedicationScreen(
    prefs: Prefs,
    now: Long,
    onOpenType: (String) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    var current by remember(now) { mutableStateOf(Dedications.label(prefs)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("לזכות מי?", onBack = onBack, onSettings = onSettings)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Palette.Chocolate)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Arch(width = 88.dp, height = 106.dp) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "ההנחה והתפילות של היום יכולות להיות לזכות מישהו.",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Cream,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text("בחר סוג, ובעמוד הבא כתוב שם.", style = MaterialTheme.typography.bodyMedium, color = Palette.GoldSoft)
            }

            current?.let { label ->
                Spacer(Modifier.height(14.dp))
                SoftCard(Modifier.fillMaxWidth(), gold = true) {
                    Column {
                        Text("ההקדשה הפעילה", style = MaterialTheme.typography.labelMedium, color = Palette.GoldDeep)
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            prefs.clearDedication()
                            current = null
                        }) {
                            Text("הסר", color = Palette.Muted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Dedications.all.forEach { d ->
                LinkRow(
                    title = d.title,
                    subtitle = d.subtitle,
                    onClick = { onOpenType(d.id) },
                    leading = {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Palette.Cream)
                                .border(1.dp, if (prefs.dedicationType == d.id) Palette.Gold else Palette.Sand, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(iconPainter(d.icon), contentDescription = null, tint = Palette.GoldDeep, modifier = Modifier.size(20.dp))
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** One kind of dedication: explanation, a name, a gold button. */
@Composable
fun DedicateScreen(prefs: Prefs, typeId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val type = Dedications.byId(typeId)
    if (type == null) {
        onBack()
        return
    }
    var name by remember { mutableStateOf(if (prefs.dedicationType == typeId) prefs.dedicationName else "") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(type.title, onBack = onBack)
        Column(
            Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Arch(width = 104.dp, height = 126.dp) {
                Icon(iconPainter(type.icon), contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(type.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(type.explain, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))

            Row(Modifier.fillMaxWidth()) {
                Text(if (type.id == "general") "המילים שלך:" else "השם:", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            FormField(name, { name = it }, type.placeholder)
            Spacer(Modifier.height(18.dp))
            GoldButton(
                text = "הקדש את היום",
                enabled = name.isNotBlank() || type.id == "general",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    prefs.dedicationType = type.id
                    prefs.dedicationName = name
                    onSaved()
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "ההקדשה מופיעה במסך הראשי ובמסך הנעילה, ונשארת עד שתחליף או תסיר אותה.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}
