package eu.kanade.tachiyomi.animeextension.en.hentaiht

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.hentaiht.HentaiHtFilters.TagFilter
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiHt :
    AnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Hentai.ht"

    override val baseUrl = "https://hentai.ht"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var ageCookie: String? = null

    private fun refreshAgeCookie() {
        synchronized(this) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/age-confirmation")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .header("Origin", baseUrl)
                    .header("Referer", "$baseUrl/")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()
                val res = network.cloudflareClient.newCall(req).execute()
                val cookieHeaders = res.headers.values("Set-Cookie")
                val httpUrl = baseUrl.toHttpUrl()
                val cookies = Cookie.parseAll(httpUrl, res.headers)
                client.cookieJar.saveFromResponse(httpUrl, cookies)
                for (c in cookieHeaders) {
                    if (c.contains("__Secure-hht_age")) {
                        ageCookie = c.substringBefore(";")
                        break
                    }
                }
                res.close()
            } catch (_: Exception) {}
        }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val orig = chain.request()
            if (orig.url.host.contains("hentai.ht")) {
                if (ageCookie == null && !orig.url.encodedPath.contains("age-confirmation")) {
                    refreshAgeCookie()
                }
                val builder = orig.newBuilder()
                    .header("Referer", "$baseUrl/")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                ageCookie?.let { builder.header("Cookie", it) }
                val response = chain.proceed(builder.build())
                if (response.code == 403 && !orig.url.encodedPath.contains("age-confirmation")) {
                    response.close()
                    refreshAgeCookie()
                    val retryBuilder = orig.newBuilder()
                        .header("Referer", "$baseUrl/")
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                    ageCookie?.let { retryBuilder.header("Cookie", it) }
                    return@addInterceptor chain.proceed(retryBuilder.build())
                }
                return@addInterceptor response
            }
            chain.proceed(orig)
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)
        .add("Accept", "application/json")

    // ======================= Popular Anime =======================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/api/v1/catalog/home", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val data = json.decodeFromString<CatalogHomeDto>(body)
        val animeList = data.popular.map { it.toSAnime() }
        return AnimesPage(animeList, false)
    }

    // ======================= Latest Anime =======================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/v1/catalog/releases?page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val body = response.body.string()
        val data = json.decodeFromString<ReleasesPageDto>(body)
        val animeList = data.releases.mapNotNull { it.title?.toSAnime() }
        val hasNextPage = data.page < data.totalPages
        return AnimesPage(animeList, hasNextPage)
    }

    // ======================= Search Anime =======================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            return GET("$baseUrl/api/v1/catalog/search?q=$encodedQuery", headers)
        }

        var tagParam = ""
        for (filter in filters) {
            if (filter is TagFilter) {
                if (filter.selected.isNotBlank()) {
                    tagParam = "&tag=" + URLEncoder.encode(filter.selected, "UTF-8")
                }
            }
        }

        return GET("$baseUrl/api/v1/catalog?page=$page$tagParam", headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val path = response.request.url.encodedPath

        return if (path.contains("search")) {
            val data = json.decodeFromString<SearchResponseDto>(body)
            val animeList = data.titles.map { it.toSAnime() }
            AnimesPage(animeList, false)
        } else {
            val data = json.decodeFromString<CatalogPageDto>(body)
            val animeList = data.titles.map { it.toSAnime() }
            val hasNextPage = data.page < data.totalPages
            AnimesPage(animeList, hasNextPage)
        }
    }

    // ======================= Anime Details =======================

    override fun animeDetailsRequest(anime: SAnime): Request =
        GET("$baseUrl${anime.url}", headers)

    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body.string()
        val wrapper = json.decodeFromString<TitleDetailsWrapperDto>(body)
        val title = wrapper.title

        return SAnime.create().apply {
            this.title = title.name
            thumbnail_url = title.coverFull ?: title.cover
            description = buildString {
                if (!wrapper.synopsis.isNullOrBlank()) {
                    append(wrapper.synopsis)
                    append("\n\n")
                }
                title.english?.let { append("English: $it\n") }
                title.year?.let { append("Year: $it\n") }
                val studioName = title.studio ?: title.studios.firstOrNull()
                studioName?.let { append("Studio: $it\n") }
            }
            genre = title.tags.joinToString(", ")
            status = when (title.status?.lowercase()) {
                "finished" -> SAnime.COMPLETED
                "airing" -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            initialized = true
        }
    }

    // ======================= Episode List =======================

    override fun episodeListRequest(anime: SAnime): Request =
        animeDetailsRequest(anime)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.body.string()
        val wrapper = json.decodeFromString<TitleDetailsWrapperDto>(body)
        val title = wrapper.title

        return title.episodes.map { ep ->
            SEpisode.create().apply {
                episode_number = ep.number.toFloat()
                name = buildString {
                    append("Episode ${ep.number}")
                    val epTitle = ep.titleEnglish ?: ep.label
                    if (!epTitle.isNullOrBlank() && epTitle != "Episode ${ep.number}") {
                        append(": $epTitle")
                    }
                }
                url = "/api/v1/playback/${title.slug}/${ep.number}"
                date_upload = parseDate(ep.releasedAt ?: ep.airdate)
            }
        }.sortedByDescending { it.episode_number }
    }

    // ======================= Video List =======================

    override fun videoListRequest(episode: SEpisode): Request {
        val prefLang = preferences.getString(PREF_LANG_KEY, "ENG SUB") ?: "ENG SUB"
        val encodedLang = URLEncoder.encode(prefLang, "UTF-8")
        return GET("$baseUrl${episode.url}?language=$encodedLang&censorship=censored", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()
        val playback = json.decodeFromString<PlaybackDto>(body)
        val asset = playback.asset ?: return emptyList()
        val manifestPath = asset.manifestUrl ?: return emptyList()

        val manifestUrl = if (manifestPath.startsWith("http")) manifestPath else "$baseUrl$manifestPath"

        val videoHeaders = headersBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()

        val subTracks = playback.subtitles.mapNotNull { sub ->
            val subUrl = sub.url ?: return@mapNotNull null
            val fullSubUrl = if (subUrl.startsWith("http")) subUrl else "$baseUrl$subUrl"
            Track(fullSubUrl, sub.label ?: sub.language ?: "Sub")
        }

        val quality = playback.quality ?: "1080p"
        val langLabel = playback.language ?: "Sub"
        val video = Video(
            manifestUrl,
            "Hentai.ht - $quality ($langLabel)",
            manifestUrl,
            headers = videoHeaders,
            subtitleTracks = subTracks
        )

        val videos = mutableListOf(video)

        // Try uncensored variant if requested and not already uncensored
        if (playback.censorship == "censored") {
            try {
                val uncensoredUrl = response.request.url.newBuilder()
                    .setQueryParameter("censorship", "uncensored")
                    .build()
                val uncensoredReq = GET(uncensoredUrl.toString(), headers)
                val uncensoredRes = client.newCall(uncensoredReq).execute()
                if (uncensoredRes.isSuccessful) {
                    val uncensoredBody = uncensoredRes.body.string()
                    val uncensoredPlayback = json.decodeFromString<PlaybackDto>(uncensoredBody)
                    val unAsset = uncensoredPlayback.asset
                    if (unAsset?.manifestUrl != null) {
                        val unManifestUrl = if (unAsset.manifestUrl.startsWith("http")) {
                            unAsset.manifestUrl
                        } else {
                            "$baseUrl${unAsset.manifestUrl}"
                        }
                        val unSubTracks = uncensoredPlayback.subtitles.mapNotNull { sub ->
                            val sUrl = sub.url ?: return@mapNotNull null
                            val full = if (sUrl.startsWith("http")) sUrl else "$baseUrl$sUrl"
                            Track(full, sub.label ?: sub.language ?: "Sub")
                        }
                        videos.add(
                            Video(
                                unManifestUrl,
                                "Hentai.ht - $quality ($langLabel - Uncensored)",
                                unManifestUrl,
                                headers = videoHeaders,
                                subtitleTracks = unSubTracks
                            )
                        )
                    }
                }
                uncensoredRes.close()
            } catch (_: Exception) {}
        }

        return videos
    }

    override fun videoUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    // ======================= Filters =======================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        TagFilter("Tag", HentaiHtFilters.TAGS)
    )

    // ======================= Preferences =======================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val langPref = ListPreference(screen.context).apply {
            key = PREF_LANG_KEY
            title = "Preferred Subtitle Language"
            entries = arrayOf("English (ENG SUB)", "French (FR SUB)")
            entryValues = arrayOf("ENG SUB", "FR SUB")
            setDefaultValue("ENG SUB")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_LANG_KEY, newValue as String).apply()
                true
            }
        }
        screen.addPreference(langPref)
    }

    // ======================= Utilities & DTOs =======================

    private fun TitleItemDto.toSAnime(): SAnime = SAnime.create().apply {
        val rId = routeId ?: id
        setUrlWithoutDomain("/api/v1/catalog/titles/$rId")
        this.title = this@toSAnime.name
        thumbnail_url = coverFull ?: cover
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val format = if (dateStr.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            }
            format.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private const val PREF_LANG_KEY = "preferred_sub_language"
    }

    // ======================= Data Transfer Objects =======================

    @Serializable
    private data class CatalogHomeDto(
        val popular: List<TitleItemDto> = emptyList(),
        val releases: List<ReleaseItemDto> = emptyList()
    )

    @Serializable
    private data class CatalogPageDto(
        val page: Int = 1,
        val totalPages: Int = 1,
        val titles: List<TitleItemDto> = emptyList()
    )

    @Serializable
    private data class ReleasesPageDto(
        val page: Int = 1,
        val totalPages: Int = 1,
        val releases: List<ReleaseItemDto> = emptyList()
    )

    @Serializable
    private data class ReleaseItemDto(
        val entry: ReleaseEntryDto? = null,
        val title: TitleItemDto? = null
    )

    @Serializable
    private data class ReleaseEntryDto(
        val slug: String? = null,
        val episode: Int? = null,
        val at: String? = null
    )

    @Serializable
    private data class SearchResponseDto(
        val titles: List<TitleItemDto> = emptyList()
    )

    @Serializable
    private data class TitleItemDto(
        val id: Long? = null,
        val routeId: Long? = null,
        val slug: String? = null,
        val name: String,
        val english: String? = null,
        val cover: String? = null,
        val coverFull: String? = null
    )

    @Serializable
    private data class TitleDetailsWrapperDto(
        val title: TitleDetailsDto,
        val synopsis: String? = null
    )

    @Serializable
    private data class TitleDetailsDto(
        val id: Long? = null,
        val routeId: Long? = null,
        val slug: String? = null,
        val name: String,
        val english: String? = null,
        val cover: String? = null,
        val coverFull: String? = null,
        val banner: String? = null,
        val tags: List<String> = emptyList(),
        val year: Int? = null,
        val status: String? = null,
        val studio: String? = null,
        val studios: List<String> = emptyList(),
        val hasUncensored: Boolean = false,
        val episodes: List<EpisodeItemDto> = emptyList()
    )

    @Serializable
    private data class EpisodeItemDto(
        val number: Int,
        val label: String? = null,
        val titleEnglish: String? = null,
        val airdate: String? = null,
        val releasedAt: String? = null
    )

    @Serializable
    private data class PlaybackDto(
        val slug: String? = null,
        val episode: Int? = null,
        val quality: String? = null,
        val language: String? = null,
        val censorship: String? = null,
        val asset: PlaybackAssetDto? = null,
        val subtitles: List<PlaybackSubtitleDto> = emptyList()
    )

    @Serializable
    private data class PlaybackAssetDto(
        val id: String? = null,
        val manifestUrl: String? = null,
        val mimeType: String? = null
    )

    @Serializable
    private data class PlaybackSubtitleDto(
        val id: String? = null,
        val language: String? = null,
        val label: String? = null,
        val url: String? = null,
        val default: Boolean = false
    )
}
