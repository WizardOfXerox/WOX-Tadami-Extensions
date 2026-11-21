package eu.kanade.tachiyomi.animeextension.es.animefenix

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeFenixFilters {
    open class QueryPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(
            displayName,
            vals.map { it.first }.toTypedArray(),
        ) {
        fun toQueryPart(name: String) = vals[state].second.takeIf { it.isNotEmpty() }?.let { "&$name=${android.net.Uri.encode(vals[state].second)}" } ?: ""
    }

    private inline fun <reified R> AnimeFilterList.asQueryPart(name: String): String = (this.getFirst<R>() as QueryPartFilter).toQueryPart(name)

    private inline fun <reified R> AnimeFilterList.getFirst(): R = this.filterIsInstance<R>().first()

    private fun String.changePrefix() = this.takeIf { it.startsWith("&") }?.let { this.replaceFirst("&", "?") } ?: this

    data class FilterSearchParams(val filter: String = "") {
        fun getQuery() = filter.changePrefix()
    }

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()
        return FilterSearchParams(
            filters.asQueryPart<StateFilter>("status") +
                filters.asQueryPart<TypesFilter>("type") +
                filters.asQueryPart<GenresFilter>("genre") +
                filters.asQueryPart<SortFilter>("sort"),
        )
    }

    val FILTER_LIST get() = AnimeFilterList(
        AnimeFilter.Header("La busqueda por texto ignora el filtro"),
        StateFilter(),
        TypesFilter(),
        GenresFilter(),
        SortFilter(),
    )

    class StateFilter : QueryPartFilter("Estado", AnimeFenixFiltersData.STATE)
    class TypesFilter : QueryPartFilter("Tipo", AnimeFenixFiltersData.TYPES)
    class GenresFilter : QueryPartFilter("Género", AnimeFenixFiltersData.GENRES)
    class SortFilter : QueryPartFilter("Orden", AnimeFenixFiltersData.SORT)

    private object AnimeFenixFiltersData {
        val STATE = arrayOf(
            Pair("Todos", ""),
            Pair("En emisión", "airing"),
            Pair("Finalizado", "finished"),
            Pair("Próximamente", "upcoming"),
        )

        val TYPES = arrayOf(
            Pair("Todos", ""),
            Pair("TV Anime", "TV Anime"),
            Pair("Película", "Película"),
            Pair("OVA", "OVA"),
            Pair("ONA", "ONA"),
            Pair("Especial", "Especial"),
        )

        val SORT = arrayOf(
            Pair("Predeterminado", ""),
            Pair("Nombre", "title"),
            Pair("Mejor valorados", "rating"),
            Pair("Más recientes", "newest"),
            Pair("Más antiguos", "oldest"),
        )

        val GENRES = arrayOf(
            Pair("Todos", ""),
            Pair("3D", "3d"),
            Pair("Acción", "accion"),
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("Antropomórfico", "antropomorfico"),
            Pair("Artes Marciales", "artes-marciales"),
            Pair("Aventura", "aventura"),
            Pair("Bondage", "bondage"),
            Pair("Carreras", "carreras"),
            Pair("Casadas", "casadas"),
            Pair("Chikan", "chikan"),
            Pair("Ciencia Ficción", "ciencia-ficcion"),
            Pair("Comedia", "comedia"),
            Pair("Deportes", "deportes"),
            Pair("Detectives", "detectives"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Elenco Adulto", "elenco-adulto"),
            Pair("Elfas", "elfas"),
            Pair("Enfermeras", "enfermeras"),
            Pair("Escolares", "escolares"),
            Pair("Espacial", "espacial"),
            Pair("Fantasía", "fantasia"),
            Pair("Futanari", "futanari"),
            Pair("Gal", "gal"),
            Pair("Gore", "gore"),
            Pair("Gourmet", "gourmet"),
            Pair("Hardcore", "hardcore"),
            Pair("Harem", "harem"),
            Pair("Histórico", "historico"),
            Pair("Idols (Hombre)", "idols-hombre"),
            Pair("Idols (Mujer)", "idols-mujer"),
            Pair("Incesto", "incesto"),
            Pair("Infantil", "infantil"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Juegos Estrategia", "juegos-estrategia"),
            Pair("Juegos Sexuales", "juegos-sexuales"),
            Pair("Mahou Shoujo", "mahou-shoujo"),
            Pair("Maids", "maids"),
            Pair("Mecha", "mecha"),
            Pair("Milfs", "milfs"),
            Pair("Militar", "militar"),
            Pair("Misterio", "misterio"),
            Pair("Mitología", "mitologia"),
            Pair("Música", "musica"),
            Pair("Netorare", "netorare"),
            Pair("Ninfomanía", "ninfomania"),
            Pair("Ninjas", "ninjas"),
            Pair("Orgías", "orgias"),
            Pair("Oyakodon", "oyakodon"),
            Pair("Paizuri", "paizuri"),
            Pair("Parodia", "parodia"),
            Pair("Petit", "petit"),
            Pair("Psicológico", "psicologico"),
            Pair("Recuentos de la Vida", "recuentos-de-la-vida"),
            Pair("Romance", "romance"),
            Pair("Samurái", "samurai"),
            Pair("Seinen", "seinen"),
            Pair("Shota", "shota"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Sobrenatural", "sobrenatural"),
            Pair("Softcore", "softcore"),
            Pair("Succubus", "succubus"),
            Pair("Superpoderes", "superpoderes"),
            Pair("Suspenso", "suspenso"),
            Pair("Teacher", "teacher"),
            Pair("Tentáculos", "tentaculos"),
            Pair("Terror", "terror"),
            Pair("Tetonas", "tetonas"),
            Pair("Threesome", "threesome"),
            Pair("Vampiros", "vampiros"),
            Pair("Vanilla", "vanilla"),
            Pair("Violación", "violacion"),
            Pair("Vírgenes", "virgenes"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        )
    }
}
