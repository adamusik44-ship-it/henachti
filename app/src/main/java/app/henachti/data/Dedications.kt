package app.henachti.data

data class DedicationType(val id: String, val title: String, val subtitle: String, val icon: String)

/** What the day's tefillin and prayers are dedicated to. Stored locally in Prefs. */
object Dedications {
    val all = listOf(
        DedicationType("memory", "לעילוי נשמת", "נשמה צרורה בצרור החיים", "sparkle"),
        DedicationType("healing", "לרפואת", "רפואה שלמה לגוף ולנפש", "heart"),
        DedicationType("success", "להצלחת", "ברכה והצלחה בכל מעשה ידיהם", "sun"),
        DedicationType("mazal", "למזל טוב של", "שמחה, ברכה והתחלה טובה", "flame"),
        DedicationType("general", "הקדשה כללית", "הקדשה אישית מכל הלב", "book"),
    )

    fun byId(id: String): DedicationType? = all.firstOrNull { it.id == id }

    /** "לרפואת יוסף בן חנה", or null when nothing is dedicated. */
    fun label(prefs: Prefs): String? {
        val type = byId(prefs.dedicationType) ?: return null
        val name = prefs.dedicationName
        return when {
            type.id == "general" && name.isBlank() -> type.title
            type.id == "general" -> "הקדשה: $name"
            name.isBlank() -> type.title
            else -> "${type.title} $name"
        }
    }
}
