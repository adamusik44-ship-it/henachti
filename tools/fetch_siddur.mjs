// Builds app/src/main/assets/prayers_sefard.json from Sefaria's "Siddur Sefard" (open text).
// Run from the repo root:  node tools/fetch_siddur.mjs
// The app ships the JSON, so it never needs the network at runtime.

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = "Siddur Sefard";
const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "app", "src", "main", "assets", "prayers_sefard.json");

// Each prayer = a list of section paths (Hebrew titles as they appear in Sefaria's index).
// A path that points at a parent node expands to all of its leaves, in order.
const PRAYERS = [
  // ---- daily ----
  { id: "modeh_ani", title: "מודה אני", subtitle: "עם הקימה", category: "daily", icon: "sun",
    paths: [["סדר השכמת הבוקר", "מודה אני"]] },
  { id: "tefillin", title: "סדר הנחת תפילין", subtitle: "טלית ותפילין", category: "daily", icon: "tefillin",
    paths: [["סדר השכמת הבוקר", "טלית"], ["סדר השכמת הבוקר", "תפילין"]] },
  { id: "shacharit", title: "שחרית מלאה", subtitle: "לימות החול", category: "daily", icon: "book",
    paths: [["שחרית לימות החול"]], skip: ["לשני וחמישי", "קריאת התורה"] },
  { id: "shema", title: "קריאת שמע", subtitle: "וברכותיה", category: "daily", icon: "shield",
    paths: [["שחרית לימות החול", "ק\"ש וברכותיה"]] },
  { id: "mincha", title: "תפילת מנחה", subtitle: "לימות החול", category: "daily", icon: "flame",
    paths: [["מנחה לימות החול"]], skip: ["קריאה לתענית ציבור"] },
  { id: "arvit", title: "תפילת ערבית", subtitle: "לימות החול", category: "daily", icon: "moon",
    paths: [["ערבית לימות החול"]], skip: ["ערבית למוצאי שבת", "ספירת העומר"] },
  { id: "bedtime_shema", title: "קריאת שמע שעל המיטה", subtitle: "לפני השינה", category: "daily", icon: "moon",
    paths: [["קריאת שמע שעל המיטה"]] },
  { id: "birkat_hamazon", title: "ברכת המזון", subtitle: "אחרי הסעודה", category: "daily", icon: "bread",
    paths: [["סדר ברכת המזון", " ברכת המזון"]] },

  // ---- shabbat & holidays ----
  { id: "candles", title: "הדלקת נרות שבת", subtitle: "ערב שבת", category: "shabbat", icon: "candle",
    paths: [["סדר הדלקת נרות שבת"]] },
  { id: "kabbalat_shabbat", title: "קבלת שבת", subtitle: "לכה דודי", category: "shabbat", icon: "sun",
    paths: [["קבלת שבת"]] },
  { id: "arvit_shabbat", title: "ערבית של שבת", subtitle: "ליל שבת", category: "shabbat", icon: "moon",
    paths: [["ערבית של שבת"]] },
  { id: "kiddush_night", title: "קידוש ליל שבת", subtitle: "שלום עליכם, אשת חיל, קידוש", category: "shabbat", icon: "cup",
    paths: [["סעודת ליל שבת", "שלום עליכם"], ["סעודת ליל שבת", "אשת חיל"], ["סעודת ליל שבת", "קידוש ליל שבת"]] },
  { id: "shacharit_shabbat", title: "שחרית של שבת", subtitle: "פסוקי דזמרה, שמע, עמידה", category: "shabbat", icon: "book",
    paths: [["שחרית של שבת", "פסוקי דזמרה"], ["שחרית של שבת", "ק\"ש וברכותיה"], ["שחרית של שבת", "תפילת עמידה"]] },
  { id: "musaf_shabbat", title: "מוסף של שבת", subtitle: "", category: "shabbat", icon: "book",
    paths: [["מוסף של שבת"]] },
  { id: "kiddush_day", title: "קידוש ליום השבת", subtitle: "", category: "shabbat", icon: "cup",
    paths: [["סעודת שבת", "קידוש ליום השבת"]] },
  { id: "mincha_shabbat", title: "מנחה לשבת", subtitle: "", category: "shabbat", icon: "flame",
    paths: [["מנחה לשבת קודש", "קרבנות"], ["מנחה לשבת קודש", "תפילת עמידה"]] },
  { id: "havdala", title: "הבדלה", subtitle: "מוצאי שבת", category: "shabbat", icon: "candle",
    paths: [["למוצאי שבת", "סדר הבדלה"]] },
  { id: "hallel", title: "הלל", subtitle: "ראש חודש ומועדים", category: "shabbat", icon: "sun",
    paths: [["לראש חודש", "סדר הלל"]] },
  { id: "sefirat_haomer", title: "ספירת העומר", subtitle: "", category: "shabbat", icon: "sparkle",
    paths: [["ערבית לימות החול", "ספירת העומר"]] },

  // ---- special ----
  { id: "tefilat_haderech", title: "תפילת הדרך", subtitle: "בדרך ובטיסה", category: "special", icon: "shield",
    paths: [["ברכות הנהנין", "תפלת הדרך"], ["ברכות הנהנין", "תפילת הדרך לטסים באוירון"]] },
  { id: "mi_sheberach_choleh", title: "מי שברך לחולה", subtitle: "לרפואה שלמה", category: "special", icon: "heart",
    paths: [["שחרית של שבת", "מי שברך לחולה"]] },
  { id: "hagomel", title: "ברכת הגומל", subtitle: "", category: "special", icon: "sparkle",
    paths: [["שחרית של שבת", "ברכת הגומל"]] },
  { id: "birkot_hanehenin", title: "ברכות הנהנין", subtitle: "על המאכלים", category: "special", icon: "bread",
    paths: [["ברכות הנהנין", "בורא פרי העץ"], ["ברכות הנהנין", "בורא פרי האדמה"], ["ברכות הנהנין", "ברכת שהכל"],
            ["ברכות הנהנין", "בורא נפשות"], ["ברכות הנהנין", "ברכה אחרונה מעין שלוש"], ["ברכות הנהנין", "ברכת שהחיינו"]] },
  { id: "kiddush_levana", title: "קידוש לבנה", subtitle: "", category: "special", icon: "moon",
    paths: [["קידוש לבנה"]] },
  { id: "perek_shira", title: "פרק שירה", subtitle: "", category: "special", icon: "sparkle",
    paths: [["תוספות אחרי שחרית", "פרק שירה"]] },
  { id: "ikarim", title: "שלשה עשר עיקרים", subtitle: "אני מאמין", category: "special", icon: "shield",
    paths: [["תוספות אחרי שחרית", "שלשה עשר עיקרים"]] },
  { id: "zechirot", title: "שש זכירות", subtitle: "", category: "special", icon: "book",
    paths: [["תוספות אחרי שחרית", "שש זכירות"]] },
  { id: "parashat_haman", title: "פרשת המן", subtitle: "לפרנסה", category: "special", icon: "bread",
    paths: [["תוספות אחרי שחרית", "פרשת המן"]] },
  { id: "bakashot", title: "בקשות לאחר התפילה", subtitle: "", category: "special", icon: "sparkle",
    paths: [["תוספות אחרי שחרית", "בקשות לאחר התפילה"]] },
];

async function getJson(url) {
  const res = await fetch(url, { headers: { accept: "application/json" } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

function findNode(nodes, heTitle) {
  return (nodes || []).find((n) => (n.heTitle || "").trim() === heTitle.trim());
}

/** Resolves a Hebrew path to the list of leaf nodes with their English ref paths. */
function resolve(schema, path, skip = []) {
  let node = schema;
  const en = [];
  for (const he of path) {
    const next = findNode(node.nodes, he);
    if (!next) throw new Error(`node not found: ${path.join(" / ")} (at "${he}")`);
    node = next;
    en.push(next.title);
  }
  const leaves = [];
  const walk = (n, enPath, hePath) => {
    if (n.nodes && n.nodes.length) {
      for (const c of n.nodes) {
        if (skip.includes((c.heTitle || "").trim())) continue;
        walk(c, [...enPath, c.title], [...hePath, c.heTitle]);
      }
    } else {
      leaves.push({ ref: [ROOT, ...enPath].join(", "), heading: hePath[hePath.length - 1].trim() });
    }
  };
  walk(node, en, path);
  return leaves;
}

function flatten(x, out = []) {
  if (Array.isArray(x)) x.forEach((y) => flatten(y, out));
  else if (typeof x === "string") out.push(x);
  return out;
}

function clean(html) {
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/\{[פס]\}/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

async function fetchSection(leaf) {
  const url = `https://www.sefaria.org/api/texts/${encodeURIComponent(leaf.ref)}?context=0&commentary=0&pad=0`;
  const data = await getJson(url);
  const paragraphs = flatten(data.he).map(clean).filter((p) => p.length > 0);
  return { heading: leaf.heading, paragraphs };
}

const index = await getJson(`https://www.sefaria.org/api/v2/index/${encodeURIComponent(ROOT.replace(/ /g, "_"))}`);
const schema = index.schema;

const prayers = [];
for (const p of PRAYERS) {
  const sections = [];
  for (const path of p.paths) {
    const leaves = resolve(schema, path, p.skip || []);
    for (const leaf of leaves) {
      try {
        const s = await fetchSection(leaf);
        if (s.paragraphs.length) sections.push(s);
        process.stdout.write(`  ${p.id}: ${leaf.heading} (${s.paragraphs.length})\n`);
      } catch (e) {
        process.stdout.write(`  !! ${p.id}: ${leaf.ref}: ${e.message}\n`);
      }
      await new Promise((r) => setTimeout(r, 150));
    }
  }
  prayers.push({ id: p.id, title: p.title, subtitle: p.subtitle, category: p.category, icon: p.icon, sections });
}

mkdirSync(dirname(OUT), { recursive: true });
const json = JSON.stringify({ nusach: "ספרד", source: "Sefaria, Siddur Sefard", prayers });
writeFileSync(OUT, json, "utf8");
console.log(`wrote ${OUT} (${(json.length / 1024).toFixed(0)} KB, ${prayers.length} prayers)`);
