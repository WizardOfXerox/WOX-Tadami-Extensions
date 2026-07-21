package eu.kanade.tachiyomi.animeextension.pt.assistirfilmes

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class AssistirFilmes : ParsedAnimeHttpSource() {
    override val name = "AssistirFilmes"
    override val baseUrl = "https://assistirfilmes.co"
    override val lang = "pt"
    override val supportsLatest = true

    // Private request tag data class to preserve metadata across multi-hop redirects
    private data class VideoTag(val epUrl: String, val epNumber: Float)

    // Resilient OkHttp Client with Rate Limiting & Custom Error Interceptor
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(RateLimitInterceptor(2, 1, TimeUnit.SECONDS))
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    403 -> "Geo-blocked / Cloudflare Block (403): Access denied by server."
                    404 -> "Endpoint Not Found (404): Page or stream source is missing."
                    400 -> "Bad Request (400): Invalid parameter passed to endpoint."
                    429 -> "Rate Limited (429): Exceeded maximum allowed requests per second."
                    503 -> "DDoS Protection Active (503): Solve Cloudflare challenge in WebView."
                    else -> "HTTP Error ${response.code}"
                }
                throw Exception(errorMsg)
            }
            response
        }
        .build()

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)
    override fun popularAnimeSelector(): String = "div.series-intro__poster, div.flw-item, div.postbody article, div.poster, div.item"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val a = element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        title = element.selectFirst("h2, h3, div.title, img")?.let { it.attr("alt").ifBlank { it.text() } } ?: a?.text() ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.let { it.attr("abs:src").ifBlank { it.attr("data-src") } }
    }
    override fun popularAnimeNextPageSelector(): String? = "a.next, a.pagination-next, ul.pagination li.active + li a"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search?q=$query&page=$page", headers)
    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, h1, div.series-intro__title")?.text() ?: "Unknown Title"
        description = document.selectFirst("div.entry-content, div.description, div.summary")?.text()
        thumbnail_url = document.selectFirst("div.series-intro__poster img, div.poster img, div.thumb img")?.attr("abs:src")
        genre = document.select("div.genres a, div.tags a").joinToString(", ") { it.text() }
        initialized = true
    }

    override fun episodeListSelector(): String = "ul.episodes li, div.eplister li a, div.episode-item, ul.episodelist li a"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val a = if (element.tagName() == "a") element else element.selectFirst("a")
        setUrlWithoutDomain(a?.attr("href") ?: "")
        name = element.text().ifBlank { "Episode 1" }
        episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 1.0f
    }

    // Tagged Video Request to prevent parameter loss across redirects
    override fun videoListRequest(episode: SEpisode): Request {
        val tag = VideoTag(episode.url, episode.episode_number)
        return GET(baseUrl + episode.url, headers).newBuilder().tag(VideoTag::class.java, tag).build()
    }

    override fun videoListParse(response: Response): List<Video> {
        val tag = response.request.tag(VideoTag::class.java)
        val doc = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // 1. Primary iframe selector
        val iframeSrc = doc.selectFirst("div.source-box iframe, div.player-embed iframe, div#source-player-1 iframe, iframe.metaframe, iframe")?.attr("abs:src")
        if (!iframeSrc.isNullOrBlank()) {
            videoList.add(Video(iframeSrc, "Direct Video Stream", iframeSrc, headers))
        }

        // 2. Fallback direct video sources
        doc.select("video source").forEach { source ->
            val src = source.attr("abs:src")
            if (src.isNotBlank()) {
                val quality = source.attr("res") ?: "1080p"
                videoList.add(Video(src, quality, src, headers))
            }
        }

        return videoList
    }

    override fun videoListSelector(): String = "iframe, video source"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = ""

    // Fork compatibility method (omitting override for Aniyomi build compatibility)
    fun relatedAnimeListRequest(anime: SAnime): Request = GET("$baseUrl/recommendations", headers)
}
