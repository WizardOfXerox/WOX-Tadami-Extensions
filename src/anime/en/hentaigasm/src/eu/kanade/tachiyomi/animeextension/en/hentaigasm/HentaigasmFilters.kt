package eu.kanade.tachiyomi.animeextension.en.hentaigasm

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaigasmFilters {
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val selected: String
            get() = vals[state].second
    }

    class SortFilter : UriPartFilter("Sort By", SORT_OPTIONS)

    class GenreFilter(name: String, genres: Array<Pair<String, String>>) : UriPartFilter(name, genres)

    val SORT_OPTIONS = arrayOf(
        Pair("Latest (Date)", "date"),
        Pair("Popular (Most Viewed)", "views"),
        Pair("Random", "rand"),
    )

    val GENRES = arrayOf(
        Pair("All Genres", ""),
        Pair("New Release", "hentai/new-hentai-release"),
        Pair("Preview", "preview"),
        Pair("3D", "3d"),
        Pair("Anal", "anal"),
        Pair("Blowjob", "blowjob"),
        Pair("Bondage", "bondage"),
        Pair("Boobjob", "boobjob"),
        Pair("Cheating", "cheating"),
        Pair("Creampie", "creampie"),
        Pair("Demon", "demon"),
        Pair("Exclusive", "exclusive"),
        Pair("Exhibition", "exhibition"),
        Pair("Fantasy", "fantasy"),
        Pair("Footjob", "footjob"),
        Pair("Futanari", "futanari"),
        Pair("Gangbang", "gangbang"),
        Pair("Harem", "harem"),
        Pair("Incest", "incest"),
        Pair("Maid", "maid"),
        Pair("Masturbation", "masturbation"),
        Pair("Monster", "monster"),
        Pair("Motion Anime", "motion-anime"),
        Pair("Nurse", "nurse"),
        Pair("Rape", "rape"),
        Pair("School", "school"),
        Pair("Season", "season"),
        Pair("Series", "series"),
        Pair("Sex Toy", "sex-toy"),
        Pair("Swimsuit", "swimsuit"),
        Pair("Tentacle", "tentacle"),
        Pair("Uncensored", "uncensored"),
        Pair("Vanilla", "vanilla"),
        Pair("Virgin", "virgin"),
        Pair("X-Ray", "x-ray"),
        Pair("Yuri", "yuri"),
    )
}
