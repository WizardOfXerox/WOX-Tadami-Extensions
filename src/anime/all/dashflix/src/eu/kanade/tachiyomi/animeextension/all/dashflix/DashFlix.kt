package eu.kanade.tachiyomi.animeextension.all.dashflix

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DashFlix : AnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "DASHFLIX"
    override val baseUrl = "https://dashflix.top"
    override val lang = "all"
    override val supportsLatest = true

    private val tmdbApiKey = "4f599baa15d072c9de346b2816a131b8"
    private val tmdbBaseUrl = "https://api.themoviedb.org/3"
    private val tmdbImgUrl = "https://image.tmdb.org/t/p/w500"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "$baseUrl/")
                .build()
            chain.proceed(request)
        }
        .build()

    // ============================== POPULAR ==============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$tmdbBaseUrl/discover/movie?api_key=$tmdbApiKey&language=en-US&page=$page&sort_by=popularity.desc", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val parsed = json.decodeFromString<TmdbPageDto>(response.body.string())
        val animeList = parsed.results.map { dto ->
            SAnime.create().apply {
                url = "/player.html?id=${dto.id}"
                title = dto.title ?: dto.name ?: "Unknown Movie"
                thumbnail_url = if (dto.poster_path != null) "$tmdbImgUrl${dto.poster_path}" else null
            }
        }
        val hasNextPage = parsed.page < parsed.total_pages
        return AnimesPage(animeList, hasNextPage)
    }

    // ============================== LATEST ==============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$tmdbBaseUrl/discover/movie?api_key=$tmdbApiKey&language=en-US&page=$page&sort_by=primary_release_date.desc", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== SEARCH & FILTERS ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            return GET("$tmdbBaseUrl/search/movie?api_key=$tmdbApiKey&language=en-US&query=$encodedQuery&page=$page", headers)
        }

        var genre = ""
        var sortBy = "popularity.desc"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                is SortFilter -> sortBy = filter.toUriPart()
                else -> {}
            }
        }

        val url = StringBuilder("$tmdbBaseUrl/discover/movie?api_key=$tmdbApiKey&language=en-US&page=$page&sort_by=$sortBy")
        if (genre.isNotEmpty()) {
            url.append("&with_genres=").append(genre)
        }

        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== DETAILS ==============================

    override fun animeDetailsParse(response: Response): SAnime = SAnime.create().apply {
        title = "DASHFLIX Movie"
        description = "Stream HD movies and TV shows on DASHFLIX."
        initialized = true
    }

    // ============================== EPISODES ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val urlStr = response.request.url.toString()
        val tmdbId = Regex("id=(\\d+)").find(urlStr)?.groupValues?.get(1) ?: "550"
        
        return listOf(
            SEpisode.create().apply {
                url = "/player.html?id=$tmdbId"
                name = "Full Movie / Episode 1"
                episode_number = 1.0f
            }
        )
    }

    // ============================== VIDEO RESOLUTION ==============================

    override fun videoListParse(response: Response): List<Video> {
        val urlStr = response.request.url.toString()
        val tmdbId = Regex("id=(\\d+)").find(urlStr)?.groupValues?.get(1) ?: "550"

        val servers = listOf(
            Pair("VSEmbed", "https://vsembed.ru/embed/movie/$tmdbId"),
            Pair("VidLink", "https://vidlink.pro/movie/$tmdbId"),
            Pair("2Embed", "https://2embed.cc/embed/$tmdbId"),
            Pair("MultiEmbed", "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"),
            Pair("VidFast", "https://vidfast.pro/movie/$tmdbId?autoPlay=true"),
            Pair("VSEmbed SU", "https://vsembed.su/embed/movie/$tmdbId")
        )

        return servers.map { (serverName, streamUrl) ->
            Video(streamUrl, serverName, streamUrl, headers)
        }
    }

    fun relatedAnimeListRequest(anime: SAnime): Request =
        GET("$tmdbBaseUrl/discover/movie?api_key=$tmdbApiKey&language=en-US&page=1&sort_by=popularity.desc", headers)

    // ============================== ROBUST FILTERS ==============================

    override fun getFilterList() = AnimeFilterList(
        SortFilter(),
        GenreFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class GenreFilter : UriPartFilter("Genre", arrayOf(
        Pair("All", ""),
        Pair("Action", "28"),
        Pair("Adventure", "12"),
        Pair("Animation", "16"),
        Pair("Comedy", "35"),
        Pair("Crime", "80"),
        Pair("Documentary", "99"),
        Pair("Drama", "18"),
        Pair("Family", "10751"),
        Pair("Fantasy", "14"),
        Pair("History", "36"),
        Pair("Horror", "27"),
        Pair("Music", "10402"),
        Pair("Mystery", "9648"),
        Pair("Romance", "10749"),
        Pair("Science Fiction", "878"),
        Pair("TV Movie", "10770"),
        Pair("Thriller", "53"),
        Pair("War", "10752"),
        Pair("Western", "37")
    ))

    private class SortFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Popularity Descending", "popularity.desc"),
        Pair("Popularity Ascending", "popularity.asc"),
        Pair("Release Date Descending", "primary_release_date.desc"),
        Pair("Release Date Ascending", "primary_release_date.asc"),
        Pair("Rating Descending", "vote_average.desc"),
        Pair("Rating Ascending", "vote_average.asc")
    ))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY
            title = "Preferred Video Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080p", "720p", "480p", "360p")
            setDefaultValue("1080p")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                summary = entryValues[index] as String
                preferences.edit().putString(PREF_QUALITY, selected).apply()
                true
            }
        }
        screen.addPreference(qualityPref)
    }

    companion object {
        private const val PREF_QUALITY = "preferred_quality"
    }
}

@Serializable
data class TmdbPageDto(
    val page: Int,
    val results: List<TmdbItemDto>,
    val total_pages: Int
)

@Serializable
data class TmdbItemDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val poster_path: String? = null
)
