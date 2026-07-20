package eu.kanade.tachiyomi.animeextension.all.twitch

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Twitch : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Twitch"

    override val baseUrl = "https://twitchtracker.com"

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ==============================

    override fun popularAnimeSelector(): String = "table tbody tr, tbody tr"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/channels/ranking?page=$page", headers)

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val link = element.selectFirst("td a[href*=/channels/]") ?: element.selectFirst("td a")
            val href = link?.attr("href") ?: ""
            if (href.isNotBlank()) {
                setUrlWithoutDomain(href)
            } else {
                setUrlWithoutDomain("/channels/ranking")
            }

            title = element.selectFirst("td a.item-title")?.text()?.takeIf { it.isNotBlank() }
                ?: link?.text()?.takeIf { it.isNotBlank() }
                ?: "Twitch Streamer"

            val img = element.selectFirst("td img")
            thumbnail_url = img?.attr("abs:src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")
                ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "ul.pagination.pagination-simple li a:contains(Next), a[rel=next]"

    // ============================== Latest ==============================

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/channels/live?page=$page", headers)

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeSelector(): String = "table.tops tbody tr, table tbody tr"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val liveNowFilter = try { (filters.find { it is LiveNowFilter } as? LiveNowFilter)?.state ?: false } catch (_: Exception) { false }
        val langFilter = try { (filters.find { it is LanguageFilter } as? LanguageFilter) } catch (_: Exception) { null }

        val langCode = langFilter?.toUriPart() ?: ""

        return when {
            query.isNotBlank() -> GET("$baseUrl/search?q=$query", headers)
            liveNowFilter && langCode.isNotEmpty() -> GET("$baseUrl/channels/live/$langCode?page=$page", headers)
            liveNowFilter -> GET("$baseUrl/channels/live?page=$page", headers)
            langCode.isNotEmpty() -> GET("$baseUrl/channels/ranking/$langCode?page=$page", headers)
            else -> GET("$baseUrl/channels/ranking?page=$page", headers)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val url = response.request.url.toString()
        val document = response.asJsoup()
        return if (url.contains("/channels/")) {
            val nextPageElement = document.selectFirst("ul.pagination.pagination-simple li a:contains(Next)")?.text()
            val nextPage = !nextPageElement.isNullOrBlank()
            val items = document.select(popularAnimeSelector()).mapNotNull { 
                try { popularAnimeFromElement(it) } catch (_: Exception) { null }
            }
            AnimesPage(items, nextPage)
        } else {
            val items = document.select(searchAnimeSelector()).mapNotNull { 
                try { searchAnimeFromElement(it) } catch (_: Exception) { null }
            }
            AnimesPage(items, false)
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("td a.item-title, td a")
        val href = link?.attr("href") ?: ""
        if (href.isNotBlank()) {
            setUrlWithoutDomain(href)
        } else {
            setUrlWithoutDomain("/channels/ranking")
        }

        title = link?.text()?.takeIf { it.isNotBlank() } ?: "Twitch Streamer"

        val img = element.selectFirst("td.image-cell img, td img")
        thumbnail_url = img?.attr("abs:src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("src")
            ?: ""
    }

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("div#app-conception div#app-title, h1")?.text() ?: "Twitch Streamer"
            genre = document.select("ul.list-group li div a.label").joinToString { it.text() }
            status = SAnime.ONGOING
            description = document.selectFirst("div.row div.col-md-4.text-center, div.channel-description")?.text() ?: "Twitch Stream"
            thumbnail_url = document.selectFirst("img.channel-avatar, img[src*=/profile_images/]")?.attr("abs:src") ?: ""
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val isLive = document.select("li.list-group-item span.label.label-danger").text().contains("streaming now", ignoreCase = true)

        val episode = SEpisode.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            name = if (isLive) "Streaming Now (Refresh to update state)" else "Offline (Refresh to update state)"
            episode_number = if (isLive) 1F else 0F
        }
        return listOf(episode)
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================== Video List ==============================

    @Serializable
    private data class ApiResponse(
        val success: Boolean? = null,
        val urls: UrlsDto? = null
    )

    @Serializable
    private data class UrlsDto(
        val audio_only: String? = null,
        @SerialName("160p") val veryLow: String? = null,
        @SerialName("360p") val low: String? = null,
        @SerialName("480p") val sd: String? = null,
        @SerialName("720p") val hd: String? = null,
        @SerialName("1080p") val fhd: String? = null
    )

    override fun videoListParse(response: Response): List<Video> {
        val pageUrl = response.request.url.toString()
        val channelName = pageUrl.substringAfterLast("/").trim()

        if (channelName.isBlank()) {
            throw Exception("Invalid Twitch channel URL")
        }

        val videos = mutableListOf<Video>()
        try {
            val twitchUrl = "https://www.twitch.tv/$channelName"
            val apiUrl = "https://pwn.sh/tools/streamapi.py?url=$twitchUrl"
            val apiResponse = client.newCall(GET(apiUrl, headers)).execute()
            if (apiResponse.isSuccessful) {
                val jsonStr = apiResponse.body.string()
                val apiData = json.decodeFromString<ApiResponse>(jsonStr)
                apiData.urls?.let { u ->
                    u.fhd?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "1080p (FHD)", it)) }
                    u.hd?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "720p (HD)", it)) }
                    u.sd?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "480p (SD)", it)) }
                    u.low?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "360p (Low)", it)) }
                    u.veryLow?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "160p (Very Low)", it)) }
                    u.audio_only?.takeIf { it.isNotBlank() && it != "null" }?.let { videos.add(Video(it, "Audio Only", it)) }
                }
            }
            apiResponse.close()
        } catch (_: Exception) {}

        if (videos.isEmpty()) {
            val offlineVideoUrl = "https://cdn.discordapp.com/attachments/909242748283006996/1014362630456090654/amogus.mp4"
            return listOf(Video(offlineVideoUrl, "Stream Offline / Refresh when live", offlineVideoUrl))
        }

        return videos
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Preferences & Filters ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Search query overrides filters"),
        LiveNowFilter(),
        LanguageFilter()
    )

    private class LiveNowFilter : AnimeFilter.CheckBox("Show Live Now only", false)

    private class LanguageFilter : UriPartFilter(
        "Language",
        arrayOf(
            Pair("Any", ""),
            Pair("English", "english"),
            Pair("Spanish", "spanish"),
            Pair("Portuguese", "portuguese"),
            Pair("French", "french"),
            Pair("German", "german"),
            Pair("Italian", "italian"),
            Pair("Russian", "russian"),
            Pair("Japanese", "japanese"),
            Pair("Korean", "korean"),
            Pair("Chinese", "chinese")
        )
    )

    private abstract class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
