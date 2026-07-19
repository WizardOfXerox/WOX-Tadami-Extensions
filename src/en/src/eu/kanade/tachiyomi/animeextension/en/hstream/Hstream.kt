package eu.kanade.tachiyomi.animeextension.en.hstream

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class Hstream : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Hstream"

    override val baseUrl = "https://hstream.moe"

    override val lang = "en"

    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val trimmed = dateStr.trim(' ', '|')
            dateFormat.parse(trimmed)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/search?order=view-count&page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.group > a"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val urlPath = element.attr("href")
            setUrlWithoutDomain(urlPath)
            
            val img = element.selectFirst("img")
            title = img?.attr("alt") ?: ""
            
            val epNumStr = url.substringAfterLast("-").substringBefore("/")
            val basePath = url.substringBeforeLast("-")
            thumbnail_url = "$baseUrl/images$basePath/cover-ep-$epNumStr.webp"
        }
    }

    override fun popularAnimeNextPageSelector(): String = "span[aria-current] + a"

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/search?order=recently-uploaded&page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.startsWith("https://") || query.startsWith("http://")) {
            val u = query.toHttpUrl()
            if (u.host == baseUrl.toHttpUrl().host) {
                val pathSegment = u.pathSegments.getOrNull(1)
                if (pathSegment != null) {
                    return getSearchAnime(page, "id:$pathSegment", filters)
                }
            }
            throw Exception("Unsupported url")
        }

        if (query.startsWith("id:")) {
            val pathSegment = query.removePrefix("id:")
            val response = client.newCall(GET("$baseUrl/hentai/$pathSegment", headers)).execute()
            if (!response.isSuccessful) {
                response.close()
                throw Exception("Failed to fetch search result: ${response.code}")
            }
            val document = response.asJsoup()
            val anime = animeDetailsParse(document)
            anime.setUrlWithoutDomain("/hentai/$pathSegment")
            anime.initialized = true
            return AnimesPage(listOf(anime), false)
        }

        return super.getSearchAnime(page, query, filters)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("page", page.toString())

            val orderFilter = filters.filterIsInstance<HstreamFilters.OrderFilter>().firstOrNull()
            addQueryParameter("order", orderFilter?.toUri() ?: "view-count")

            val genreFilter = filters.filterIsInstance<HstreamFilters.GenreFilter>().firstOrNull()
            var includeCount = 0
            genreFilter?.state?.forEach { genre ->
                if (genre.isIncluded()) {
                    addQueryParameter("tags[$includeCount]", genre.value)
                    includeCount++
                } else if (genre.isExcluded()) {
                    addQueryParameter("blacklist[]", genre.value)
                }
            }

            val studioFilter = filters.filterIsInstance<HstreamFilters.StudioFilter>().firstOrNull()
            studioFilter?.state?.forEach { studio ->
                if (studio.state) {
                    addQueryParameter("studios[]", studio.value)
                }
            }
        }.build().toString()

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            status = SAnime.COMPLETED
            
            val infoBlock = document.selectFirst("div.relative > div.justify-between > div")
                ?: throw Exception("Info block not found")
            
            val rawTitle = infoBlock.selectFirst("div > h1")?.text() ?: ""
            title = rawTitle.substringBeforeLast(" - ")
            artist = infoBlock.select("div > a:nth-of-type(3)").text()
            
            val img = document.selectFirst("div.float-left > img.object-cover")
            thumbnail_url = img?.absUrl("src")
            
            val genreElements = document.select("ul.list-none > li > a")
            genre = genreElements.eachText().joinToString(", ")
            
            val desc = document.selectFirst("div.relative > p.leading-tight")
            description = desc?.text()
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val uploadDateText = document.selectFirst("a:has(i.fa-upload)")?.ownText()
        val uploadDate = parseDate(uploadDateText)
        val currentUrl = response.request.url.toString()

        val animeHomeUrl = document.selectFirst("h1 > a[href*=/hentai/]")?.attr("abs:href")
        if (!animeHomeUrl.isNullOrBlank()) {
            try {
                val homeResponse = client.newCall(GET(animeHomeUrl, headers)).execute()
                if (homeResponse.isSuccessful) {
                    val homeDoc = homeResponse.asJsoup()
                    val episodeElements = homeDoc.select("div.group > a[href*=/hentai/]")
                    if (episodeElements.isNotEmpty()) {
                        return episodeElements.mapIndexed { index, element ->
                            SEpisode.create().apply {
                                val epUrl = element.attr("abs:href").ifBlank { element.attr("href") }
                                setUrlWithoutDomain(epUrl)
                                
                                val epNumStr = epUrl.substringAfterLast("-").substringBefore("/")
                                val epNum = epNumStr.toFloatOrNull() ?: (index + 1).toFloat()
                                episode_number = epNum
                                name = "Episode $epNumStr"
                                
                                date_upload = if (epUrl.substringAfterLast("/") == currentUrl.substringAfterLast("/")) {
                                    uploadDate
                                } else {
                                    0L
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to single episode if request fails
            }
        }

        val episode = SEpisode.create().apply {
            date_upload = uploadDate
            setUrlWithoutDomain(document.location())
            val epNumStr = url.substringAfterLast("-").substringBefore("/")
            val epNum = epNumStr.toFloatOrNull() ?: 1.0f
            episode_number = epNum
            name = "Episode $epNumStr"
        }
        return listOf(episode)
    }

    override fun episodeFromElement(element: Element): SEpisode {
        throw UnsupportedOperationException()
    }

    override fun episodeListSelector(): String {
        throw UnsupportedOperationException()
    }

    // ============================== Video Listing ==============================

    @Serializable
    data class PlayerApiResponse(
        val legacy: Int = 0,
        val resolution: String = "4k",
        val stream_url: String? = null,
        val stream_domains: List<String>? = null
    )

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val pageResponse = client.newCall(GET(baseUrl + episode.url, headers)).execute()
        if (!pageResponse.isSuccessful) {
            pageResponse.close()
            throw Exception("Failed to fetch episode page: ${pageResponse.code}")
        }
        val document = pageResponse.asJsoup()
        
        val cookies = client.cookieJar.loadForRequest(pageResponse.request.url)
        val xsrfCookie = cookies.firstOrNull { it.name == "XSRF-TOKEN" }
            ?: throw Exception("XSRF-TOKEN cookie not found")
        
        val eIdElement = document.selectFirst("input#e_id")
            ?: throw Exception("Episode ID element not found")
        val episodeId = eIdElement.attr("value")
        
        val xsrfToken = URLDecoder.decode(xsrfCookie.value, "utf-8")
        
        val apiHeaders = headersBuilder().apply {
            set("Referer", document.location())
            set("Origin", baseUrl)
            set("X-Requested-With", "XMLHttpRequest")
            set("X-XSRF-TOKEN", xsrfToken)
        }.build()
        
        val requestBody = "{\"episode_id\":\"$episodeId\"}"
            .toRequestBody("application/json".toMediaType())
            
        val apiResponse = client.newCall(POST("$baseUrl/player/api", apiHeaders, requestBody)).execute()
        if (!apiResponse.isSuccessful) {
            apiResponse.close()
            throw Exception("Player API request failed: ${apiResponse.code}")
        }
        
        val responseBody = apiResponse.body.string()
        val playerApiResp = json.decodeFromString<PlayerApiResponse>(responseBody)
        
        val streamUrl = playerApiResp.stream_url
            ?: throw Exception("Stream URL missing in API response")
        val streamDomains = playerApiResp.stream_domains
            ?: throw Exception("Stream domains missing in API response")
        if (streamDomains.isEmpty()) {
            throw Exception("No stream domains found in API response")
        }
        
        val randomDomain = streamDomains.random()
        val baseStream = "$randomDomain/$streamUrl"
        val subtitleTracks = listOf(Track("$baseStream/eng.ass", "English"))
        
        val resolutions = listOfNotNull(
            "720",
            "1080",
            if (playerApiResp.resolution == "4k") "2160" else null
        )
        
        val videoList = resolutions.map { res ->
            val suffix = if (playerApiResp.legacy != 0) {
                if (res == "720") "/x264.720p.mp4" else "/av1.${res}.webm"
            } else {
                "/$res/manifest.mpd"
            }
            val videoUrl = "$baseStream$suffix"
            Video(videoUrl, "${res}p", videoUrl, headers = null, subtitleTracks = subtitleTracks)
        }
        
        return videoList.sort()
    }

    override fun videoListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun videoFromElement(element: Element): Video {
        throw UnsupportedOperationException()
    }

    override fun videoUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = arrayOf("720p (HD)", "1080p (FULLHD)", "2160p (4K)")
            entryValues = arrayOf("720p", "1080p", "2160p")
            setDefaultValue("720p")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }

    override fun List<Video>.sort(): List<Video> {
        val preferredQuality = preferences.getString(PREF_QUALITY_KEY, "720p") ?: "720p"
        return this.sortedWith(
            compareBy { !it.quality.contains(preferredQuality) }
        )
    }

    override fun getFilterList(): AnimeFilterList = HstreamFilters.getFilters()

    companion object {
        private const val PREF_QUALITY_KEY = "pref_quality_key"
    }
}
