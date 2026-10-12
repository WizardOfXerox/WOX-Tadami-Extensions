package eu.kanade.tachiyomi.animeextension.en.hentaihaven

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaiHavenFilters {
    class GenreFilter(name: String, private val genres: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, genres.map { it.first }.toTypedArray()) {
        val selected: String
            get() = genres[state].second
    }

    val GENRES = arrayOf(
        Pair("All Genres", ""),
        Pair("BBW", "bbw"),
        Pair("BDSM", "bdsm"),
        Pair("Ecchi", "ecchi"),
        Pair("Femdom", "femdom"),
        Pair("Harem", "harem"),
        Pair("MILF", "milf"),
        Pair("Tentacle", "tentacle"),
        Pair("Uncensored Hentai", "uncensored-hentai"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri")
    )
}
