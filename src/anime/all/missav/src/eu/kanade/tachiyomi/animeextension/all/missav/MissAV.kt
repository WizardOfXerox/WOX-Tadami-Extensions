package eu.kanade.tachiyomi.animeextension.all.missav

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.javcoverfetcher.JavCoverFetcher
import eu.kanade.tachiyomi.lib.javcoverfetcher.JavCoverFetcher.fetchHDCovers
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.lib.m3u8server.M3u8Integration
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class MissAV :
    AnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "MissAV"

    override val lang = "all"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_\$id", 0)
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val m3u8Integration by lazy {
        M3u8Integration(client)
    }

    override var baseUrl: String = PREF_DOMAIN_DEFAULT

    override val supportsLatest = true

    private var docHeaders: Headers = newHeaders()

    private fun newHeaders(): Headers = headers.newBuilder().apply {
        set("Origin", baseUrl)
        set("Referer", "$baseUrl/")
    }.build()

    private var playlistExtractor: PlaylistUtils = PlaylistUtils(client, docHeaders)

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/en/today-hot?page=$page", docHeaders)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val entries = document.select("div.thumbnail").map { element ->
            SAnime.create().apply {
                element.select("a.text-secondary").also {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
                thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
            }
        }

        val hasNextPage = document.selectFirst("a[rel=next]") != null

        return AnimesPage(entries, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/new?page=$page", docHeaders)

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val subRoute = filters.firstInstanceOrNull<SubtitleFilter>()?.selected
            val genre = filters.firstInstanceOrNull<GenreList>()?.selected
            if (query.isNotEmpty()) {
                addEncodedPathSegments("en/search")
                addPathSegment(query.trim())
            } else if (subRoute != null) {
                addEncodedPathSegments(subRoute)
            } else if (genre != null) {
                addEncodedPathSegments(genre)
            } else {
                addEncodedPathSegments("en/new")
            }
            filters.firstInstanceOrNull<SortFilter>()?.selected?.let {
                addQueryParameter("sort", it)
            }
            addQueryParameter("page", page.toString())
        }.build().toString()

        return GET(url, docHeaders)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        if (document.selectFirst("div[x-data*=handleRecommendResponse]") != null) {
            val url = response.request.url
            val pathSegments = url.pathSegments
            val queryStr = pathSegments.getOrNull(pathSegments.indexOf("search") + 1)
                ?: throw Exception("Failed to parse search query from URL: $url")
            val query = URLDecoder.decode(queryStr, StandardCharsets.UTF_8.name())
            val page = url.queryParameter("page")?.toIntOrNull() ?: 1
            client.newCall(fallbackApiSearch(query, page))
                .execute().use {
                    if (!it.isSuccessful) {
                        Log.e("MissAv", "Failed to fetch search results: ${it.code}")
                        throw Exception("No more results found")
                    }

                    val data = json.decodeFromString<RecommendationsResponse>(it.body.string())
                    recommMap[query] = data.recommId
                    return data.toAnimePage()
                }
        }

        val entries = document.select("div.thumbnail").map { element ->
            SAnime.create().apply {
                element.select("a.text-secondary").also {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
                thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
            }
        }

        val hasNextPage = document.selectFirst("a[rel=next]") != null

        return AnimesPage(entries, hasNextPage)
    }

    private val recommMap: MutableMap<String, String> = ConcurrentHashMap()

    private fun fallbackApiSearch(query: String, page: Int): Request {
        val recommId = recommMap[query]
        return if (page == 1 || recommId == null) {
            val body = json.encodeToString(MissAvApi.searchData(query)).toRequestBody("application/json".toMediaType())
            POST(MissAvApi.searchURL(getUuid()), docHeaders, body)
        } else {
            val body = json.encodeToString(MissAvApi.recommData).toRequestBody("application/json".toMediaType())
            POST(MissAvApi.recommURL(recommId), docHeaders, body)
        }
    }

    override fun getFilterList() = getFilters()

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()

        val jpTitle = document.select("div.text-secondary span:contains(title) + span").text()
        val siteCover = document.selectFirst("video.player")?.attr("abs:data-poster")

        return SAnime.create().apply {
            title = document.selectFirst("h1.text-base")!!.text()
            genre = document.getInfo("/genres/")
            author = listOfNotNull(
                document.getInfo("/directors/"),
                document.getInfo("/makers/"),
            ).joinToString()
            artist = document.getInfo("/actresses/")
            status = SAnime.COMPLETED
            description = buildString {
                document.selectFirst("div.mb-1")?.text()?.also { append("$it\n") }

                document.getInfo("/labels/")?.also { append("\nLabel: $it") }
                document.getInfo("/series/")?.also { append("\nSeries: $it") }

                document.select("div.text-secondary:not(:has(a)):has(span)")
                    .eachText()
                    .forEach { append("\n$it") }
            }
            thumbnail_url = if (preferences.fetchHDCovers) {
                JavCoverFetcher.getCoverByTitle(jpTitle) ?: siteCover
            } else {
                siteCover
            }
        }
    }

    private fun Element.getInfo(urlPart: String) = select("div.text-secondary > a[href*=$urlPart]")
        .eachText()
        .joinToString()
        .takeIf(String::isNotBlank)

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = listOf(
        SEpisode.create().apply {
            url = anime.url
            name = "Episode"
        },
    )

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()

        val scripts = document.select("script:containsData(function(p,a,c,k,e,d))")
        var masterPlaylist = ""

        for (script in scripts) {
            val unpacked = Unpacker.unpack(script.data())
            if (unpacked.contains("source=")) {
                masterPlaylist = unpacked.substringAfter("source='", "").substringBefore("';")
                if (masterPlaylist.isBlank()) {
                    masterPlaylist = unpacked.substringAfter("source=\"", "").substringBefore("\";")
                }
                if (masterPlaylist.isNotBlank()) break
            }
        }

        if (masterPlaylist.isBlank()) return emptyList()

        val videos = playlistExtractor.extractFromHls(masterPlaylist, referer = "$baseUrl/")
        return m3u8Integration.processVideoList(videos)
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(compareByDescending { it.quality.contains(quality) })
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = PREF_DOMAIN_TITLE
            entries = PREF_DOMAIN_ENTRIES.toTypedArray()
            entryValues = PREF_DOMAIN_ENTRIES.toTypedArray()
            setDefaultValue(PREF_DOMAIN_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val domain = newValue as String
                baseUrl = domain
                docHeaders = newHeaders()
                playlistExtractor = PlaylistUtils(client, docHeaders)
                true
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES.toTypedArray()
            entryValues = PREF_QUALITY_VALUES.toTypedArray()
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_QUALITY, newValue as String).commit()
            }
        }.also(screen::addPreference)

        JavCoverFetcher.addPreferenceToScreen(screen)
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? = filterIsInstance<T>().firstOrNull()

    private fun getUuid(): String = preferences.getString(PREF_UUID_KEY, null) ?: synchronized(this) {
        // Double-check pattern to avoid generating UUID if another thread already did
        preferences.getString(PREF_UUID_KEY, null) ?: run {
            val uuid = MissAvApi.generateUUID()
            preferences.edit().putString(PREF_UUID_KEY, uuid).apply()
            uuid
        }
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "preferred_domain"
        private const val PREF_DOMAIN_TITLE = "Preferred domain (requires app restart)"
        private val PREF_DOMAIN_ENTRIES = listOf("https://missav.live", "https://missav.com", "https://missav.ws", "https://missav.ai")
        private val PREF_DOMAIN_DEFAULT = "https://missav.live"

        private const val PREF_QUALITY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private val PREF_QUALITY_ENTRIES = listOf("720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = listOf("720", "480", "360")
        private val PREF_QUALITY_DEFAULT = PREF_QUALITY_VALUES.first()

        private const val PREF_UUID_KEY = "missav_uuid"

        private val regexWhitespace = Regex("\\s+")
        private val regexSpecialCharacters =
            Regex("([-.!~#$%^&*+_|/\\\\,?:;'“”‘’\"<>(){}\\[\\]。・～：—！？、―«»《》〘〙【】「」｜]|\\s-|-\\s|\\s\\.|\\.\\s)")
        private val regexNumberOnly = Regex("^\\d+$")
    }
}
