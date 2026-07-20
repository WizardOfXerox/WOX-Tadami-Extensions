package eu.kanade.tachiyomi.animeextension.en.xprime

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class XPrime : ConfigurableAnimeSource, AnimeHttpSource() {

    override val name = "XPrime"

    override val baseUrl = "https://xprime.tv"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val tmdbApiUrl = "https://api.themoviedb.org/3"
    private val tmdbApiKey = "b823643b0cffaaf0b356d169f5a4e76f"
    private val tmdbImageBase = "https://image.tmdb.org/t/p/w500"
    private val tmdbBackdropBase = "https://image.tmdb.org/t/p/w1280"

    // ============================== DTO Models ==============================

    @Serializable
    private data class PageDto(
        val page: Int? = 1,
        val results: List<MediaItemDto>? = emptyList(),
        @SerialName("total_pages") val totalPages: Int? = 1
    )

    @Serializable
    private data class MediaItemDto(
        val id: Long,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("media_type") val mediaType: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Float? = null
    )

    @Serializable
    private data class MovieDetailDto(
        val id: Long,
        val title: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        val genres: List<GenreDto>? = emptyList(),
        @SerialName("production_companies") val productionCompanies: List<CompanyDto>? = emptyList(),
        @SerialName("external_ids") val externalIds: ExternalIdsDto? = null
    )

    @Serializable
    private data class TvDetailDto(
        val id: Long,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        val genres: List<GenreDto>? = emptyList(),
        val seasons: List<SeasonDto>? = emptyList(),
        @SerialName("external_ids") val externalIds: ExternalIdsDto? = null
    )

    @Serializable
    private data class SeasonDto(
        val id: Long,
        @SerialName("season_number") val seasonNumber: Int,
        val name: String? = null,
        @SerialName("episode_count") val episodeCount: Int? = 0,
        @SerialName("air_date") val airDate: String? = null
    )

    @Serializable
    private data class TvSeasonDetailDto(
        @SerialName("season_number") val seasonNumber: Int,
        val episodes: List<EpisodeDto>? = emptyList()
    )

    @Serializable
    private data class EpisodeDto(
        val id: Long,
        val name: String? = null,
        @SerialName("episode_number") val episodeNumber: Int,
        @SerialName("season_number") val seasonNumber: Int,
        @SerialName("air_date") val airDate: String? = null,
        val overview: String? = null
    )

    @Serializable
    private data class GenreDto(
        val id: Int,
        val name: String
    )

    @Serializable
    private data class CompanyDto(
        val id: Int,
        val name: String
    )

    @Serializable
    private data class ExternalIdsDto(
        @SerialName("imdb_id") val imdbId: String? = null
    )

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val type = preferences.getString(PREF_LATEST_KEY, "movie") ?: "movie"
        val endpoint = if (type == "tv") "tv/popular" else "movie/popular"
        return GET("$tmdbApiUrl/$endpoint?api_key=$tmdbApiKey&page=$page", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val jsonStr = response.body.string()
        val pageDto = json.decodeFromString<PageDto>(jsonStr)

        val animeList = pageDto.results?.map { item ->
            val isMovie = item.title != null || item.mediaType == "movie"
            val displayTitle = item.title ?: item.name ?: item.originalTitle ?: item.originalName ?: "Media"
            val itemType = if (isMovie) "movie" else "tv"

            SAnime.create().apply {
                url = "/$itemType/${item.id}"
                title = displayTitle
                thumbnail_url = if (item.posterPath != null) "$tmdbImageBase${item.posterPath}" else ""
            }
        } ?: emptyList()

        val hasNextPage = (pageDto.page ?: 1) < (pageDto.totalPages ?: 1)
        return AnimesPage(animeList, hasNextPage)
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$tmdbApiUrl/trending/all/day?api_key=$tmdbApiKey&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            return GET("$tmdbApiUrl/search/multi?api_key=$tmdbApiKey&query=$encodedQuery&page=$page", headers)
        }

        val url = "$tmdbApiUrl/discover/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("api_key", tmdbApiKey)
            .addQueryParameter("page", page.toString())

        var mediaType = "movie"
        for (filter in filters) {
            when (filter) {
                is TypeFilter -> {
                    mediaType = filter.toUriPart()
                }
                is GenreFilter -> {
                    val genreId = filter.toUriPart()
                    if (genreId.isNotEmpty()) {
                        url.addQueryParameter("with_genres", genreId)
                    }
                }
                is SortFilter -> {
                    val sort = filter.toUriPart()
                    if (sort.isNotEmpty()) {
                        url.addQueryParameter("sort_by", sort)
                    }
                }
                is NetworkFilter -> {
                    val networkId = filter.toUriPart()
                    if (networkId.isNotEmpty()) {
                        if (mediaType == "tv") {
                            url.addQueryParameter("with_networks", networkId)
                        } else {
                            url.addQueryParameter("with_companies", networkId)
                        }
                    }
                }
                else -> {}
            }
        }

        val finalUrl = "$tmdbApiUrl/discover/$mediaType?" + url.build().query
        return GET(finalUrl, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== Details ==============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val path = anime.url
        val isMovie = path.startsWith("/movie/")
        val id = path.substringAfterLast("/")
        val endpoint = if (isMovie) "movie/$id" else "tv/$id"
        return GET("$tmdbApiUrl/$endpoint?api_key=$tmdbApiKey&append_to_response=external_ids", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val jsonStr = response.body.string()
        val isMovie = response.request.url.toString().contains("/movie/")

        return SAnime.create().apply {
            status = SAnime.COMPLETED
            initialized = true

            if (isMovie) {
                val detail = json.decodeFromString<MovieDetailDto>(jsonStr)
                title = detail.title ?: "Movie"
                description = detail.overview ?: ""
                genre = detail.genres?.joinToString { it.name } ?: ""
                author = detail.productionCompanies?.firstOrNull()?.name ?: "TMDB"
                thumbnail_url = if (detail.posterPath != null) "$tmdbImageBase${detail.posterPath}" else ""
            } else {
                val detail = json.decodeFromString<TvDetailDto>(jsonStr)
                title = detail.name ?: "TV Series"
                description = detail.overview ?: ""
                genre = detail.genres?.joinToString { it.name } ?: ""
                author = "TMDB"
                thumbnail_url = if (detail.posterPath != null) "$tmdbImageBase${detail.posterPath}" else ""
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        return animeDetailsRequest(anime)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val jsonStr = response.body.string()
        val isMovie = response.request.url.toString().contains("/movie/")

        if (isMovie) {
            val detail = json.decodeFromString<MovieDetailDto>(jsonStr)
            val releaseMs = try {
                if (!detail.releaseDate.isNullOrBlank()) {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(detail.releaseDate)?.time ?: 0L
                } else 0L
            } catch (_: Exception) { 0L }

            return listOf(
                SEpisode.create().apply {
                    url = "/movie/${detail.id}"
                    name = detail.title ?: "Full Movie"
                    episode_number = 1F
                    date_upload = releaseMs
                }
            )
        }

        val detail = json.decodeFromString<TvDetailDto>(jsonStr)
        val episodes = mutableListOf<SEpisode>()

        detail.seasons?.filter { it.seasonNumber > 0 }?.forEach { season ->
            try {
                val seasonReq = GET("$tmdbApiUrl/tv/${detail.id}/season/${season.seasonNumber}?api_key=$tmdbApiKey", headers)
                val seasonResp = client.newCall(seasonReq).execute()
                if (seasonResp.isSuccessful) {
                    val seasonJson = seasonResp.body.string()
                    val seasonDetail = json.decodeFromString<TvSeasonDetailDto>(seasonJson)

                    seasonDetail.episodes?.forEach { ep ->
                        val airMs = try {
                            if (!ep.airDate.isNullOrBlank()) {
                                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ep.airDate)?.time ?: 0L
                            } else 0L
                        } catch (_: Exception) { 0L }

                        val epName = if (!ep.name.isNullOrBlank()) "S${ep.seasonNumber} E${ep.episodeNumber} - ${ep.name}" else "Season ${ep.seasonNumber} Episode ${ep.episodeNumber}"

                        episodes.add(
                            SEpisode.create().apply {
                                url = "/tv/${detail.id}/season/${ep.seasonNumber}/episode/${ep.episodeNumber}"
                                name = epName
                                episode_number = ep.episodeNumber.toFloat()
                                date_upload = airMs
                            }
                        )
                    }
                }
                seasonResp.close()
            } catch (_: Exception) {}
        }

        return episodes.ifEmpty {
            listOf(
                SEpisode.create().apply {
                    url = "/tv/${detail.id}/season/1/episode/1"
                    name = "Episode 1"
                    episode_number = 1F
                }
            )
        }
    }

    // ============================== Video List (Multi-Server Resolver) ==============================

    private data class VideoTag(
        val tmdbId: String,
        val season: String,
        val episode: String,
        val isMovie: Boolean
    )

    override fun videoListRequest(episode: SEpisode): Request {
        val urlPath = episode.url
        val isMovie = urlPath.startsWith("/movie/")

        val videoHeaders = headersBuilder().apply {
            set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            set("Referer", "https://vidsrc.me/")
        }.build()

        val tmdbId: String
        val season: String
        val epNum: String

        if (isMovie) {
            tmdbId = urlPath.substringAfter("/movie/")
            season = "1"
            epNum = "1"
        } else {
            val parts = urlPath.split("/")
            tmdbId = parts.getOrNull(2) ?: ""
            season = parts.getOrNull(4) ?: "1"
            epNum = parts.getOrNull(6) ?: "1"
        }

        val url = if (isMovie) {
            "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
        } else {
            "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$epNum"
        }

        val tag = VideoTag(tmdbId, season, epNum, isMovie)
        return GET(url, videoHeaders).newBuilder().tag(VideoTag::class.java, tag).build()
    }

    override fun videoListParse(response: Response): List<Video> {
        val tag = response.request.tag(VideoTag::class.java)
        val tmdbId = tag?.tmdbId ?: ""
        val season = tag?.season ?: "1"
        val episode = tag?.episode ?: "1"
        val isMovie = tag?.isMovie ?: response.request.url.toString().contains("/movie")

        val videoHeaders = headersBuilder().apply {
            set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            set("Referer", "https://vidsrc.me/")
        }.build()

        val videoList = mutableListOf<Video>()

        // 1. VidSrc.me Main
        val vidsrcMeUrl = if (isMovie) {
            "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
        } else {
            "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
        }
        videoList.add(Video(vidsrcMeUrl, "VidSrc.me (Auto)", vidsrcMeUrl, headers = videoHeaders))

        // 2. VidSrc.in Mirror
        val vidsrcInUrl = if (isMovie) {
            "https://vidsrc.in/embed/movie?tmdb=$tmdbId"
        } else {
            "https://vidsrc.in/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
        }
        videoList.add(Video(vidsrcInUrl, "VidSrc.in (Mirror)", vidsrcInUrl, headers = videoHeaders))

        // 3. VidSrc.pm Mirror
        val vidsrcPmUrl = if (isMovie) {
            "https://vidsrc.pm/embed/movie?tmdb=$tmdbId"
        } else {
            "https://vidsrc.pm/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
        }
        videoList.add(Video(vidsrcPmUrl, "VidSrc.pm (Mirror)", vidsrcPmUrl, headers = videoHeaders))

        // 4. 2Embed Mirror
        val embed2Url = if (isMovie) {
            "https://2embed.cc/embed/$tmdbId"
        } else {
            "https://2embed.cc/embedtv/$tmdbId&s=$season&e=$episode"
        }
        videoList.add(Video(embed2Url, "2Embed Server", embed2Url, headers = videoHeaders))

        // 5. VidSrc.to Mirror
        val vidsrcToUrl = if (isMovie) {
            "https://vidsrc.to/embed/movie/$tmdbId"
        } else {
            "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"
        }
        videoList.add(Video(vidsrcToUrl, "VidSrc.to Server", vidsrcToUrl, headers = videoHeaders))

        val preferredQuality = preferences.getString(PREF_QUALITY_KEY, "1080p") ?: "1080p"
        return videoList.sortedWith(compareBy { !it.quality.contains(preferredQuality) })
    }

    // ============================== Preferences & Filters ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_LATEST_KEY
            title = "Preferred Catalog View"
            entries = arrayOf("Movies", "TV Shows")
            entryValues = arrayOf("movie", "tv")
            setDefaultValue("movie")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                preferences.edit().putString(key, selected).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Video Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = entries
            setDefaultValue("1080p")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                preferences.edit().putString(key, selected).commit()
            }
        }.also(screen::addPreference)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Filters apply when search query is empty"),
        TypeFilter(),
        SortFilter(),
        GenreFilter(),
        NetworkFilter()
    )

    private abstract class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class TypeFilter : UriPartFilter(
        "Content Type",
        arrayOf(
            Pair("Movie", "movie"),
            Pair("TV Show", "tv")
        )
    )

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Popularity Descending", "popularity.desc"),
            Pair("Popularity Ascending", "popularity.asc"),
            Pair("Rating Descending", "vote_average.desc"),
            Pair("Rating Ascending", "vote_average.asc"),
            Pair("Release Date Descending", "primary_release_date.desc"),
            Pair("Release Date Ascending", "primary_release_date.asc")
        )
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
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
        )
    )

    private class NetworkFilter : UriPartFilter(
        "Streaming Platform / Network",
        arrayOf(
            Pair("All", ""),
            Pair("Netflix", "213"),
            Pair("Amazon Prime Video", "1024"),
            Pair("Disney+", "2739"),
            Pair("Apple TV+", "2552"),
            Pair("HBO Max / Max", "3186"),
            Pair("Hulu", "453"),
            Pair("Paramount+", "4330"),
            Pair("Peacock", "3353"),
            Pair("Pluto TV", "247"),
            Pair("Tubi TV", "2422")
        )
    )

    companion object {
        private const val PREF_LATEST_KEY = "pref_xprime_catalog"
        private const val PREF_QUALITY_KEY = "pref_xprime_quality"
    }
}
