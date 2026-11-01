package app.henachti.data

import android.content.Context
import org.json.JSONObject

data class PrayerSection(val heading: String, val paragraphs: List<String>)

data class Prayer(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String, // daily | shabbat | special
    val icon: String,
    val sections: List<PrayerSection>,
)

/** Siddur texts bundled in assets (built by tools/fetch_siddur.mjs from Sefaria). Loaded once. */
object PrayerRepository {

    @Volatile
    private var cache: List<Prayer>? = null

    fun load(context: Context): List<Prayer> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val text = context.assets.open("prayers_sefard.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val arr = root.getJSONArray("prayers")
            val out = ArrayList<Prayer>(arr.length())
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val sections = ArrayList<PrayerSection>()
                val secs = p.optJSONArray("sections")
                if (secs != null) for (j in 0 until secs.length()) {
                    val s = secs.getJSONObject(j)
                    val paras = ArrayList<String>()
                    val pa = s.optJSONArray("paragraphs")
                    if (pa != null) for (k in 0 until pa.length()) paras += pa.getString(k)
                    sections += PrayerSection(s.optString("heading", ""), paras)
                }
                out += Prayer(
                    id = p.getString("id"),
                    title = p.optString("title", ""),
                    subtitle = p.optString("subtitle", ""),
                    category = p.optString("category", "daily"),
                    icon = p.optString("icon", "sparkle"),
                    sections = sections,
                )
            }
            cache = out
            return out
        }
    }

    fun byId(context: Context, id: String): Prayer? = load(context).firstOrNull { it.id == id }

    /** Instruction lines have no vowel points; prayer text does. */
    fun isInstruction(paragraph: String): Boolean {
        if (paragraph.length > 260) return false
        return paragraph.none { it in 'ְ'..'ׇ' }
    }
}
