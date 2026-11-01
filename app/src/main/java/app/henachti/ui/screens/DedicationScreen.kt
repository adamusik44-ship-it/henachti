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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import app.henachti.data.DedicationType
import app.henachti.data.Dedications
import app.henachti.data.Prefs
import app.henachti.ui.Arch
import app.henachti.ui.CardShape
import app.henachti.ui.FormField
import app.henachti.ui.GoldButton
import app.henachti.ui.Palette
import app.henachti.ui.SoftCard
import app.henachti.ui.TopBar
import app.henachti.ui.iconPainter

@Composable
fun DedicationScreen(prefs: Prefs, onBack: () -> Unit, onSettings: () -> Unit) {
    var type by remember { mutableStateOf(prefs.dedicationType) }
    var name by remember { mutableStateOf(prefs.dedicationName) }
    var saved by remember { mutableStateOf(Dedications.label(prefs)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("הקדשת תפילה", onBack = onBack, onSettings = onSettings)
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
                    "לזכות מה תרצה להקדיש את התפילה?",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Cream,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text("בחר את מטרת ההקדשה", style = MaterialTheme.typography.bodyMedium, color = Palette.GoldSoft)
            }

            Spacer(Modifier.height(16.dp))
            Dedications.all.forEach { d ->
                OptionRow(d, selected = type == d.id) { type = d.id }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text("שם להקדשה:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FormField(name, { name = it }, "הכנס שם (לדוגמה: יוסף בן חנה)...")

            Spacer(Modifier.height(16.dp))
            GoldButton(
                text = "הקדש את התפילה",
                enabled = type.isNotBlank() && (name.isNotBlank() || type == "general"),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    prefs.dedicationType = type
                    prefs.dedicationName = name
                    saved = Dedications.label(prefs)
                },
            )

            saved?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ההקדשה שמורה: $it. ההנחה והתפילות הבאות מוקדשות לכך.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Success,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        prefs.clearDedication()
                        type = ""
                        name = ""
                        saved = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("הסר הקדשה", color = Palette.Muted)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun OptionRow(d: DedicationType, selected: Boolean, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick, gold = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Palette.Cream)
                    .border(1.dp, if (selected) Palette.Gold else Palette.Sand, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconPainter(d.icon), contentDescription = null, tint = Palette.GoldDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Palette.Muted)
        }
    }
}
