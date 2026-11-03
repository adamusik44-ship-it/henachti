package app.henachti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.henachti.ui.Eyebrow
import app.henachti.ui.Hairline
import app.henachti.ui.Palette
import app.henachti.ui.TopBar

/** Privacy, terms and credits. Plain text, nothing to tap. */
@Composable
fun AboutScreen(version: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("פרטיות ורישיונות", subtitle = "גרסה $version", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {

            Section("פרטיות")
            Para("האפליקציה לא אוספת, לא שומרת מחוץ לטלפון ולא שולחת שום מידע. אין לה הרשאת אינטרנט, ואין שרת.")
            Para("מצלמה: בזמן האימות הפריימים מנותחים בזיכרון ונמחקים מיד. לא נשמרת תמונה, לא נשלחת תמונה.")
            Para("נתוני שימוש: האפליקציה רואה איזו אפליקציה פתוחה כרגע, רק כדי להפעיל את מסך הנעילה. המידע לא נשמר.")
            Para("מה שנשמר בטלפון בלבד: ההגדרות, הימים שאומתו, השם וההקדשה. הסרת האפליקציה מוחקת את הכול. אין פרסומות, אין מעקב.")

            Section("תנאי שימוש")
            Para("האפליקציה ניתנת כמות שהיא, ללא אחריות מכל סוג. השימוש על אחריות המשתמש בלבד.")
            Para("האפליקציה לעולם לא חוסמת חיוג ושיחות חירום, מסך הבית, מקלדת ושעון מעורר.")
            Para("דרכי יציאה: הסרה מסודרת מתוך ההגדרות (48 שעות), ביטול מיידי ב-24 השעות הראשונות, או מצב בטוח של אנדרואיד.")
            Para("האפליקציה אינה פוסקת הלכה. זמני היום מחושבים לפי העיר שנבחרה ונועדו לנוחות בלבד.")

            Section("קרדיטים ורישיונות")
            Para("טקסטי הסידור: סידור מצודה (1981), באדיבות Sefaria, רישיון CC-BY. חלקים מתוך \"תורת אמת\", נחלת הכלל. הנוסח המסורתי עצמו הוא נחלת הכלל.")
            Para("חישוב זמנים ולוח עברי: KosherJava Zmanim, רישיון LGPL 2.1.")
            Para("גופנים: Frank Ruhl Libre ו-Heebo, רישיון SIL Open Font License 1.1.")
            Para("ספריות: AndroidX, Jetpack Compose, CameraX, Kotlin, רישיון Apache 2.0.")
            Para("קוד האפליקציה: רישיון MIT, פתוח לקריאה ב-GitHub.")
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Eyebrow(title)
    Spacer(Modifier.height(6.dp))
    Hairline()
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
    Spacer(Modifier.height(10.dp))
}
