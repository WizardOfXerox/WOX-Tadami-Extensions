package eu.kanade.tachiyomi.animeextension.all.loklok

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LoklokFilters {

    class TypeFilter : AnimeFilter.Select<String>(
        "Type",
        arrayOf(
            "All",
            "Movie",
            "TV Series",
            "Shorts",
            "Anime",
            "Variety Show",
            "Talk Show",
            "Documentary"
        )
    ) {
        fun getValue() = when (state) {
            1 -> "MOVIE,TVSPECIAL"
            2 -> "TV,SETI,VARIETY,TALK,COMIC,DOCUMENTARY"
            3 -> "MINISERIES" // Shorts
            4 -> "COMIC" // Anime
            5 -> "VARIETY,TALK"
            6 -> "TALK" // Talk Show
            7 -> "DOCUMENTARY"
            else -> "MOVIE,TV,VARIETY,COMIC,DOCUMENTARY,TVSPECIAL,MINISERIES,SETI,TALK"
        }
    }

    class RegionFilter : AnimeFilter.Select<String>(
        "Region",
        arrayOf(
            "All",
            "America",
            "Korea",
            "Indonesia",
            "U.K",
            "Japan",
            "Thailand",
            "Europe",
            "China",
            "India",
            "Australia",
            "Other"
        )
    ) {
        fun getValue() = when (state) {
            1 -> "61"
            2 -> "53"
            3 -> "41"
            4 -> "60"
            5 -> "44"
            6 -> "57"
            7 -> "37,60,58,50,54,55,48,46,45,34,35,38,39,43,62"
            8 -> "32,56"
            9 -> "40"
            10 -> "27"
            11 -> "26,28,29,30,31,33,36,42,47,49,59"
            else -> ""
        }
    }

    class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf(
            "All",
            "Drama",
            "Suspense",
            "Sci-Fi",
            "Action",
            "Romance",
            "Fantasy",
            "Horror",
            "Comedy",
            "Crime",
            "Adventure",
            "Animation",
            "Thriller",
            "Family",
            "Musical",
            "War",
            "LGBTQ",
            "Catastrophe",
            "Other"
        )
    ) {
        fun getValue() = when (state) {
            1 -> "8"
            2 -> "16"
            3 -> "19"
            4 -> "1"
            5 -> "18"
            6 -> "10"
            7 -> "13"
            8 -> "5"
            9 -> "6"
            10 -> "2"
            11 -> "3"
            12 -> "23"
            13 -> "9"
            14 -> "63,14,15"
            15 -> "24"
            16 -> "65"
            17 -> "64"
            18 -> "7,4,11,12,17,22,21,20,25"
            else -> ""
        }
    }

    class YearFilter : AnimeFilter.Select<String>(
        "Time Period",
        arrayOf(
            "All",
            "2026",
            "2021",
            "2020",
            "2019",
            "2018",
            "2017",
            "2016",
            "2015-2011",
            "2010-2000",
            "Before 2000"
        )
    ) {
        fun getValue() = when (state) {
            1 -> "2026,2026"
            2 -> "2021,2021"
            3 -> "2020,2020"
            4 -> "2019,2019"
            5 -> "2018,2018"
            6 -> "2017,2017"
            7 -> "2016,2016"
            8 -> "2011,2015"
            9 -> "2000,2010"
            10 -> "1900,2009"
            else -> ""
        }
    }

    class SortFilter : AnimeFilter.Select<String>(
        "Sort by",
        arrayOf(
            "Popularity",
            "Recent",
            "High Rating"
        )
    ) {
        fun getValue() = when (state) {
            1 -> "up"
            2 -> "score"
            else -> "count"
        }
    }

    fun getFilters() = AnimeFilterList(
        TypeFilter(),
        RegionFilter(),
        CategoryFilter(),
        YearFilter(),
        SortFilter()
    )
}
