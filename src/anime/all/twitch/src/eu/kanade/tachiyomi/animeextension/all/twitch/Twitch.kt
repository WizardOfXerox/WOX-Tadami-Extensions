package eu.kanade.tachiyomi.animeextension.all.twitch

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.LazyMutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    private var playlistExtractor by LazyMutable {
        PlaylistUtils(client, headers)
    }

    // ============================== Popular ==============================

    override fun popularAnimeSelector(): String = "table tbody tr, tbody tr"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/channels/ranking?page=$page", headers)

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val href = element.selectFirst("a[href^=/]")?.attr("href") ?: ""
            val channelName = href.removePrefix("/").substringBefore("/").substringBefore("?").trim()

            val nameText = element.selectFirst("td.name, td a, div.name")?.text()?.takeIf { it.isNotBlank() }
                ?: channelName.takeIf { it.isNotBlank() }
                ?: "Twitch Streamer"

            if (channelName.isNotBlank() && channelName != "channels") {
                setUrlWithoutDomain("/$channelName")
                title = channelName
            } else {
                setUrlWithoutDomain("/channels/ranking")
                title = nameText
            }

            val img = element.selectFirst("td img, img")
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

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        val channelName = document.location().substringAfterLast("/").substringBefore("?").trim()
        val titleText = document.selectFirst("div#app-conception div#app-title, h1, div.name")?.text()?.takeIf { it.isNotBlank() }
            ?: channelName.takeIf { it.isNotBlank() }
            ?: "Twitch Streamer"

        return SAnime.create().apply {
            title = titleText
            genre = document.select("ul.list-group li div a.label").joinToString { it.text() }
            status = SAnime.ONGOING
            description = document.selectFirst("div.row div.col-md-4.text-center, div.channel-description")?.text() ?: "Twitch Live Stream"
            thumbnail_url = document.selectFirst("img.channel-avatar, img[src*=/profile_images/], img[src*=/jtv_user_pictures/]")?.attr("abs:src") ?: ""
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val isLive = document.select("li.list-group-item span.label.label-danger").text().contains("streaming now", ignoreCase = true)

        val episode = SEpisode.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            name = if (isLive) "Streaming Now (Live)" else "Live Stream"
            episode_number = 1F
        }
        return listOf(episode)
    }

    override fun episodeListSelector(): String = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================== Video List ==============================

    override fun videoListParse(response: Response): List<Video> {
        val pageUrl = response.request.url.toString()
        val channelName = pageUrl.substringAfterLast("/").substringBefore("?").substringBefore("#").trim()

        if (channelName.isBlank()) {
            throw Exception("Invalid Twitch channel URL")
        }

        try {
            val gqlUrl = "https://gql.twitch.tv/gql"
            val bodyStr = """{"query":"query PlaybackAccessToken(${'$'}login: String!) { streamPlaybackAccessToken(channelName: ${'$'}login, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"embed\"}) { value signature } }","variables":{"login":"$channelName"}}"""

            val reqHeaders = Headers.headersOf(
                "Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko",
                "Content-Type", "application/json",
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
            )

            val gqlRequest = POST(gqlUrl, reqHeaders, bodyStr.toRequestBody("application/json".toMediaType()))
            client.newCall(gqlRequest).execute().use { gqlResponse ->
                if (gqlResponse.isSuccessful) {
                    val jsonStr = gqlResponse.body.string()
                    val root = json.parseToJsonElement(jsonStr).jsonObject
                    val tokObj = root["data"]?.jsonObject?.get("streamPlaybackAccessToken")?.jsonObject
                    val sig = tokObj?.get("signature")?.jsonPrimitive?.content
                    val token = tokObj?.get("value")?.jsonPrimitive?.content

                    if (!sig.isNullOrBlank() && !token.isNullOrBlank()) {
                        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
                        val usherUrl = "https://usher.ttvnw.net/api/channel/hls/$channelName.m3u8?sig=$sig&token=$encodedToken&allow_source=true&allow_audio_only=true&fast_bread=true"
                        val videos = playlistExtractor.extractFromHls(usherUrl, referer = "https://www.twitch.tv/")
                        if (videos.isNotEmpty()) return videos
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Twitch", "Failed to fetch Twitch live stream: ${e.message}")
        }

        val offlineVideoUrl = "https://cdn.discordapp.com/attachments/909242748283006996/1014362630456090654/amogus.mp4"
        return listOf(Video(offlineVideoUrl, "Stream Offline / Refresh when live", offlineVideoUrl))
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
