package eu.kanade.tachiyomi.animeextension.en.hentaicity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaiCityFilters {
    class CategoryFilter(name: String, private val categories: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, categories.map { it.first }.toTypedArray()) {
        val selected: String
            get() = categories[state].second
    }

    val CATEGORIES = arrayOf(
        Pair("All Categories", ""),
        Pair("3D", "3d"),
        Pair("Anal", "anal"),
        Pair("Babe", "babe"),
        Pair("Big Dick", "bigdick"),
        Pair("Big Tits", "bigtits"),
        Pair("Blowjob", "blowjob"),
        Pair("Bondage", "bondage"),
        Pair("Cosplay", "cosplay"),
        Pair("Creampie", "creampie"),
        Pair("Cumshot", "cumshot"),
        Pair("Facial", "facial"),
        Pair("Gangbang", "gangbang"),
        Pair("Hardcore", "hardcore"),
        Pair("Harem", "harem"),
        Pair("Interracial", "interracial"),
        Pair("Lesbian", "lesbian"),
        Pair("Masturbation", "masturbation"),
        Pair("MILF", "milf"),
        Pair("Monster", "monster"),
        Pair("Nurse", "nurse"),
        Pair("POV", "pov"),
        Pair("Schoolgirl", "schoolgirl"),
        Pair("Teen", "teen"),
        Pair("Tentacle", "tentacle"),
        Pair("Threesome", "threesome"),
        Pair("Uncensored", "uncensored")
    )
}
