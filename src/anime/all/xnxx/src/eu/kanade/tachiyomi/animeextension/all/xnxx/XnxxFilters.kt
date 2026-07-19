package eu.kanade.tachiyomi.animeextension.all.xnxx

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object XnxxFilters {

    class TagFilter : AnimeFilter.Text("Tag")

    class SortFilter : AnimeFilter.Select<String>(
        "Sort by",
        arrayOf("Relevance", "Upload date", "Rating", "Length", "Views")
    ) {
        fun toUri(): String = when (state) {
            0 -> "relevance"
            1 -> "date"
            2 -> "rating"
            3 -> "length"
            4 -> "views"
            else -> "relevance"
        }
    }

    class DurationFilter : AnimeFilter.Select<String>(
        "Duration",
        arrayOf("All", "1-3 min", "3-10 min", "10-20 min", "20+ min")
    ) {
        fun toUri(): String = when (state) {
            0 -> "all"
            1 -> "1-3min"
            2 -> "3-10min"
            3 -> "10-20min"
            4 -> "20min+"
            else -> "all"
        }
    }

    fun getFilters(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Search by text overrides the Tag filter"),
        TagFilter(),
        SortFilter(),
        DurationFilter()
    )
}
