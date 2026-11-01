package app.henachti.data

data class DedicationType(
    val id: String,
    val title: String,
    val subtitle: String,
    val explain: String,
    val placeholder: String,
    val icon: String,
)

/** What the day's tefillin and prayers are dedicated to. Stored locally in Prefs. */
object Dedications {
    val all = listOf(
        DedicationType(
            "memory", "לעילוי נשמת", "לנשמת יקיר שהלך לעולמו",
            "ההנחה והתפילות של היום יעלו לזכות נשמתו. נהוג לכתוב את השם ושם האב.",
            "למשל: יוסף בן יעקב", "sparkle",
        ),
        DedicationType(
            "healing", "לרפואת", "למי שזקוק לרפואה",
            "ההנחה והתפילות של היום לרפואה שלמה. נהוג לכתוב את השם ושם האם.",
            "למשל: יוסף בן חנה", "heart",
        ),
        DedicationType(
            "success", "להצלחת", "פרנסה, לימודים, זיווג",
            "ההנחה והתפילות של היום להצלחה במה שעל הפרק.",
            "למשל: משפחת כהן", "sun",
        ),
        DedicationType(
            "mazal", "למזל טוב של", "יום שמחה או התחלה חדשה",
            "ההנחה והתפילות של היום לשמחה ולהתחלה טובה.",
            "למשל: דוד ורחל", "flame",
        ),
        DedicationType(
            "general", "הקדשה אישית", "מה שבלב",
            "כתוב במילים שלך לזכות מה או מי ההנחה של היום.",
            "למשל: לשלום בבית", "book",
        ),
    )

    fun byId(id: String): DedicationType? = all.firstOrNull { it.id == id }

    /** "לרפואת יוסף בן חנה", or null when nothing is dedicated. */
    fun label(prefs: Prefs): String? {
        val type = byId(prefs.dedicationType) ?: return null
        val name = prefs.dedicationName
        return when {
            type.id == "general" && name.isBlank() -> type.title
            type.id == "general" -> name
            name.isBlank() -> type.title
            else -> "${type.title} $name"
        }
    }
}
