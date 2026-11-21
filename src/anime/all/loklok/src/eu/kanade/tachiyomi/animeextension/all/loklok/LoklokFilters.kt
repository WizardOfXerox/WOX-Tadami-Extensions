package eu.kanade.tachiyomi.animeextension.all.loklok

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LoklokFilters {

    class TypeFilter : AnimeFilter.Select<String>(
        "Type",
        arrayOf(
            "All",
            "TV Series",
            "Movie",
            "Anime",
            "Variety Show",
            "Talk Show",
            "Documentary",
        ),
    ) {
        fun getValue() = when (state) {
            1 -> "TV,SETI,VARIETY,TALK,COMIC,DOCUMENTARY"
            2 -> "MOVIE,TVSPECIAL"
            3 -> "COMIC"
            4 -> "VARIETY,TALK"
            5 -> "TALK"
            6 -> "DOCUMENTARY"
            else -> "MOVIE,TV,VARIETY,COMIC,DOCUMENTARY,TVSPECIAL,MINISERIES,SETI,TALK"
        }

        fun getLabel() = when (state) {
            1 -> "TV Series"
            2 -> "Movie"
            3 -> "Anime"
            4 -> "Variety Show"
            5 -> "Talk Show"
            6 -> "Documentary"
            else -> "ALL"
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
            "Other",
        ),
    ) {
        fun getValue() = when (state) {
            1 -> "61"
            2 -> "53"
            3 -> "41"
            4 -> "60"
            5 -> "44"
            6 -> "57"
            7 -> "34,35,37,38,39,43,45,46,48,50,54,55,58,60,62"
            8 -> "32"
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
            "Romance",
            "Action",
            "Fantasy",
            "Animation",
            "Suspense",
            "Sci-Fi",
            "Horror",
            "Comedy",
            "Crime",
            "Adventure",
            "Thriller",
            "LGBTQ",
            "Drama",
            "Variety Show",
            "Family",
            "Musical",
            "War",
            "Catastrophe",
            "Documentary",
            "Other",
        ),
    ) {
        fun getValue() = when (state) {
            1 -> "18"
            2 -> "1"
            3 -> "10"
            4 -> "3"
            5 -> "16"
            6 -> "19"
            7 -> "13"
            8 -> "5"
            9 -> "6"
            10 -> "2"
            11 -> "23"
            12 -> "65"
            13 -> "8"
            14 -> "17"
            15 -> "9"
            16 -> "14,15,63"
            17 -> "24"
            18 -> "64"
            19 -> "7"
            20 -> "4,7,11,12,17,20,21,22,25"
            else -> ""
        }
    }

    class YearFilter : AnimeFilter.Select<String>(
        "Time Period",
        arrayOf(
            "All",
            "2026",
            "2025",
            "2024",
            "2023",
            "2022",
            "2021",
            "2020",
            "2019",
            "2018",
            "2017",
            "2016",
            "2015-2011",
            "2010-2000",
            "Before 2000",
        ),
    ) {
        fun getValue() = when (state) {
            1 -> "2026,2026"
            2 -> "2025,2025"
            3 -> "2024,2024"
            4 -> "2023,2023"
            5 -> "2022,2022"
            6 -> "2021,2021"
            7 -> "2020,2020"
            8 -> "2019,2019"
            9 -> "2018,2018"
            10 -> "2017,2017"
            11 -> "2016,2016"
            12 -> "2011,2015"
            13 -> "2000,2010"
            14 -> "1900,1999"
            else -> ""
        }
    }

    class SortFilter : AnimeFilter.Select<String>(
        "Sort by",
        arrayOf(
            "Popularity",
            "Recent",
            "High Rating",
        ),
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
        SortFilter(),
    )
}
