package eu.kanade.tachiyomi.animeextension.en.hentaigasm

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object HentaigasmFilters {
    class GenreFilter(name: String, private val genres: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, genres.map { it.first }.toTypedArray()) {
        val selected: String
            get() = genres[state].second
    }

    val GENRES = arrayOf(
        Pair("All Genres", ""),
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
