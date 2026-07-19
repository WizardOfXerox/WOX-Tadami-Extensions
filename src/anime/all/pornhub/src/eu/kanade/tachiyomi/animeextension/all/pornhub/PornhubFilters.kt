package eu.kanade.tachiyomi.animeextension.all.pornhub

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object PornhubFilters {

    class OrderFilter : AnimeFilter.Select<String>(
        "Sort by",
        arrayOf("Most Viewed", "Most Recent", "Top Rated", "Hot")
    ) {
        fun toUri(): String = when (state) {
            0 -> "mv"
            1 -> "mr"
            2 -> "tr"
            3 -> "ht"
            else -> "mv"
        }
    }

    class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf(
            "All", "Amateur", "Anal", "Asian", "BBW", "BDSM", "Babe", "Behind The Scenes",
            "Big Ass", "Big Dick", "Big Tits", "Blonde", "Blowjob", "Brunette", "Ebony",
            "Family Roleplay", "Fetish", "Fisting", "French", "German", "Goth", "Group Sex",
            "Hentai", "Interracial", "Italian", "Japanese", "Latina", "MILF", "Masturbation",
            "Mature", "POV", "Parody", "Pissing", "Public", "Redhead", "Rough Sex", "Russian",
            "School", "Solo Female", "Solo Male", "Squirt", "Threesome", "Toys", "Vintage"
        )
    ) {
        fun toUri(): String = when (state) {
            0 -> ""
            1 -> "amateur"
            2 -> "anal"
            3 -> "asian"
            4 -> "bbw"
            5 -> "bdsm"
            6 -> "babe"
            7 -> "behind-the-scenes"
            8 -> "big-ass"
            9 -> "big-dick"
            10 -> "big-tits"
            11 -> "blonde"
            12 -> "blowjob"
            13 -> "brunette"
            14 -> "ebony"
            15 -> "family-roleplay"
            16 -> "fetish"
            17 -> "fisting"
            18 -> "french"
            19 -> "german"
            20 -> "goth"
            21 -> "group-sex"
            22 -> "hentai"
            23 -> "interracial"
            24 -> "italian"
            25 -> "japanese"
            26 -> "latina"
            27 -> "milf"
            28 -> "masturbation"
            29 -> "mature"
            30 -> "pov"
            31 -> "parody"
            32 -> "pissing"
            33 -> "public"
            34 -> "redhead"
            35 -> "rough-sex"
            36 -> "russian"
            37 -> "school"
            38 -> "solo-female"
            39 -> "solo-male"
            40 -> "squirt"
            41 -> "threesome"
            42 -> "toys"
            43 -> "vintage"
            else -> ""
        }
    }

    fun getFilters() = AnimeFilterList(
        OrderFilter(),
        CategoryFilter()
    )
}
