package eu.kanade.tachiyomi.animeextension.en.hentaihaven

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.hentaihaven.HentaiHavenFilters.GENRES
import eu.kanade.tachiyomi.animeextension.en.hentaihaven.HentaiHavenFilters.GenreFilter
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
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
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class HentaiHaven :
    ParsedAnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Hentai Haven"

    override val baseUrl = "https://hentaihaven.com"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .build()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", USER_AGENT)

    // ======================= Popular Anime =======================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String =
        "div.item, div.c-tabs-item__content, div.page-item-detail, div.col-6.col-md-3"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a[href*=\"/video/\"]") ?: element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("abs:href"))
        title = element.selectFirst("h3 a, .post-title a, a[title]")?.text()
            ?: link.attr("title").ifBlank { link.text() }
        val img = element.selectFirst("img")
        thumbnail_url = img?.attr("abs:data-src")?.ifBlank { null }
            ?: img?.attr("abs:data-lazy-src")?.ifBlank { null }
            ?: img?.attr("abs:src")
    }

    override fun popularAnimeNextPageSelector(): String =
        "a.next, .nav-previous, a[rel=\"next\"]"

    // ======================= Latest Anime =======================

    override fun latestUpdatesRequest(page: Int): Request =
        popularAnimeRequest(page)

    override fun latestUpdatesSelector(): String =
        popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Search Anime =======================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            return GET("$baseUrl/page/$page/?s=$encodedQuery", headers)
        }

        var genre = ""
        for (filter in filters) {
            if (filter is GenreFilter) {
                if (filter.selected.isNotBlank()) {
                    genre = filter.selected
                }
            }
        }

        return if (genre.isNotBlank()) {
            GET("$baseUrl/genre/$genre/page/$page/", headers)
        } else {
            popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String =
        popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // ======================= Anime Details =======================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1, .post-title h1, .entry-title")?.text() ?: ""
        val img = document.selectFirst(".summary_image img, .c-poster img, meta[property=\"og:image\"]")
        thumbnail_url = img?.attr("abs:src")?.ifBlank { null }
            ?: img?.attr("content")
        description = document.selectFirst(".description-summary, .manga-excerpt, .entry-content, meta[property=\"og:description\"]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }
        genre = document.select(".genres-content a, .tag-links a, a[href*=\"/genre/\"]")
            .map { it.text() }
            .distinct()
            .joinToString(", ")
        status = SAnime.COMPLETED
        initialized = true
    }

    // ======================= Episode List =======================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val seriesUrl = response.request.url.toString()
        val episodeLinks = document.select("a[href*=\"/episode-\"]")

        return if (episodeLinks.isNotEmpty()) {
            val seen = mutableSetOf<String>()
            val episodes = mutableListOf<SEpisode>()
            for (link in episodeLinks) {
                val href = link.attr("abs:href")
                if (href in seen) continue
                seen.add(href)

                val epNum = href.substringAfterLast("episode-")
                    .substringBefore("/")
                    .toFloatOrNull() ?: 1f

                episodes.add(
                    SEpisode.create().apply {
                        name = "Episode ${epNum.toInt()}"
                        episode_number = epNum
                        setUrlWithoutDomain(href)
                    }
                )
            }
            episodes.sortedByDescending { it.episode_number }
        } else {
            // Standalone video
            listOf(
                SEpisode.create().apply {
                    name = "Episode 1"
                    episode_number = 1f
                    setUrlWithoutDomain(seriesUrl)
                }
            )
        }
    }

    override fun episodeListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException("Not used")

    // ======================= Video List =======================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val episodeUrl = response.request.url.toString()

        val iframe = document.selectFirst("iframe[src*=\"player.php\"]")
            ?: return emptyList()
        val playerUrl = iframe.attr("abs:src")

        val playerReq = GET(playerUrl, headersBuilder().set("Referer", episodeUrl).build())
        val playerRes = client.newCall(playerReq).execute()
        if (!playerRes.isSuccessful) {
            playerRes.close()
            return emptyList()
        }
        val playerHtml = playerRes.body.string()
        playerRes.close()

        val tokenMeta = Regex("""name=["']x-secure-token["'] content=["']([^"']+)["']""")
            .find(playerHtml)?.groupValues?.get(1) ?: return emptyList()

        val rawToken = tokenMeta.replace("sha512-", "")
        val configJson = decodeToken(rawToken) ?: return emptyList()
        val config = json.decodeFromString<PlayerConfigDto>(configJson)

        val uri = config.uri ?: return emptyList()
        val en = config.en ?: return emptyList()
        val iv = config.iv ?: return emptyList()

        val apiBase = if (uri.startsWith("//")) "https:$uri" else uri
        val apiUrl = if (apiBase.endsWith("/")) "${apiBase}api.php" else "$apiBase/api.php"

        val formBody = FormBody.Builder()
            .add("action", "zarat_get_data_player_ajax")
            .add("a", en)
            .add("b", iv)
            .build()

        val apiReq = Request.Builder()
            .url(apiUrl)
            .post(formBody)
            .header("Referer", episodeUrl)
            .header("Origin", baseUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val apiRes = client.newCall(apiReq).execute()
        if (!apiRes.isSuccessful) {
            apiRes.close()
            return emptyList()
        }
        val apiBody = apiRes.body.string()
        apiRes.close()

        val streamResponse = json.decodeFromString<ApiResponseDto>(apiBody)
        val sources = streamResponse.data?.sources ?: return emptyList()

        val videoHeaders = headersBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()

        val videoList = mutableListOf<Video>()
        for (source in sources) {
            val src = source.src ?: continue
            if (src.contains(".m3u8")) {
                val hlsVideos = playlistUtils.extractFromHls(
                    src,
                    videoNameGen = { q -> "Hentai Haven - $q" }
                )
                videoList.addAll(hlsVideos)
            } else {
                videoList.add(Video(src, "Hentai Haven - Direct MP4", src, headers = videoHeaders))
            }
        }

        return videoList
    }

    override fun videoListSelector(): String =
        throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video =
        throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // ======================= Filters & Preferences =======================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        GenreFilter("Genre", GENRES)
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val prefRes = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = arrayOf("1080p", "720p", "480p")
            entryValues = arrayOf("1080", "720", "480")
            setDefaultValue("1080")
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_QUALITY_KEY, newValue as String).apply()
                true
            }
        }
        screen.addPreference(prefRes)
    }

    // ======================= Decoding Helpers =======================

    private fun decodeToken(token: String): String? {
        return try {
            val step1 = decodeBase64(rot13(token))
            val step2 = decodeBase64(rot13(step1))
            val finalDecoded = Base64.decode(rot13(step2), Base64.DEFAULT)
            String(finalDecoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun rot13(str: String): String {
        val sb = java.lang.StringBuilder(str.length)
        for (c in str) {
            when (c) {
                in 'a'..'m' -> sb.append(c + 13)
                in 'n'..'z' -> sb.append(c - 13)
                in 'A'..'M' -> sb.append(c + 13)
                in 'N'..'Z' -> sb.append(c - 13)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun decodeBase64(str: String): String {
        val decoded = Base64.decode(str, Base64.DEFAULT)
        return String(decoded, Charsets.ISO_8859_1)
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val PREF_QUALITY_KEY = "preferred_quality"
    }

    // ======================= DTOs =======================

    @Serializable
    private data class PlayerConfigDto(
        val en: String? = null,
        val iv: String? = null,
        val uri: String? = null
    )

    @Serializable
    private data class ApiResponseDto(
        val status: Boolean = false,
        val data: ApiDataDto? = null
    )

    @Serializable
    private data class ApiDataDto(
        val sources: List<SourceDto> = emptyList()
    )

    @Serializable
    private data class SourceDto(
        val src: String? = null,
        val label: String? = null,
        val type: String? = null
    )
}
