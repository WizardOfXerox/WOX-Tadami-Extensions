package eu.kanade.tachiyomi.animeextension.en.forcedcinema

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object ForcedCinemaFilters {
    class SortFilter : AnimeFilter.Select<String>(
        "Sort By",
        arrayOf("Newest", "Oldest", "Title A-Z", "Title Z-A"),
    ) {
        fun toOrderBy() = when (state) {
            2, 3 -> "title"
            else -> "date"
        }
        fun toOrder() = when (state) {
            1, 2 -> "asc"
            else -> "desc"
        }
    }

    class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        CATEGORIES.map { it.first }.toTypedArray(),
    ) {
        val selectedId: Int
            get() = CATEGORIES[state].second
    }

    class TagFilter : AnimeFilter.Select<String>(
        "Tag",
        TAGS.map { it.first }.toTypedArray(),
    ) {
        val selectedId: Int
            get() = TAGS[state].second
    }

    val CATEGORIES = arrayOf(
        Pair("All Categories", 0),
        Pair("BDSM", 2513),
        Pair("Explicit", 1305),
        Pair("Gay", 1306),
        Pair("Hentai", 1303),
        Pair("Incest", 3704),
        Pair("Lesbian", 1349),
        Pair("Mainstream", 1304),
        Pair("Reverse Rape", 1307),
    )

    val TAGS = arrayOf(
        Pair("All Tags", 0),
        Pair("brunette", 109),
        Pair("blonde", 41),
        Pair("from behind", 36),
        Pair("Bestcutscenes", 229),
        Pair("against the floor", 149),
        Pair("raped in bed", 307),
        Pair("struggling", 446),
        Pair("gang rape", 35),
        Pair("vintage", 15),
        Pair("rape", 53),
        Pair("young", 93),
        Pair("latina", 61),
        Pair("Asian", 20),
        Pair("screaming", 673),
        Pair("dubious consent", 173),
        Pair("tied hands", 297),
        Pair("crying", 316),
        Pair("Aznude", 247),
        Pair("rape attempt", 121),
        Pair("unwilling", 98),
        Pair("kidnapped", 129),
        Pair("celebrity", 40),
        Pair("forced anal", 81),
        Pair("bent over", 185),
        Pair("bondage", 49),
        Pair("forced oral", 1288),
        Pair("forced strip", 243),
        Pair("begging to stop", 253),
        Pair("home intrusion", 27),
        Pair("resignation look", 262),
        Pair("redhead", 113),
        Pair("ripped shirt", 391),
        Pair("beaten", 419),
        Pair("Cutscenes.net", 482),
        Pair("blowjob", 1329),
        Pair("interrupted", 742),
        Pair("violent", 16),
        Pair("reluctant", 97),
        Pair("gagged", 45),
        Pair("gropped", 144),
        Pair("coerced", 306),
        Pair("multiple rape victims", 207),
        Pair("forced to watch", 100),
        Pair("prison rape", 217),
        Pair("spousal rape", 96),
        Pair("raped in front of husband or boyfriend", 1274),
        Pair("rape and murder", 442),
        Pair("interracial", 71),
        Pair("raped in forest", 58),
        Pair("Rapetube", 84),
        Pair("all fours", 348),
        Pair("roughed", 780),
        Pair("chained", 381),
        Pair("torture", 330),
        Pair("unconscious", 234),
        Pair("perverted old man", 416),
        Pair("showing disconfort or not liking it", 5784),
        Pair("kneeled", 721),
        Pair("hazel", 548),
        Pair("raped at gunpoint", 89),
        Pair("forced lesbianism", 186),
        Pair("raped by monster", 65),
        Pair("milf", 126),
        Pair("filmed while being raped", 160),
        Pair("aoh", 2514),
        Pair("ebony", 157),
        Pair("double rape", 26),
        Pair("tied to bed", 46),
        Pair("raped on table", 244),
        Pair("sex slave", 206),
        Pair("humiliation", 176),
        Pair("big tits", 59),
        Pair("rape with object", 87),
        Pair("submissive girl", 2685),
        Pair("rapist killed", 792),
        Pair("hand on mouth", 124),
        Pair("raped at knifepoint", 476),
        Pair("against the wall", 389),
        Pair("blackmail", 141),
        Pair("JustRapeHer", 367),
        Pair("explicit oral", 83),
        Pair("school uniform", 188),
        Pair("gore", 233),
        Pair("compilation", 1301),
        Pair("male domination", 2684),
        Pair("raped in car", 183),
        Pair("liking it at the end", 717),
        Pair("indiferent during rape", 578),
        Pair("sexual abuse", 54),
        Pair("drugged", 34),
        Pair("forced prostitution", 187),
        Pair("prostitute", 3047),
        Pair("molested", 240),
        Pair("pornstar", 1716),
        Pair("flashback", 3079),
        Pair("raped by soldiers", 208),
        Pair("rape off camera or implied", 2675),
        Pair("handcuffed", 91),
        Pair("animation", 438),
        Pair("brother and sister", 4239),
    )
}
